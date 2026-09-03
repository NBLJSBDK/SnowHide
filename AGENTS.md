--ANSWER--

## 0. Highest Priority: Response and Engineering Principles

### 0.1 Response rule — highest priority

- Every assistant response must begin with the exact marker `--ANSWER--` as its first non-whitespace line.
- This applies to progress updates, questions, tool-related explanations, and final answers.
- Do not place any text before the marker.

### 0.2 Project core principles — second priority

- **功能优先，安全优先**：先保证用户功能可用、数据安全和已验证行为不回退，再谈抽象和架构美观；不为了“架构漂亮”重写稳定业务。
- **架构服务于功能**：每次新增或修复功能都必须能说明“放在哪一层、谁拥有数据、业务入口是什么、如何测试、失败怎么办”。
- **渐进式模块化**：当前单 Gradle module 内通过 package、依赖方向、接口、组合根和测试建立边界；不为追求形式提前拆 Gradle 多模块，不制造没有真实调用者的空壳。
- **依赖方向固定**：层级流向遵循 `feature → domain → data → core`；新代码的直接 import 遵循 `feature → domain/ui`、`domain → data/core`、`data → core`，系统入口通过组合根获得依赖；UI 负责无状态绘制，业务规则不堆进 Composable。
- **单一所有权**：一个数据只有一个 Repository 所有者，一个业务动作只有一个 UseCase 入口，一个系统入口只处理生命周期和系统适配，不复制业务规则。
- **纯逻辑先行**：状态机、过滤、排序、规划和策略优先放入 Android/Compose 无关的 `domain`，用 JVM 单元测试保护；Android 生命周期、Binder、无障碍和 Compose 只做适配。
- **小步、可回滚、可验证**：每个可编译功能点独立提交；不把未经验证的多个阶段合并成大提交；修改后至少检查编译、相关测试、`git diff --check` 和必要的真机行为。
- **协议和数据兼容**：架构重构只能改变代码组织，不能悄悄改变冻结命令、SP/JSON、Backup v1、Binder、`versionCode=1` 或已确认的用户行为。
- **真实调用者原则**：只有出现真实功能需求和调用者时才新增抽象、目录或接口；未来能力先记录边界，不提前堆空壳。
- **发布纪律**：功能实现、测试、模块文档和正式版本说明/版本号按独立提交组织；正式发布提交、推送或创建 Tag 前必须补齐真实更新说明并完成验证。

### 0.3 Git operation rules — third priority

- Git 提交说明必须使用下列允许的类型，类型后可按项目需要添加作用域，例如 `feat(home): ...`。

| type | 用途 | 示例 |
|---|---|---|
| `feat` | 新功能（feature） | `feat(auth): 增加微信登录功能` |
| `fix` | 修补 Bug | `fix(menu): 修复下拉菜单在移动端不显示的错误` |
| `refactor` | 重构，不新增功能且不修复 Bug | `refactor: 简化逻辑判断函数` |
| `perf` | 性能优化 | `perf: 提高渲染效率` |
| `docs` | 文档变动 | `docs: 更新 API 使用说明` |
| `test` | 增加或调整测试 | `test: 添加登录模块单元测试` |
| `chore` | 构建过程或辅助工具变动 | `chore: 升级依赖库` |
| `wip` | 工作进行中（Work In Progress） | `wip: 正在处理搜索建议逻辑` |

- 禁止使用模糊提交类型：`add`、`update`、`modify`、`big`。

### 0.4 Feature and release workflow — fourth priority

- **适用范围**：新功能和 Bug 修复统一遵循以下流程；功能确认与正式发布确认分开进行。
1. **建立可回退基线**：先执行 `git status --short`，工作区必须干净，且当前 `HEAD` 已有提交记录，确保随时可以回到当前状态。发现未提交或未跟踪的用户修改时先停止并确认，不擅自覆盖或清理。
2. **设置开发版号**：确定本次正式目标版本，例如 `0.3.3`，应用内先使用 `0.3.3-dev`；同一开发阶段反复修改和重新编译时，可递增为 `0.3.3-dev.1`、`0.3.3-dev.2` 等，用于区分安装包。`versionCode` 永远保持 `1`，开发版号不写入正式版本历史。
3. **开发与修复**：进行功能开发、Bug 修复、相关测试和必要的局部解耦；保持安全边界、数据兼容和既有行为不回退。
4. **功能验收存档**：编译并安装 APK，等待用户确认并明确授权提交。通过后按 Git 类型规则提交功能/修复存档；未通过则不提交，继续修改、递增开发版号并重新编译安装，直到通过。
5. **正式版本验收存档**：将 `-dev` 开发版号改为正式版号，更新 `VersionHistory.kt`、真实更新日志以及需要同步的 `AGENTS.md`、`v0.3task.md` 和设计文档；再次编译并安装，等待用户确认并明确授权提交后，提交独立的正式发布存档。
6. **Tag 与推送**：只有在用户明确授权后，检查提交历史、工作区、远端差异和版本说明，再创建对应 Tag 并推送分支和 Tag；不强推、不移动旧 Tag。

---

# AGENTS.md — SnowHide（冻结工具 / 暂定名「雪藏」）

> **一句话**：Android 应用冻结器——用 Shizuku 把应用「冻结」（`pm disable-user`：图标消失+进程全断+断自启），
> 一键解冻秒恢复。**除了用户明确选择的「移除并卸载」，绝不删除任何应用数据。**

**设计文档**（唯一事实来源，所有 UI/功能/架构决策全在这）：
`~/opencode_dev/docs/冻结工具设计.md` —— 新对话开工前先读它（§9 代码架构规范是硬约束）。

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
  - 齿轮二级菜单 7 项（整理目录/增删应用/启用全部/停用全部/快速启停/更多选项/关于）；应用分身并入增删应用左栏模式
- **数据**：SharedPreferences + JSON（数据规模小，不用 Room——与设计文档 §4 的 Room 方案有出入，P0 拍板用 SP+JSON，以后数据量大再迁移）。

---

## 2. Build

```bash
# Windows 直接（二选一）：
gradlew.bat assembleDebug
gradlew.bat assembleRelease
# WSL（agent 已验证可用）：
/mnt/c/Windows/System32/cmd.exe /c "cd /d D:\GitHub\Android\SnowHide && build_debug.bat"
/mnt/c/Windows/System32/cmd.exe /c "cd /d D:\GitHub\Android\SnowHide && build_release.bat"
```

- 项目根有 `build_debug.bat` / `build_release.bat`（设置 `JAVA_HOME=C:\Program Files\Java\jdk-17.0.5` 后调用 gradlew.bat）。
- Windows/WSL 环境变更先阅读 `/home/wywy521/temp/环境变化说明.txt`；全局 Java 22 不改变 Android 构建固定使用 JDK 17 的规则。
- **不要在 WSL 直接 `./gradlew`**（Windows java 读不了 `/mnt/d` 路径）。
- 输出：`app/build/outputs/apk/debug/app-debug.apk` / `release/app-release.apk`。
- 签名配置在 `local.properties`（gitignored）：`RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_ALIAS`、`RELEASE_KEY_PASSWORD`（MediaSync 同款）。
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
- `versionName` = 语义版本（当前正式版 `0.5.0`，上一正式版 `0.4.2`）；编译时间由 gradle 单独生成并展示，不能把编译时间混入版本号。
- `0.5.0` 已完成正式发布；下一开发目标待确定，开发阶段仍使用目标版本加 `-dev` 后缀，正式版本号和 `VersionHistory.kt` 只在发布存档阶段更新。
- **开发版号**：开发阶段使用正式目标版本加 `-dev` 后缀，例如 `0.3.3-dev`；反复修改和重新编译可递增为 `0.3.3-dev.1`、`0.3.3-dev.2`，正式发布时去掉后缀恢复为 `0.3.3`。开发版号只用于区分安装包，不创建正式 Tag 或正式版本历史记录。
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

- 只通过 `PowerEngine` 操作**被管理应用的包管理器状态**（`pm disable-user/enable` 等）；不得删除或修改被管理应用的数据/文件。SnowHide 自身的 SharedPreferences、JSON、缓存和用户主动选择的备份文件属于正常功能，不在此限制内。
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

- Shizuku 授权流程：UI 引导卡 → `Shizuku.requestPermission(code)`（必须从 Activity 发起）→ `OnRequestPermissionResultListener` 接收结果 → `EngineManager.refresh()`。
- `POST_NOTIFICATIONS` 已在当前版本接入 Manifest 和 Activity 运行时申请；后续阶段权限为 P2 Device Owner、P3 root，按设计文档补齐。

---

## 8. Architecture (package `com.nbljsbdk.snowhide`)

依赖单向：层级流向为 `feature → domain → data → core`；直接 import 遵循 `feature → domain/ui`、`domain → data/core`、`data → core`；feature 间禁止互相 import。

```
MainActivity.kt        — 系统入口：CompositionRoot.initActivity() + setContent + Shizuku 授权回调及系统适配

app/                  ★组合根
  CompositionRoot.kt   — 唯一依赖装配点，初始化 Repository、EngineRegistry、反馈适配和 AppContainer
  AppContainer.kt      — 持有页面需要的 UseCase 依赖
  AppShell.kt           — 页面组合与导航

core/                  ★核心抽象（P0 就位，扩展点全在这）
  engine/
    Engine.kt          — PowerEngine 接口（宪法，永不改签名；新能力=默认实现返回失败）
    TargetedPowerEngine.kt — 按具体用户空间查询和冻结/解冻的可选能力
    EngineManager.kt   — 注册表 + 探测 + StateFlow（UI 注册表驱动渲染）
    impl/ShizukuEngineImpl.kt — P0 实现：Shizuku UserService 执行 pm 命令
    impl/RootEngineImpl.kt / DoEngineImpl.kt — 空壳（P3/P2）
    registry/EngineRegistry.kt — 唯一知道「有哪些引擎」的文件（加引擎=这里一行）
  model/AppTarget.kt / UserProfile.kt — 包名+用户空间目标和用户信息模型
  operation/PmQuery.kt / PmOutputParser.kt — 受控用户空间查询和输出解析
  mode/
    FreezeMode.kt      — 枚举（isImplemented 标志，未开放灰显）
    FreezeExecutor.kt  — 引擎×模式分发
  feedback/
    HapticType.kt      — 分场景震动反馈类型
  accessibility/
    AccessibilityServiceState.kt — 系统启用状态端口和服务实际连接状态

data/
  model/GridModels.kt  — GridItem / Folder / FolderApp（type/parent 概念见设计文档 §4）
  repo/GridRepository.kt — 宫格/文件夹/排序/整理目录全部数据操作（SP+JSON，StateFlow）
  repo/ListOrderRepository.kt — 增删应用/快速启停滑入列表顺序（排序档位不持久化）
  repo/AppListRepository.kt — 已安装应用扫描、名称和图标信息
  repo/FrozenStateStore.kt — 全局冻结状态查询与 StateFlow
  repo/BackupRepository.kt — v1 备份数据读写
  repo/QuickToggleRepository.kt — 快速启停成员和 opened 状态
  repo/RecentFreezeQueueRepository.kt — Recent 补执行短队列
  repo/RecentCalibrationRepository.kt — Recent 校准包名/窗口锚点（SP+StateFlow）
  repo/AppCloneRepository.kt — 应用分身当前用户空间选择（SP+StateFlow）
  prefs/SettingsRepository.kt — 布局/壁纸/开关/图标包（SP 持久化）

domain/
  FreezeUseCase.kt     — 冻结/解冻/批量/快速清理/划卡停用（UI 永不直连 executor）
  BatchExec.kt         — 批量执行、分块和进度规则
  backup/BackupUseCase.kt — 备份导入导出校验与业务结果
  recent/              — Recent 会话状态、候选策略和校准业务
  folder/              — 文件夹页面规划
  folder/FolderPageSettingsUseCase.kt — 循环、排除和返回主屏设置入口
  settings/AppearanceSettingsUseCase.kt — 外观设置入口
  organize/            — 整理目录状态机与 Reducer
  appclone/AppCloneUseCase.kt — 用户空间选择、状态查询和目标冻结/解冻
  accessibility/AccessibilityRequirementUseCase.kt — 无障碍依赖提示规则（不作为冻结门禁）
  appmanage/           — 应用管理冻结规划
  QuickToggleUseCase.kt — 快速启停成员与执行规则

feature/
  home/                — 主屏幕（HomeViewModel + HomeScreen + 宫格设置浮框）
    appmanage/           — user 0 与非 user 0 目标增删应用；左栏可切换已有分身
  folder/ organize/ settings/ about/ — 主功能页；划卡停用与版本历史在 settings/about

service/               — 锁屏清理与 Recent 划卡停用；未来扩展 tile
  LockCleanAccessibilityService.kt — 唯一无障碍服务入口
  RecentSwipeController.kt — Recent 会话快照、校准、差异和即时冻结
  RecentTaskParser.kt — Recent 无障碍节点解析

platform/
  accessibility/AndroidAccessibilityServiceSettingsReader.kt — 读取系统无障碍启用状态

ui/
  util/AppIconLoader.kt — 图标包协议 + 系统回退 + 缓存
  util/FeedbackController.kt — Toast 总开关与后台失败通知
  util/HapticController.kt — 震动硬件调用与系统静音/勿扰过滤
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
2. Shizuku 实现：`pm disable-user --user 0 <pkg>` / `pm enable <pkg>`（通过 `Shizuku.bindUserService` 启动 `ShellCommandService`，在 shell 身份执行）。
3. 冻结状态查询：`pm list packages -d` 批量解析（`listFrozenPackages()`），一次刷新全列表。
4. 结果 Snackbar 提示（设置里有 toast 开关）。

### Recent 划卡停用
- 每次进入 Recent 建立当前快照；确认已添加且未锁定应用从快照消失后立即冻结，短期持久化队列用于无障碍服务重连后补执行，退出 Recent 只负责会话收尾。
- 校准记录为空时首次进入自动校准且不冻结；手动校准用于修复识别失败。
- 只冻结已添加且未锁定的应用，候选、任务快照和执行前均排除雪藏自身；不查询当前冻结状态，Recent 逐包经 `FreezeUseCase.freezeApp()` 执行命令，不进入全局 `BatchProgress`。
- 无障碍事件在服务层合并处理，节点遍历和单包冻结均放在后台执行，避免主线程 ANR。

### 整理目录
- 模块具体状态、转移、键位和持久化规则见 [`docs/modules/organize.md`](docs/modules/organize.md)。
- 通用边界：界面通过 ViewModel/Reducer 驱动，结构数据由 `GridRepository` 统一持久化；新增整理逻辑不得绕过该边界。

### 底部图标栏（设计文档 §3.6）
- 显示 = 已添加且解冻的应用；长按锁定（持久化，豁免快速清理/息屏清理）；上划冻结；最右快速清理

---

## 10. Platform gotchas (learned the hard way — keep these)

- **Shizuku 包名与权限命名空间（关键，容易写错）**：
  - Shizuku **应用包名 = `moe.shizuku.privileged.api`**（进程名、启动入口都用它；`getLaunchIntentForPackage` 必须用它）
  - `moe.shizuku.manager` **不是可启动包名**，只是权限/组件的命名空间前缀（如 `moe.shizuku.manager.permission.API_V23`）
  - 打开 Shizuku 管理器、检查进程都要用 `privileged.api`
- **Shizuku 启动竞态**：服务刚重启或 Binder 尚未重新挂载时，`checkSelfPermission()` 可能抛 `Not an attached client`；引擎可用性探测必须 `runCatching`，异常按暂不可用处理，不能让主界面启动崩溃。
- **Shizuku 授权与权限**：授权结果走 `OnRequestPermissionResultListener` 回调（listener 模式，**不是** onRequestPermissionsResult 转发——13.x 无此方法）。`requestPermission` 前必须 `pingBinder()` 检查，否则抛 `binder haven't been received`。**执行 pm 命令用 UserService 方案**：`Shizuku.bindUserService` 把 ShellCommandService 拉起到 shell 身份进程，手写 Binder 事务执行 `pm disable-user/enable`（13.1.5 的 `newProcess` 是 private 不可用）。
- **R8 混淆会杀死 Shizuku UserService（release 专属坑）**：ShellCommandService 由 Shizuku server 端实例化（构造器/onTransact 经框架调用），release 混淆后构造器被破坏 → 所有 pm 命令静默挂起（冻结解冻无效、增删界面移出卡死）。**proguard-rules.pro 必须 keep 该类**（已加，勿删）。
- **卸载重装会清掉 Shizuku 授权**：API_V23 是 dangerous 权限，覆盖安装（`install -r`）保留、卸载重装清零且可能带「不再询问」标记 → 授权弹窗弹不出。真机调试需要时用 `adb shell pm grant <pkg> moe.shizuku.manager.permission.API_V23`。
- **ColorOS 吞后台 Toast** → 通知兜底（本项目 P0 前台操作为主，受影响小）。
- **任务移除后进程被回收** → Recent 使用系统托管无障碍服务，不另起常驻前台服务；即时冻结队列落 SharedPreferences，服务重连后补执行；`stopWithTask=false` 防止划掉雪藏任务时主动停止服务。
- **OPPO 静态 shortcuts 崩** → 用动态 ShortcutManager（MediaSync 已验证方案）。
- **图标包协议**：发现 `queryBroadcastReceivers(INSTALL_ICON_PACK)`；请求 `RESOLVE_ICON` 有序广播（`sendOrderedBroadcast` + result receiver 取 `icon` extra）；失败回退系统图标。
- **`pm list packages -d`**：输出 `package:com.xxx` 逐行解析（`-d -s` 只列系统禁用项，神之一手目标过滤用）。
- **binder.transact 是同步调用**（sh 进程跑完才返回）——必须在 IO 线程执行（`withContext(Dispatchers.IO)`），主线程执行批量命令会 ANR（真机实锤）。
- **图标包 appfilter 协议**：轻语/轻风等无 INSTALL_ICON_PACK 广播的包，直接解析 `assets/appfilter.xml`（`createPackageContext(pkg, CONTEXT_RESTRICTED)` 读 assets/资源；`CONTEXT_INCLUDE_CODE` 从 Android 11 起被禁——SecurityException 实锤）。匹配用**包名优先**（冻结应用 `getLaunchIntentForPackage` 返回 null，组件匹配失效）。并发解析要 synchronized（113 并发卡死实锤）；getIdentifier 缓存（IPC 慢）。
- **LazyGrid key 重复崩溃**：同文件夹重复成员（历史数据 moveAppToFolder 缺防重）→ FolderScreen `key={it}` 崩。init 自动清理 + moveAppToFolder 防重 + 渲染 distinct 兜底。搜索成员 item 的 id 必须绝对唯一（负数 hash 会碰撞，用 `Long.MIN_VALUE+index`）。
- **ColorOS/realme 激进杀后台**：NoDisplay Activity 立即 finish 会被秒杀进程（快捷方式动作丢失+无反馈）——Translucent 保活窗口直到执行完成。后台 Toast 被吞（Android 12+ 后台 toast 限制）——Toast 在窗口存活期间发，失败兜底通知。
- **日志门控**：调试日志一律用 `if (BuildConfig.DEBUG) Log.d(...)`——debug 有、release 自动无（一键开关约定）。
- **Recent 无障碍识别**：使用 `AccessibilityNodeInfo` 解析窗口，候选应用由已添加列表提供；窗口包名/类名锚点持久化，未知 ROM 通过手动校准修复。
- **Compose BOM 2024.09.00**：`TextOverflow.MiddleEllipsis` 不可用（用 Ellipsis）；`animateFloatAsState` 需要 `androidx.compose.animation.core.animateFloatAsState`（注意 import 路径，旧 BOM 下 animation 包结构）。
- **debug/release 包名**：`com.nbljsbdk.snowhide` / `...snowhide.debug`——logcat 过滤、数据目录、Shizuku 授权均按包名独立。
- **SP+JSON 存储键**：`snowhide_grid`（grid_items/folders/folder_apps）、`snowhide_settings`。
- **锁屏精确闹钟权限**：未授予 `SCHEDULE_EXACT_ALARM` 时，锁屏清理必须回退 `setAndAllowWhileIdle()`，不能直接调用 `setExactAndAllowWhileIdle()` 让广播接收器崩溃。

---

## 11. Recent features (current state — 2026-08-27)

```
4a20b4d chore: 发布 v0.4.2
610c812 fix(home): 修复 Dock 拖动与冻结反馈延迟
9897d84 chore: 发布 v0.4.1
2e3c88b feat(home): 增加动画速度设置
4306543 docs: 同步 v0.4.0 发布状态
309c05d chore: 发布 v0.4.0
ae99343 feat(home): 将重进设置并入目录设置
dcb9c2f feat(home): 增加宫格设置并完善返回层级
b206ae1 chore: 发布 v0.3.2
3b74430 docs: 修正宫格循环顺序说明
d361a73 test: 覆盖主屏文件夹循环顺序
a05bd2f fix: 统一主屏与文件夹循环顺序
cb3cfa4 docs: 记录发布提交纪律
8349cbc chore: 发布 v0.3.1
ee370b4 style: 调整设置页卡片层级
5936878 feat: 增加分场景震动反馈
62bdef4 feat: 添加提示与反馈设置
69df809 feat: 完善应用列表管理与启动容错
44a18f3 feat: 全局震动与锁屏清理说明
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
0ac9e93 feat: 完成 v0.3 架构施工与回归修复
0222799 docs: 更新 v0.3.0 发布记录
9cdc536 feat: 优化 Dock 操作槽并固化个人设置
43cc632 test: 覆盖 Dock 设置备份读写
01c771b docs: 拆分整理目录模块文档
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
- 增删应用/快速启停方向手势限制、最近添加排序、图标形状同步、包名整行显示
- 提示与反馈三级页：统一 Toast 开关、重进主屏提示开关、前台 Snackbar/后台失败通知分流
- 震动反馈三级页：总开关 + 导航/冻结锁定/整理列表/批量四类强度，动作确认或完成时触发
- 设置页卡片层级：提示与反馈/震动反馈独立卡片进入三级页；宫格相关设置统一移至主屏长按菜单，整体视觉协调留待后期 UI 优化
- v0.2.0：Recent 划卡停用（自动/手动校准、会话差异、退出后批量冻结）完成并通过真机验证；经 Shizuku 静默读取真实任务包名，支持 ColorOS 同名卡片并优化滑动时序
- v0.2.0：关于应用显示语义版本、编译时间和版本历史
- v0.2.1：修复 Recent 基线建立期间误停用全部应用；改为任务快照确认后逐包即时停用，不触发主界面批量进度条；增加持久化补执行队列、雪藏自身排除和应用名 Toast
- v0.2.2：统一 Recent 划卡停用提示文案；补充渐进式解耦架构指导
- v0.3.0：组合根/AppShell、受控命令、QuickToggle/Backup 数据边界、Recent/整理/文件夹纯逻辑、反馈平台适配、JVM 回归测试和 Android Test 编译门已落地；Release 真机三条回归通过并已发布
- v0.3.1：Dock 右侧操作槽图标独立调节与形状同步；固化个人设置默认值；备份支持操作图标大小；整理目录规则文档补齐
- v0.3.2：修复主屏宫格文件夹顺序与循环滑动页面顺序不一致；新增主屏文件夹顺序回归测试
- v0.4.1：美化设置新增四档动画速度；统一控制主屏/文件夹导航、重进主屏、搜索回主屏、Dock 回弹和冻结滤镜等显式动画；关闭时瞬时切换；备份兼容旧动画开关并补充回归测试
- v0.4.2：修复 Dock 上划拖动帧排队导致的不跟手；释放后立即隐藏目标图标，失败恢复、成功即时更新共享状态并后台校准；增加重复冻结请求保护

**当前正式版（`0.5.0`，2026-09-03，已完成 Release 编译和自动化验证；应用分身行为沿用 2026-09-01 用户确认）**：
- 应用分身已并入「增删应用」：顶栏复选框只切换左栏数据源，按现有非 user 0 用户空间读取用户/系统应用和冻结状态；右滑将完整 `AppTarget` 加入右栏及主屏 Grid/文件夹体系，“应用”再按目标执行冻结，绝不回退 user 0。复选框、user 999 识别、用户/系统过滤、目标命令和 user 0 右栏隔离已通过此前真机验证。Recent 通过 shell `dumpsys activity recents` 保留任务 `userId`；缺少或无法唯一匹配用户空间时拒绝目标，不投影到 user 0。
- 主屏搜索框已补左侧间距；当划卡停用或锁屏清理已选中、但无障碍未启用或宽限期后仍未连接时，主屏显示引导卡，基础冻结、宫格和 Dock 不受影响。
- `pm list users` flags 按 Android 十六进制协议解析；分身目标命令在 UseCase 内串行排队，连续右滑不会静默丢操作。
- 所有冻结/解冻命令和冻结状态查询均显式指定用户空间；Recent 队列保存 `AppTarget`，状态刷新不会用旧查询结果覆盖刚完成的命令；用户已确认主应用与分身隔离行为无明显问题。
- 所有冻结/解冻命令和冻结状态查询均显式指定用户空间；Recent 队列保存 `AppTarget`，状态刷新不会用旧查询结果覆盖刚完成的命令。
- 应用管理冻结规划只接受明确处于 `ACTIVE` 状态的目标；未知、缺失或冻结状态不会进入新增冻结计划，操作失败时保留页面状态并显示错误。

> P1/P2/P3 是当时制定的开发规划；现在根据开发进度和实际需求调整开发顺序，以下顺序为当前实际优先级。

**未完成（按当前实际优先级）**：
1. 批量停用/启用性能优化：Dock 单个上划拖动与即时反馈已在 v0.4.2 修复；仅优化批量操作，并保持逐包进度、安全边界和 Binder IO 线程规则。
2. 批量进度条的挖孔、Edge-to-Edge 和横屏适配：处理顶部摄像头区域黑边，以及横屏时该区域与宫格栏颜色不一致。
3. 锁屏自动清理例外目录（锁定应用豁免已完成）。

**低优先级（当前用不到，后续再做）**：
5. P1：休眠模式（`pm suspend`）。
6. P1：壁纸图片选择器和图片背景实际渲染。
7. P2：固定桌面快捷方式（`requestPinShortcut` 接口已留；动态快捷方式已完成）。
8. P2：Device Owner / Dhizuku 引擎。
9. P3：root 引擎。
10. P2/P3：禁用/隐藏模式及多引擎身份一致性。
11. 应用桥（已完成调研，低优先级）。
12. 设置页整体视觉美化。

**最后处理（低影响问题）**：
- 只在超超超快速左右滑动时会触发交界处闪回；影响较小，战略性撤退，最后修复。

**0.5.0 发布内容**：
- 应用分身并入增删应用，主应用与分身使用完整 `AppTarget` 隔离；分身目标写入 Grid/文件夹、锁定集、快速启停和 Backup v1。
- 所有冻结、解冻、批量执行和状态查询均使用明确用户空间；无效用户空间或未安装目标拒绝执行，不回退 user 0。
- Recent 任务快照读取并保留 `userId`；同包名跨用户目标按身份差异冻结，缺少用户信息且有歧义时不执行。
- 修复冻结状态刷新竞态和手动解冻后旧 Recent 队列继续执行的问题；新增跨用户 JVM 回归测试。
- 应用管理补充系统应用筛选、状态过滤、重复目标和失败路径测试，避免未知状态误冻结。
- 继承 `0.4.2` 的 Dock 跟手、四档动画速度、主屏/文件夹导航动画和 Backup v1 兼容规则。

## 12. Known limitations / candidate next tasks

1. **debug 包比 release 卡**：debug 无 R8 优化 + debuggable，大列表渲染差异明显——日常使用 release（正常）。
2. **图标包覆盖不全**：未收录的应用回退系统图标（可配合「图标形状=圆形」统一视觉）。
3. **Android instrumentation**：此前 `connectedDebugAndroidTest` 在 RMX3888（Android 14）运行 6 个测试全部通过；最近一次重跑在启动阶段超时，当前不作为待办，之后再处理。
4. **应用桥**：功能已研究清楚（黑白门 3.3.3 反编译实证），当前用不到，低优先级；快速启停一定程度替代它。
5. **调试日志已清空**：以后加日志用 `BuildConfig.DEBUG` 门控。
6. **应用分身真机验证**：RMX3888 的最终 Release 已识别运行中的 ColorOS `user 999 (MultiApp)`；复选框、筛选、目标右栏/Grid、显式用户命令和 user 0 隔离已完成验证，用户确认最新版本似乎无问题。当前设备唯一第三方分身目标的原有冻结状态未被 agent 改动。
7. **跨用户独有应用元数据**：Android 当前用户的 PackageManager 无法直接提供仅存在于其他用户空间的名称、图标和安装时间；此类目标暂以包名和系统图标回退，不为此引入未经验证的隐藏 API。
8. **设置页整体美观待优化**：当前功能卡片层级已按类别整理，视觉协调性留待后期统一处理。
9. **批量操作性能待调查（当前第二优先级）**：Dock 单个上划拖动与即时反馈已在 v0.4.2 修复；“全部停用/启用”等批量操作仍需先采样 Binder、命令和 UI 等待时间，再决定是否优化。
10. **挖孔与横屏显示待调查（当前第三优先级）**：批量进度条附近出现摄像头挖孔区域黑边，横屏时该区域与宫格栏颜色不一致；需结合真实设备的 WindowInsets、DisplayCutout 和系统栏策略复现。
11. **锁屏自动清理例外目录（当前第四优先级）**：锁定应用豁免已完成，尚需增加例外目录选择和清理候选过滤。
12. **极高速左右滑动交界处闪回（最后处理）**：只在超超超快速左右滑动时触发，影响较小，当前战略性撤退。

---

## 13. Repo / git

- 当前分支：`master`，当前正式发布提交：`e872da5`（`v0.5.0`）；上一正式发布提交：`4a20b4d`（`v0.4.2`）；历史回退基线：`5cd8ce6`（`baseline-before-v0.2.0` tag）。
- `v0.4` 基线 Tag 已指向 `666eae5`（主线开始 `0.4.0-dev` 开发）；开发阶段不创建 `-dev` Tag。
- `v0.4.0` 正式发布提交 `309c05d` 已完成，`master` 分支和 `v0.4.0` Tag 已推送。
- `v0.4.1` 正式发布提交 `9897d84` 已完成，`v0.4.1` Tag 已创建并推送。
- `v0.4.2` 正式发布提交为 `4a20b4d`，`v0.4.2` Tag 已创建并推送。
- `v0.5.0` 正式发布提交为 `e872da5`，Tag 创建后保持不移动。
- 基线历史仍包含 `v0.1.6`（指向 `69df809`）及提示、震动、设置页卡片层级功能。
- `v0.2.0` 已在真机验证完成并获得用户明确授权后创建；后续版本仍须遵循同样的验证和授权流程。
- `v0.2.1` 已在真机验证完成并获得用户明确授权后创建；后续版本仍须遵循同样的验证和授权流程。
- `v0.2.2` 已创建，内容为 Recent 提示修正与渐进式解耦架构指导；后续版本仍须遵循同样的更新说明流程。
- `v0.3.0` 已由提交 `0ac9e93` 创建并推送；Release 真机三条回归已通过，Android instrumentation 仍待后续补跑。
- `v0.3.1` 发布说明、Release 构建和真机覆盖安装已完成，本次发布提交后创建并推送 Tag。
- `v0.3.2` 更新说明、Release 构建和真机覆盖安装完成，本次发布提交后创建并推送 Tag。
- 未经用户明确允许，不执行 commit、amend、push 或创建 tag。

---

## 14. adb usage

- adb 位置：`C:\toolpath\platform-tools\adb.exe`（WSL 对应 `/mnt/c/toolpath/platform-tools/adb.exe`）。
- **Shizuku 激活固定使用此命令**：`adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
- **纪律**：编译完成后 agent 可自动执行 `adb install -r` 覆盖安装验证 APK（不清除应用数据）；卸载、清数据和修改设备包管理器状态等危险操作仍须先征得用户同意。
- 无线调试：配对端口 ≠ 连接端口（动态）；offline 用 `kill-server` 清除；多设备加 `-s <ip:port>`。
- logcat：`-c` 清 → 复现 → `-d -s <TAG>`（崩溃看 `AndroidRuntime`）。
- 安装：`adb -s <ip:port> install -r app/build/outputs/apk/debug/app-debug.apk`（versionCode 恒 1，覆盖装永远有效）。

---

## 15. Working agreement (for the agent)

- 用**中文**沟通；代码注释中文 KDoc（MediaSync 风格）。
- 铁律：§6 文件安全、§4 versionCode、§13 git 纪律。
- 分层纪律：feature 间零 import、UI 无状态、注册表驱动、impl 隔离。
- 改权限/版本/插件/架构时，同步更新本文件与 `~/opencode_dev/docs/冻结工具设计.md`。
- 未经用户明确允许，不写应用代码、不修改功能性文件；用户明确要求同步文档时，只修改指定文档。
- 未经用户明确允许，不执行 commit、amend、push 或创建 tag；编译完成后的 `adb install -r` 可自动执行，卸载、清数据和修改设备包管理器状态仍须明确授权。
- 遇到困难不擅自退缩、跳过或终止；主动说明阻塞原因，并询问用户是否继续或是否执行下一步操作。
- 编译通过后可以直接安装 APK 并继续真机验证；commit、amend、push、创建 tag 仍必须先获得用户明确授权。
- 用户明确授权后，每个可编译的小功能点再单独 commit 存档（§13 规范）。
- **HARD RULE（正式发布更新说明）**：准备正式版本发布提交、push 或创建 Tag 前，必须先根据 Git 版本区间核对并更新 `app/src/main/java/com/nbljsbdk/snowhide/feature/about/VersionHistory.kt`；发布状态同时同步到 `AGENTS.md`、`v0.3task.md` 和设计文档。没有对应且真实的更新说明，不得提交正式发布版本、推送或创建 Tag。
- **开发版与发布版边界**：功能/修复开发阶段使用 `-dev` 版号，功能验收通过后可以先提交功能存档；不提前伪造正式版本历史。正式版号、`VersionHistory.kt` 和正式更新说明只进入专门的发布提交。
- **发布提交教训**：`v0.2.0` 历史曾把 Recent 划卡停用功能和更新说明放在同一发布提交；已发布历史不重写。以后功能实现、测试、模块文档和正式版本说明/版本号按独立提交组织，正式版本说明与版本号只进入专门的发布提交，确认后再推送和创建 Tag。
- **HARD RULE（用户强调）**：集成任何三方库前，**必须先看官方文档并核对 jar 内真实 API 签名**，绝不凭训练数据假设 API 存在（训练数据会过期）。Shizuku 教训：`newProcess` 已私有、`onRequestPermissionsResult` 转发已移除、`ShizukuProvider` 必须手动声明——三个坑全是凭训练数据写的。正确做法：① webfetch 官方 README/GitHub ② javap/jadx 反编译核对 jar 签名 ③ 再写代码。

---

## 16. 渐进式解耦指导（长期硬约束）

### 16.1 目标

本项目不进行一次性大重构，不为了“架构漂亮”重写已经真机验证过的业务。

目标是：新功能不再扩大耦合；每次修改一个小功能时，只顺手把该功能触及的边界向正确方向迁移一小步。旧代码允许暂时存在过渡依赖，逐个功能收敛，直到达到目标结构。

### 16.2 目标架构树

```text
com.nbljsbdk.snowhide/
├── MainActivity.kt                         # 极薄入口：Activity 权限与 CompositionRoot
├── app/                                    # 唯一组合根（逐步新增）
│   ├── CompositionRoot.kt                  # 冷启动初始化与依赖装配
│   └── AppShell.kt                         # 页面组合与导航
├── core/                                   # Android 无关核心抽象/引擎契约
│   ├── engine/                             # PowerEngine、Manager、Registry、impl
│   ├── mode/                               # FreezeMode、FreezeExecutor
│   └── feedback/                           # HapticType、反馈接口（逐步新增）
├── data/                                   # SP+JSON、缓存、Repository、数据模型
│   ├── model/
│   ├── prefs/
│   └── repo/
├── domain/                                 # 业务规则和 UseCase
│   ├── recent/                             # Recent 纯状态与业务差异
│   ├── backup/                             # 备份导入导出业务
│   └── quicktoggle/                        # 快速启停业务
├── feature/                                # 页面状态、交互、无状态 Compose
│   ├── home/
│   ├── settings/
│   ├── appmanage/
│   ├── organize/
│   ├── folder/
│   ├── quicktoggle/
│   ├── about/
│   └── shortcut/
├── service/                                # Android 系统入口和事件适配
│   ├── LockCleanAccessibilityService.kt
│   ├── LockCleanReceiver.kt
│   ├── QuickToggleTileService.kt
│   ├── RecentSwipeController.kt
│   ├── RecentTaskParser.kt
│   └── RecentTaskSnapshotProvider.kt
├── platform/                               # 共享 Android 适配（有真实需要再新增）
│   ├── feedback/
│   └── recent/
├── ui/                                     # 纯视觉组件、主题、Modifier、图标视觉工具
│   ├── components/
│   ├── theme/
│   └── util/
└── bridge/                                 # 外部应用桥；当前保持预留
```

### 16.3 依赖方向

`A → B` 表示 A 可以依赖 B：

```text
app/组合根 → feature / service / domain / data / core / platform
feature    → domain / ui
service    → domain / core / platform
domain     → data / core
data       → core
platform   → core
ui         → core
bridge     → core
```

严格禁止新增以下依赖：

```text
data     ✕→ ui / feature / service / domain
domain   ✕→ Compose / Activity / Service / Toast / Notification / SharedPreferences
service  ✕→ feature / ui
feature  ✕→ service / 其他 feature
ui       ✕→ data / service / feature
feature  ✕→ ShizukuEngineImpl / RootEngineImpl 等具体实现
```

现有的 `feature → data.repo`、`service → ui.util`、`data → ui` 等越层依赖暂不一次性清除；以后触碰相关功能时，必须优先迁移当前调用点，且新代码不得继续复制这种写法。

### 16.4 各层职责

**core**

- `PowerEngine` 接口是宪法，不随功能改签名。
- 具体引擎和第三方 API 只在 `core/engine/impl/` 内出现。
- `EngineRegistry` 是唯一注册引擎的文件；新增引擎原则上只增加 impl 文件和注册行。
- `FreezeExecutor` 负责引擎×模式分发，不负责 UI 状态和提示。

**data**

- 只负责持久化、缓存、JSON 编解码和数据 StateFlow。
- Repository 是某类数据的唯一所有者。
- 新增 SP key 必须放在对应 Repository，禁止 feature/service 直接读写 SharedPreferences。
- 现有 key、备份格式和已保存数据必须向后兼容。
- `SettingsRepository` 不再新增对 `ui` 类型的依赖；视觉枚举以后迁移到 data model 或 core model。

**domain**

- 负责业务规则、过滤、状态转换和 UseCase。
- 所有冻结/解冻/批量/Recent 动作必须经 domain，不允许 UI 或 service 拼接业务命令。
- domain 不创建 Toast、Notification、Activity、AccessibilityService 或 Compose 状态。
- 批量入口可以使用 `BatchExec`；Recent 单包入口必须调用 `FreezeUseCase.freezeApp()`，不得触发全局 `BatchProgress`。

**feature/ui**

- ViewModel 只暴露 `UiState`、事件和动作，不把 Repository 暴露给 Composable。
- Composable 只接收 state 和 callback，不直接访问 EngineManager、Repository、SharedPreferences 或 Service。
- `feature` 之间不互相 import；页面组合由 `app/AppShell.kt` 负责。
- 主题、颜色、间距、字体、动画和卡片视觉优先放在 `ui/`，不要把视觉判断写进 domain。

**service**

- 只负责系统生命周期、事件接收、节点读取和调度。
- `RecentTaskParser` 只解析无障碍节点，不执行冻结。
- `RecentTaskSnapshotProvider` 只负责读取系统任务身份，不决定业务结果。
- `RecentSwipeController` 逐步缩小为 Android 事件/Handler/生命周期适配层；Recent 会话状态、候选过滤和冻结队列业务逐步迁移到 `domain/recent`。
- service 不直接依赖 `ui.util.FeedbackController`；反馈能力以后通过 `core/feedback` 接口或 `platform` adapter 注入。

### 16.5 重点模块的解耦边界

**Home**

- 当前 `HomeScreen.kt` 允许继续工作，但新代码不得继续在其中增加业务分支。
- 逐步拆成 `HomeRoute`、`HomeContent`、`HomeGrid`、`HomeDock`、`HomeDialogs`。
- `HomeRoute` 收集状态并处理副作用；`HomeContent` 和子组件只绘制并回调。
- 先抽无状态 UI，再移动 ViewModel 业务，不改变 Pager、手势、冻结和导航行为。

**Settings**

- `SettingsScreen` 逐步变成页面组合和设置项展示层。
- 备份导入导出迁移到 `BackupUseCase` + `BackupViewModel`，保留现有 JSON 格式。
- Recent 设置页通过 callback 或 domain 门面请求校准，不直接 import `RecentSwipeController`。
- About 不直接依赖其他 feature 的 ViewModel。

**Recent**

- 首次任务快照未建立前，任何差异都不得停用。
- parser 负责识别，snapshot provider 负责事实，domain 负责差异和策略，controller 负责时序。
- 新增 `domain/recent/RecentSessionState.kt`，先抽纯 Kotlin 状态，不先搬 Android Handler。
- 新增 `domain/recent/RecentSwipeUseCase.kt`，逐步收回候选过滤、锁定判断、队列和逐包冻结。
- `RecentFreezeQueueRepository` 保存带 `userId` 的短目标队列；读写、schema、成功移除和失败保留必须保持安全，旧包名-only 队列不得映射到分身。
- 雪藏自身在候选、任务快照、队列和执行入口全部排除。

**QuickToggle**

- 新增 `QuickToggleRepository` 作为成员列表和 opened 状态唯一数据源。
- `QuickToggleUseCase` 负责成员和执行规则。
- `QuickToggleViewModel` 负责搜索、排序和 UI 状态。
- TileService 和 ShortcutActionActivity 只做系统入口，不能各自解析 JSON 或复制业务。

**Backup**

- `BackupRepository` 先保持 v1 JSON 读写兼容。
- `BackupUseCase` 负责导入校验、版本判断和导入结果。
- `SettingsScreen` 只处理 SAF Uri 和页面反馈。
- 未经单独设计，不修改既有备份 key，不删除旧字段。

**初始化**

- Activity、AccessibilityService、Receiver、TileService 的冷启动初始化逐步集中到 `CompositionRoot`。
- 不再在每个入口复制 EngineRegistry、Repository、UseCase 装配代码。
- `MainActivity` 仍保留 Shizuku Activity 权限请求和 binder 回调。
- 不引入 Hilt/Koin，仅使用轻量手写组合根。

### 16.6 每个小功能的工作规则

每次新增一个小功能时，按以下顺序执行：

1. 先确定功能真正属于 UI、feature、domain、data、service 还是 core。
2. 业务动作优先进入已有 UseCase；没有合适 UseCase 才新增一个小 UseCase。
3. 持久化字段只进入 Repository；不在 UI/service 内直接写 SP。
4. Android 系统入口只接收事件，业务交给 domain。
5. 新 Composable 必须保持无状态。
6. 如果触碰旧越层代码，只迁移本次功能涉及的局部调用，不做全局重写。
7. 一个小功能最多附带一个相邻的局部解耦动作，避免功能和重构互相遮蔽。
8. 保持既有行为、SP key、备份格式、版本规则和安全约束不变。
9. 完成后至少执行编译、相关真机验证和 `git diff --check`。
10. 每个可编译小功能单独存档；commit、push、tag 仍按 §13 授权规则执行。

### 16.7 分阶段迁移顺序

**阶段 0：规则生效**

- 只更新本指导，不改业务行为。
- 新代码停止增加 feature→feature、service→ui、data→ui 等依赖。

**阶段 1：组合根**

- 新增 `app/CompositionRoot.kt`。
- 统一冷启动初始化和 UseCase 装配。
- 不改变业务时序，不改持久化格式。

**阶段 2：数据所有权**

- 先处理 QuickToggle 重复 JSON。
- 再处理 Backup 的 UseCase/ViewModel 边界。
- 触碰 `FrozenStateStore` 时再逐步处理它对 EngineManager 的直接依赖。

**阶段 3：UI 页面壳**

- Home 先拆 Route/Content，再拆 Dock、Pager、Dialog。
- Settings 先拆 Route/ViewModel，再处理各功能 section。
- 视觉优化优先在纯 UI 组件进行。

**阶段 4：Recent 纯逻辑**

- 先抽 `RecentSessionState`。
- 再抽差异、过滤和队列执行。
- 最后缩小 `RecentSwipeController`。
- 每一步单独编译和真机验证。

**阶段 5：共享 Android 适配和安全边界**

- 反馈、震动、任务读取等确实被多个入口共享时，再新增 platform adapter。
- 触碰 shell 命令时补充包名校验和受控命令参数，不能继续扩大 `sh -c` 字符串拼接风险。
- 不为了提前“完整架构”而创建空壳目录或抽象接口。

### 16.8 暂时不要大改

以下文件和协议除非当前功能明确涉及，否则保持稳定：

- `core/engine/Engine.kt`
- `core/engine/impl/ShizukuEngineImpl.kt`
- `core/engine/impl/ShellCommandService.kt`
- `core/mode/FreezeExecutor.kt`
- `data/repo/GridRepository.kt`
- `data/repo/BackupRepository.kt`
- `service/RecentSwipeController.kt`
- `service/RecentTaskParser.kt`
- `ui/util/AppIconLoader.kt`
- `AndroidManifest.xml`
- 既有 SP key、备份 JSON、Shizuku UserService 协议和 `versionCode=1`

不引入 Room、Hilt/Koin、多模块或事件总线解决当前问题；先用现有 SP+JSON、手写组合根和小步迁移保持可控。
