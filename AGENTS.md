# AGENTS.md — SnowHide（冻结工具 / 暂定名「雪藏」）

> **一句话**：Android 应用冻结器——用 Shizuku 把应用「冻结」（`pm disable-user`：图标消失+进程全断+断自启），
> 一键解冻秒恢复。**除了用户明确选择的「移除并卸载」，绝不删除任何应用数据。**

**设计文档**（唯一事实来源，所有 UI/功能/架构决策全在这）：
`~/opencode_dev/冻结工具设计.md` —— 新对话开工前先读它（§9 代码架构规范是硬约束）。

This file is the single source of truth for resuming work in a fresh conversation. Read it fully first.

---

## 1. What this app does (purpose & mental model)

- **核心功能**：冻结应用（`pm disable-user --user 0 <pkg>`）——图标消失、进程全断、自启/通知/后台全停、数据保留；解冻（`pm enable`）秒恢复。
- **权限通道**：Shizuku（shell 身份，P0 唯一实现）；root / Device Owner 引擎空壳预留（P2/P3）。
- **模式**：P0 只有「冻结」一档；休眠/禁用/隐藏按路线图扩展（FreezeMode 枚举预留）。
- **界面模型**（launcher 式）：
  - 主屏幕混排宫格（应用+文件夹交错，透明壁纸背景）
  - 文件夹全屏打开（二级封顶不嵌套），左右滑动在主屏⇄文件夹间**循环**切换
  - 底部图标栏 = 已添加且解冻的应用（长按锁定、上划冻结、最右快速清理）
  - 齿轮二级菜单 8 项（增加应用/移除应用/整理目录/启用全部/停用全部/快速启停/更多选项/关于）
- **数据**：SharedPreferences + JSON（数据规模小，不用 Room——与设计文档 §4 的 Room 方案有出入，P0 拍板用 SP+JSON，以后数据量大再迁移）。

---

## 2. Build

```bash
# Windows 直接：
gradlew.bat assembleDebug / assembleRelease
# WSL（agent 已验证可用）：
/mnt/c/Windows/System32/cmd.exe /c "cd /d D:\GitHub\Android\SnowHide && build_debug.bat"
```

- 项目根有 `build_debug.bat` / `build_release.bat`（设 JAVA_HOME=jdk-17.0.5 + 调 gradlew.bat）。
- **不要在 WSL 直接 `./gradlew`**（Windows java 读不了 `/mnt/d` 路径）。
- 输出：`app/build/outputs/apk/debug/app-debug.apk` / `release/app-release.apk`。
- 签名配置在 `local.properties`（gitignored）：`RELEASE_STORE_FILE/PASSWORD/ALIAS/KEY_PASSWORD`（MediaSync 同款）。
- 首次编译曾失败：AS 模板 `core-ktx 1.19.0` 要求 SDK 37 → **已降级 1.10.1**，勿再升级。

---

## 3. Tech stack

- AGP `9.1.1`、Kotlin `2.2.10`、Compose BOM `2024.09.00`、Java 11。
- `minSdk 29`（用户拍板：低版本用黑白门）、`targetSdk 36`、`compileSdk 36`（新 DSL release(36){minorApiLevel=1}）。
- 依赖：`dev.rikka.shizuku:api/provider 13.1.5`、`material-icons-extended`、`lifecycle-viewmodel-compose`、`kotlinx-coroutines-android 1.7.3`。
- **不用**：Room（SP+JSON 足够）、SaltUI（Hidden API 风险）、haze（毛玻璃 P1 再加）。
- 图标：FontAwesome 5.15.4 → VectorDrawable（素材 `~/temp/fontawesome-free-5.15.4-desktop.zip`，清单见设计文档附录 A）。

---

## 4. Versioning (do NOT change casually)

- **`versionCode` 永远 = 1**（HARD RULE）：覆盖安装生命线，任何改动都是 bug；只有 `versionName` 可变。
- `versionName` = 构建日期时间 `YYMMDDHHmm`（gradle 里 SimpleDateFormat 自动生成，精确到分钟，用户拍板）。
- **Debug 变体**：`applicationIdSuffix = ".debug"` + `versionNameSuffix = "-debug"` + 应用名「雪藏D」（`app/src/debug/res/values/strings.xml` 覆盖）。
- 覆盖安装要求同签名；debug/release 包名不同 = 两个独立 App 共存。

---

## 5. Plugin rules (do NOT change)

- 只应用 `kotlin.compose`——绝不加 `kotlin-android`（重复 Kotlin 插件会报 `Cannot add extension with name 'kotlin'`）。
- 无 `kotlinOptions {}`（需要 kotlin-android，我们没有）。
- 无重复 `core-ktx`（AS 可能自动加第二个，删掉）。
- 未用 KSP/kapt——数据层是 SP+JSON，不引注解处理器。

---

## 6. File safety (HARD CONSTRAINT — never violate)

- 只操作**包管理器状态**（`pm disable-user/enable` 等），**不删除、不修改任何应用数据/文件**。
- ⚠️ **唯一特例（用户拍板）**：「移除并卸载」允许真正卸载应用（删应用+数据）——仅限用户在长按菜单明确选择该项并二次确认时执行。
- 禁止出现：`File.delete()`、`rm`、`Runtime.exec("rm")`、`PackageManager.deletePackage`（除卸载特例外）等。
- `pm` 命令全部经 `PowerEngine.exec()`（shell 身份），不写任何本地文件。

---

## 7. Permissions

| 权限 | 用途 |
|------|------|
| `moe.shizuku.manager.permission.API_V23` | Shizuku API（shell 身份执行 pm 命令） |
| `android.permission.QUERY_ALL_PACKAGES` | Android 11+ 查询全部已装应用（增加应用界面/图标包扫描） |
| `android.permission.POST_NOTIFICATIONS` | 快捷方式执行失败通知（Android 13+ 首次启动申请） |
| `android.permission.VIBRATE` | dock 锁定/解锁震动反馈（受系统静音/勿扰控制） |

- Shizuku 授权流程：UI 引导卡 → `Shizuku.requestPermission(code)`（必须从 Activity 发起）→ `MainActivity.onRequestPermissionsResult` 转发给 `Shizuku.onRequestPermissionsResult` → `EngineManager.refresh()`。
- 后续阶段权限：P1 通知（POST_NOTIFICATIONS）、P2 Device Owner、P3 root——到时按设计文档补。

---

## 8. Architecture (package `com.nbljsbdk.snowhide`)

依赖单向：`feature → domain → data → core`；feature 间禁止互相 import。

```
MainActivity.kt        — 极薄：EngineRegistry.init() + setContent + Shizuku 授权回调转发

core/                  ★核心抽象（P0 就位，扩展点全在这）
  engine/
    Engine.kt          — PowerEngine 接口（宪法，永不改签名；新能力=默认实现返回失败）
    EngineManager.kt   — 注册表 + 探测 + StateFlow（UI 注册表驱动渲染）
    impl/ShizukuEngineImpl.kt — P0 实现：Shizuku.newProcess 执行 pm 命令
    impl/RootEngineImpl.kt / DoEngineImpl.kt — 空壳（P3/P2）
    registry/EngineRegistry.kt — 唯一知道「有哪些引擎」的文件（加引擎=这里一行）
  mode/
    FreezeMode.kt      — 枚举（isImplemented 标志，未开放灰显）
    FreezeExecutor.kt  — 引擎×模式分发

data/
  model/GridModels.kt  — GridItem / Folder / FolderApp（type/parent 概念见设计文档 §4）
  repo/GridRepository.kt — 宫格/文件夹/排序/整理目录全部数据操作（SP+JSON，StateFlow）
  prefs/SettingsRepository.kt — 布局/壁纸/开关/图标包（SP 持久化）

domain/
  FreezeUseCase.kt     — 冻结/解冻/批量/快速清理（UI 永不直连 executor）

feature/
  home/                — 主屏幕（HomeViewModel + HomeScreen）
  folder/ organize/ appmanage/ settings/ about/ — P0 后续建

service/               — P1+（accessibility 划卡停用 / lockclean / tile），目录预留

ui/
  util/AppIconLoader.kt — 图标包协议 + 系统回退 + 缓存
  util/FrostModifiers.kt — 霜化 Modifier（ColorMatrix）
  theme/               — 霜冻深色主题（IceBlue/FrostCard 等）

bridge/                — 应用桥预留（已研究清楚，黑白门 3.3.3 反编译确认；优先级最后，快速启停一定程度替代它）
```

**引擎模块化铁律**（用户特别要求，将来大概率重构）：
- 接口=宪法；impl 隔离（每个引擎一个文件，三方库 import 只在各自文件）
- 注册表单点；重构引擎 = 重写 impl 目录，外部零波及
- UI 引擎/模式区块 = 注册表驱动渲染（不硬编码）

**UI 解耦铁律**（用户特别要求，方便慢慢优化颜值）：
- Composable 一律无状态（state+回调）
- 视觉细节收 ui/ 组件层；颜色走 theme；动画封装 Modifier 扩展
- 优化颜值永不触碰业务逻辑

---

## 9. Core flow (behavior contract)

### 冻结 / 解冻
1. UI 调 `FreezeUseCase.freezeApp/unfreezeApp(pkg)` → `FreezeExecutor` 按模式分发 → 主引擎执行。
2. Shizuku 实现：`pm disable-user --user 0 <pkg>` / `pm enable <pkg>`（`Shizuku.newProcess` shell 身份）。
3. 冻结状态查询：`pm list packages -d` 批量解析（`listFrozenPackages()`），一次刷新全列表。
4. 结果 Snackbar 提示（设置里有 toast 开关）。

### 整理目录状态机（设计文档 §3.10 即时应用版）
- 主屏进入整理目录时无高亮；从文件夹页进入时自动高亮当前文件夹。
- 状态包含无高亮、主屏 app、文件夹、主屏 app+目标文件夹、文件夹+内 app；双高亮时最近点击对象拥有左右键焦点。
- 先点 app 再点文件夹不会丢失 app；左右移动文件夹，下键把 app 加入文件夹；移动完成后自动高亮下栏刚加入的 app。
- 左右=当前点击/焦点对象在所属区域内排序（不循环）；上下=跨区转移（下=主屏→文件夹末、上=文件夹→主屏末）。
- 加号新建文件夹；减号直接删除选中文件夹，内应用续补主屏后，不二次确认；所有结构操作即时应用，确认/返回仅退出，不提供取消回滚。

### 底部图标栏（设计文档 §3.6）
- 显示 = 已添加且解冻的应用；长按锁定（持久化，豁免快速清理/息屏清理）；上划冻结；最右快速清理

---

## 10. Platform gotchas (learned the hard way — keep these)

- **Shizuku 包名与权限命名空间（关键，容易写错）**：
  - Shizuku **应用包名 = `moe.shizuku.privileged.api`**（进程名、启动入口都用它；`getLaunchIntentForPackage` 必须用它）
  - `moe.shizuku.manager` **不是可启动包名**，只是权限/组件的命名空间前缀（如 `moe.shizuku.manager.permission.API_V23`）
  - 打开 Shizuku 管理器、检查进程都要用 `privileged.api`
- **Shizuku 授权与权限**：授权结果走 `OnRequestPermissionResultListener` 回调（listener 模式，**不是** onRequestPermissionsResult 转发——13.x 无此方法）。`requestPermission` 前必须 `pingBinder()` 检查，否则抛 `binder haven't been received`。**执行 pm 命令用 UserService 方案**：`Shizuku.bindUserService` 把 ShellCommandService 拉起到 shell 身份进程，手写 Binder 事务执行 `pm disable-user/enable`（13.1.5 的 `newProcess` 是 private 不可用）。
- **R8 混淆会杀死 Shizuku UserService（release 专属坑）**：ShellCommandService 由 Shizuku server 端实例化（构造器/onTransact 经框架调用），release 混淆后构造器被破坏 → 所有 pm 命令静默挂起（冻结解冻无效、增删界面移出卡死）。**proguard-rules.pro 必须 keep 该类**（已加，勿删）。
- **卸载重装会清掉 Shizuku 授权**：API_V23 是 dangerous 权限，覆盖安装（`install -r`）保留、卸载重装清零且可能带「不再询问」标记 → 授权弹窗弹不出。真机调试需要时用 `adb shell pm grant <pkg> moe.shizuku.manager.permission.API_V23`。
- **ColorOS 吞后台 Toast** → 通知兜底（本项目 P0 前台操作为主，受影响小）。
- **OPPO 静态 shortcuts 崩** → 用动态 ShortcutManager（MediaSync 已验证方案）。
- **图标包协议**：发现 `queryBroadcastReceivers(INSTALL_ICON_PACK)`；请求 `RESOLVE_ICON` 有序广播（`sendOrderedBroadcast` + result receiver 取 `icon` extra）；失败回退系统图标。
- **`pm list packages -d`**：输出 `package:com.xxx` 逐行解析（`-d -s` 只列系统禁用项，神之一手目标过滤用）。
- **binder.transact 是同步调用**（sh 进程跑完才返回）——必须在 IO 线程执行（`withContext(Dispatchers.IO)`），主线程执行批量命令会 ANR（真机实锤）。
- **图标包 appfilter 协议**：轻语/轻风等无 INSTALL_ICON_PACK 广播的包，直接解析 `assets/appfilter.xml`（`createPackageContext(pkg, CONTEXT_RESTRICTED)` 读 assets/资源；`CONTEXT_INCLUDE_CODE` 从 Android 11 起被禁——SecurityException 实锤）。匹配用**包名优先**（冻结应用 `getLaunchIntentForPackage` 返回 null，组件匹配失效）。并发解析要 synchronized（113 并发卡死实锤）；getIdentifier 缓存（IPC 慢）。
- **LazyGrid key 重复崩溃**：同文件夹重复成员（历史数据 moveAppToFolder 缺防重）→ FolderScreen `key={it}` 崩。init 自动清理 + moveAppToFolder 防重 + 渲染 distinct 兜底。搜索成员 item 的 id 必须绝对唯一（负数 hash 会碰撞，用 `Long.MIN_VALUE+index`）。
- **ColorOS/realme 激进杀后台**：NoDisplay Activity 立即 finish 会被秒杀进程（快捷方式动作丢失+无反馈）——Translucent 保活窗口直到执行完成。后台 Toast 被吞（Android 12+ 后台 toast 限制）——Toast 在窗口存活期间发，失败兜底通知。
- **日志门控**：调试日志一律用 `if (BuildConfig.DEBUG) Log.d(...)`——debug 有、release 自动无（一键开关约定）。
- **Compose BOM 2024.09.00**：`TextOverflow.MiddleEllipsis` 不可用（用 Ellipsis）；`animateFloatAsState` 需要 `androidx.compose.animation.core.animateFloatAsState`（注意 import 路径，旧 BOM 下 animation 包结构）。
- **debug/release 包名**：`com.nbljsbdk.snowhide` / `...snowhide.debug`——logcat 过滤、数据目录、Shizuku 授权均按包名独立。
- **SP+JSON 存储键**：`snowhide_grid`（grid_items/folders/folder_apps）、`snowhide_settings`。

---

## 11. Recent features (current state — 2026-08-20)

```
54d48cf fix: 文件夹页 LazyGrid key 重复崩溃（成员重复数据清理+防重+distinct）
b98771c perf: 图标加载提速（启动预热 appfilter + getIdentifier 缓存 + 图标缩放 256px）
b660a46 feat: 美化设置加图标形状（圆角方形/圆形，未收录图标包也裁圆）
3c368a9 fix: 图标包冻结应用不生效（包名匹配优先，冻结应用组件匹配失效）
6012b7f feat: 图标包支持 appfilter.xml 协议（Pure 轻语/轻风无广播包可用）
32f27ad feat: 冻结滤镜语义重排（原色=真正原色无遮罩/变蓝改淡化）+ 神之一手恢复解冻全部
93c73bb feat: 神之一手目标过滤 + 结果弹窗滚动可复制
5701cae fix: 快捷方式 ColorOS 杀进程（Translucent 保活窗口 + 进度弹窗）
54bee25 feat: 批量执行统一（execBatched 阈值20分块40）+ 进度条 + 防重复
195dc9b fix: ANR 根因（binder.transact 切 IO 线程）
d996a27 feat: 进度条文案计数（正在停用: 45/130）
f91a1f6 feat: 快捷方式长条冒泡进度弹窗（逐个 exec 平滑+1）
```

已完成（08-13 之后）：
- 图标包 appfilter 协议（轻语/轻风）+ 图标形状 + 图标加载提速 + 冻结应用图标支持
- 批量执行统一（停用/启用全部、智能清理、神之一手、快速启停）逐个+进度条+防重复
- 神之一手转正式（About 页，解冻全部含系统应用，结果滚动+复制）
- 快捷方式 4 个（含启用全部）Pure 圆形图标 + 冒泡进度弹窗 + Toast
- ANR 修复（transact 切 IO）、横滑 key 重复闪退修复
- 冻结滤镜语义重排、浅色主题、设置持久化修复、版本号精确到分钟
- 备份导入导出（SAF + 立即重启确认）、搜索自动聚焦、权限说明
- 全局震动统一、锁屏自动清理说明弹窗、整理目录双高亮与即时状态机

**未完成（候选下一步）**：
1. 应用分身（pm --user 分用户冻结）
2. P1：休眠模式、划卡停用（无障碍）、壁纸图片选择器
3. P2/P3：固定快捷方式（接口已留）、DO 引擎、root 引擎、禁用/隐藏模式

## 12. Known limitations / candidate next tasks

1. **debug 包比 release 卡**：debug 无 R8 优化 + debuggable，大列表渲染差异明显——日常使用 release（正常）。
2. **图标包覆盖不全**：未收录的应用回退系统图标（可配合「图标形状=圆形」统一视觉）。
3. **无自动化测试**：验证 = WSL 编译 + 用户真机测试。
4. **应用桥**：功能已研究清楚（黑白门 3.3.3 反编译实证），优先级最后；快速启停一定程度替代它。
5. **调试日志已清空**：以后加日志用 `BuildConfig.DEBUG` 门控。

---

## 13. Repo / git

---

## 14. adb usage

- adb 位置：`C:\Users\nbljsbdk\AppData\Local\Android\Sdk\platform-tools\adb.exe`（WSL 全路径调用）。
- **Shizuku 激活固定使用此命令**：`adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
- **纪律**：agent 只执行**安全操作**（`adb devices`、`logcat` 只读诊断）；**安装/卸载/清数据等危险操作必须先征得用户同意**。
- 无线调试：配对端口 ≠ 连接端口（动态）；offline 用 `kill-server` 清除；多设备加 `-s <ip:port>`。
- logcat：`-c` 清 → 复现 → `-d -s <TAG>`（崩溃看 `AndroidRuntime`）。
- 安装：`adb -s <ip:port> install -r app/build/outputs/apk/debug/app-debug.apk`（versionCode 恒 1，覆盖装永远有效）。

---

## 15. Working agreement (for the agent)

- 用**中文**沟通；代码注释中文 KDoc（MediaSync 风格）。
- 铁律：§6 文件安全、§4 versionCode、§13 git 纪律。
- 分层纪律：feature 间零 import、UI 无状态、注册表驱动、impl 隔离。
- 改权限/版本/插件/架构时，同步更新本文件与 `~/opencode_dev/冻结工具设计.md`。
- 每个可编译的小功能点 → commit 存档（§13 规范）。
- **HARD RULE（用户强调）**：集成任何三方库前，**必须先看官方文档并核对 jar 内真实 API 签名**，绝不凭训练数据假设 API 存在（训练数据会过期）。Shizuku 教训：`newProcess` 已私有、`onRequestPermissionsResult` 转发已移除、`ShizukuProvider` 必须手动声明——三个坑全是凭训练数据写的。正确做法：① webfetch 官方 README/GitHub ② javap/jadx 反编译核对 jar 签名 ③ 再写代码。
