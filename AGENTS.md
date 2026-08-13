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
- `versionName` = 构建日期 `YYMMDD`（gradle 里 SimpleDateFormat 自动生成）。
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
| `android.permission.QUERY_ALL_PACKAGES` | Android 11+ 查询全部已装应用（增加应用界面） |

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

bridge/                — 应用桥预留（BridgeCoordinator 接口，现在不实现，用户「没研究清楚」）
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

### 整理目录状态机（设计文档 §3.10 终版，P0 待实现 UI）
- ① 无选中：全灰仅「创建」可点 → ② 选中主屏 app：左右+创建 → ③ 选中文件夹：左右/创建/删除+名字输入+内应用展示
- 左右=区内排序（不循环）；上下=跨区转移（下=主屏→文件夹末、上=文件夹→主屏末）；删除只杀文件夹（内应用续补主屏后）；退出=取消/确认+返回键保存询问

### 底部图标栏（设计文档 §3.6）
- 显示 = 已添加且解冻的应用；长按锁定（持久化，豁免快速清理/息屏清理）；上划冻结；最右快速清理

---

## 10. Platform gotchas (learned the hard way — keep these)

- **Shizuku.newProcess 是私有 API**——13.1.5 中 `Shizuku.newProcess(...)` 为 private！不能用它执行命令。备选：`ShizukuRemoteProcess` / UserService（`Shizuku.bindUserService`），或 `ShizukuBinderWrapper` 直调 IPackageManager（黑白门 AppGate 同款）。**P0 编译错误待修，改用哪个方案要查 Shizuku 13.1.5 公开 API。**
- **ColorOS 吞后台 Toast** → 通知兜底（本项目 P0 前台操作为主，受影响小）。
- **OPPO 静态 shortcuts 崩** → 用动态 ShortcutManager（MediaSync 已验证方案）。
- **图标包协议**：发现 `queryBroadcastReceivers(INSTALL_ICON_PACK)`；请求 `RESOLVE_ICON` 有序广播（`sendOrderedBroadcast` + result receiver 取 `icon` extra）；失败回退系统图标。
- **`pm list packages -d`**：输出 `package:com.xxx` 逐行解析；Android 版本差异待真机验证。
- **Compose BOM 2024.09.00**：`TextOverflow.MiddleEllipsis` 不可用（用 Ellipsis）；`animateFloatAsState` 需要 `androidx.compose.animation.core.animateFloatAsState`（注意 import 路径，旧 BOM 下 animation 包结构）。
- **debug/release 包名**：`com.nbljsbdk.snowhide` / `...snowhide.debug`——logcat 过滤、数据目录、Shizuku 授权均按包名独立。
- **SP+JSON 存储键**：`snowhide_grid`（grid_items/folders/folder_apps）、`snowhide_settings`。

---

## 11. Recent features (current state — 2026-08-13)

```
fa02251 feat: 初始化 SnowHide——P0 架构骨架   ← 已 push（唯一一次）
```

已完成：工程创建（minSdk 29 / 包名 / 签名配置 / build bat）+ core 引擎层（接口/管理器/三引擎/注册表）+ mode 层 + data 层（GridRepository/SettingsRepository）+ domain（FreezeUseCase）+ feature/home（主屏 UI 骨架）+ 霜冻主题 + 图标加载器 + 霜化 Modifier + Shizuku 权限声明。

**未完成（当前正在做）**：编译错误修复（见 §12 第 1 条）+ P0 剩余界面。

---

## 12. Known limitations / candidate next tasks

1. **编译错误（8 个，待修）**：
   - `ShizukuEngineImpl`：`Shizuku.newProcess` 是 private——换 Shizuku 公开 API 执行命令（UserService 或 Binder 直调）
   - `MainActivity.onRequestPermissionsResult` Unresolved——ComponentActivity 需要 override 签名核对（可能 needs `onRequestPermissionsResult` 在 androidx.activity 是 final？查）
   - `HomeViewModel.kt:258` 语法错误（多余右括号）
   - `AppIconLoader`：`toBitmap()` unresolved（Drawable 转 Bitmap 用 BitmapDrawable/Canvas 方式）
   - `FrostModifiers`：`animateFloatAsState`/`colorFilter` import 错误（animation-core 路径 + graphicsLayer 内 colorFilter 用法）
   - `HomeScreen`：foundation API experimental（`combinedClickable` 需 `@OptIn(ExperimentalFoundationApi::class)`）
2. **P0 剩余界面**：整理目录状态机 UI、文件夹全屏页+循环滑动、增加/移除应用左右分栏、设置页（布局/壁纸/图标包选择）、齿轮菜单接线、长按菜单接线。
3. **占位图标**：齿轮/文件夹/快速清理目前用系统图标占位，下一步从 FontAwesome 转 VectorDrawable 替换（附录 A 清单）。
4. **无自动化测试**：验证 = WSL 编译 + 用户真机测试。
5. **应用桥**：预留不实现（用户没研究清楚，后加）。

---

## 13. Repo / git

- Remote: `git@github.com:NBLJSBDK/SnowHide.git`, branch `master`。
- **提交规范（用户新定，强制）**：
  - 格式：`type: 中文描述`，type ∈ `feat / fix / refactor / perf / docs / test / chore / wip`
  - **禁止**：`add`、`update`、`modify`、`big` 等宽泛词
  - 示例：`feat: 增加应用界面左右分栏滑动移动`、`fix: 修复 Shizuku newProcess 私有 API 编译错误`
- **工作模式（用户拍板）**：开发阶段允许 agent **commit 小功能存档**；**push 只做 init 那一次**，之后推送必须用户明确命令。
- 不提交：`session-*.md`、`local.properties`、keystore（`.gitignore` 已覆盖）。
- amend/force-push：必须用户明确批准。

---

## 14. adb usage

- adb 位置：`C:\Users\nbljsbdk\AppData\Local\Android\Sdk\platform-tools\adb.exe`（WSL 全路径调用）。
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
