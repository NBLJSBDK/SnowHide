package com.nbljsbdk.snowhide.feature.home

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.FolderApp
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.BatchProgress
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementState
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementUseCase
import com.nbljsbdk.snowhide.domain.folder.FolderPageInput
import com.nbljsbdk.snowhide.domain.folder.FolderPagePlan
import com.nbljsbdk.snowhide.domain.folder.FolderPagePlanner
import com.nbljsbdk.snowhide.domain.folder.FolderPageSettingsUseCase
import com.nbljsbdk.snowhide.domain.settings.AnimationLevel
import com.nbljsbdk.snowhide.domain.settings.AppearanceSettingsUseCase
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutCreator
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutSpec
import com.nbljsbdk.snowhide.ui.util.AppIconLoader
import com.nbljsbdk.snowhide.ui.util.FeedbackController
import com.nbljsbdk.snowhide.ui.util.HapticController
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主屏幕 ViewModel（P0）
 *
 * 组合引擎状态、宫格数据、冻结状态、图标加载，
 * 是 feature/home 的唯一状态中枢（UI 无逻辑）。
 */
class HomeViewModel(
    application: Application,
    private val freezeUseCase: FreezeUseCase,
    private val appearanceSettingsUseCase: AppearanceSettingsUseCase,
    private val folderPageSettingsUseCase: FolderPageSettingsUseCase,
    private val accessibilityRequirementUseCase: AccessibilityRequirementUseCase,
    private val desktopShortcutCreator: DesktopShortcutCreator,
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val engineManager = EngineManager
    private val gridRepository = GridRepository
    private val settingsRepository = SettingsRepository

    val gridItems: StateFlow<List<GridItem>> = gridRepository.gridItems
    val homeFolderIds: StateFlow<List<Long>> = gridRepository.gridItems
        .map(::folderIdsInHomeOrder)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folders: StateFlow<List<Folder>> = gridRepository.folders
    val folderApps: StateFlow<List<FolderApp>> = gridRepository.folderApps
    val lockedTargets: StateFlow<Set<AppTarget>> = gridRepository.lockedTargets
    val folderPagePlan: StateFlow<FolderPagePlan> = combine(
        folders,
        homeFolderIds,
        folderPageSettingsUseCase.loopEnabled,
        folderPageSettingsUseCase.excludedFolderIds,
    ) { folderList, homeIds, loopEnabled, excludedFolderIds ->
        FolderPagePlanner.plan(
            folders = folderList.map { FolderPageInput(it.id, it.sortOrder) },
            loopEnabled = loopEnabled,
            excludedFolderIds = excludedFolderIds,
            homeFolderIds = homeIds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FolderPagePlanner.plan(emptyList()),
    )

    val columns: StateFlow<Int> = settingsRepository.columns
    val iconSize: StateFlow<Int> = settingsRepository.iconSize
    val verticalSpace: StateFlow<Int> = settingsRepository.verticalSpace
    val dockIconSize: StateFlow<Int> = settingsRepository.dockIconSize
    val dockActionIconSize: StateFlow<Int> = settingsRepository.dockActionIconSize
    val folderPreview: StateFlow<Int> = settingsRepository.folderPreview
    val showAppName: StateFlow<Boolean> = appearanceSettingsUseCase.showAppName
    val showReturnHomeButton: StateFlow<Boolean> = folderPageSettingsUseCase.showReturnHomeButton
    val folderPageLoopEnabled: StateFlow<Boolean> = folderPageSettingsUseCase.loopEnabled
    val excludedFolderIds: StateFlow<Set<Long>> = folderPageSettingsUseCase.excludedFolderIds
    val resetHomeOnReentry: StateFlow<Boolean> = folderPageSettingsUseCase.resetHomeOnReentry
    val showReentryToast: StateFlow<Boolean> = settingsRepository.showReentryToast
    val autoSyncStatus: StateFlow<Boolean> = settingsRepository.autoSyncStatus
    val iconPack: StateFlow<String> = appearanceSettingsUseCase.iconPack
    val transparentBg: StateFlow<Boolean> = appearanceSettingsUseCase.transparentBg
    val wallpaperOverlay: StateFlow<Float> = appearanceSettingsUseCase.wallpaperOverlay
    val iconShape: StateFlow<String> = appearanceSettingsUseCase.iconShape
    val animationLevel: StateFlow<AnimationLevel> = appearanceSettingsUseCase.animationLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnimationLevel.MEDIUM)
    val freezeStyle: StateFlow<String> = appearanceSettingsUseCase.freezeStyle

    /** 由组合根注入业务用例，避免页面 ViewModel 自行装配依赖。 */
    class Factory(
        private val application: Application,
        private val freezeUseCase: FreezeUseCase,
        private val appearanceSettingsUseCase: AppearanceSettingsUseCase,
        private val folderPageSettingsUseCase: FolderPageSettingsUseCase,
        private val accessibilityRequirementUseCase: AccessibilityRequirementUseCase,
        private val desktopShortcutCreator: DesktopShortcutCreator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(
                    application,
                    freezeUseCase,
                    appearanceSettingsUseCase,
                    folderPageSettingsUseCase,
                    accessibilityRequirementUseCase,
                    desktopShortcutCreator,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    // ═══════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════

    /** 冻结状态映射（pkg → 是否冻结，共享存储：磁贴/增删界面操作后同样刷新） */
    val frozenStates: StateFlow<Map<AppTarget, Boolean>> = FrozenStateStore.targetStates

    /** 正在执行冻结命令的应用，先从 Dock 隐藏，失败时恢复。 */
    private val _pendingFreezeTargets = MutableStateFlow<Set<AppTarget>>(emptySet())
    val pendingFreezeTargets: StateFlow<Set<AppTarget>> = _pendingFreezeTargets.asStateFlow()

    /** 应用系统实际状态（冻结/正常/已删除/无法确认） */
    val appStates: StateFlow<Map<AppTarget, AppRuntimeState>> = FrozenStateStore.targetAppStates

    /** 应用显示名映射（pkg → 中文名，来自全局预加载列表） */
    private val _labels = MutableStateFlow<Map<AppTarget, String>>(emptyMap())
    val labels: StateFlow<Map<AppTarget, String>> = _labels.asStateFlow()

    /** 操作提示（Snackbar 文案） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    val batchProgress: StateFlow<Float?> = BatchProgress.progress
    val batchLabel: StateFlow<String?> = BatchProgress.label

    /** 当前整理目录模式开关 */
    private val _organizing = MutableStateFlow(false)
    val organizing: StateFlow<Boolean> = _organizing.asStateFlow()

    /** 齿轮菜单开关 */
    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen.asStateFlow()

    /** 搜索状态 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    /** 增加/移除应用界面开关 */
    private val _appManageOpen = MutableStateFlow(false)
    val appManageOpen: StateFlow<Boolean> = _appManageOpen.asStateFlow()

    /** 设置页开关 */
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    fun openSettings() {
        _menuOpen.value = false
        _settingsOpen.value = true
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    /** 快速启停管理界面开关 */
    private val _quickToggleOpen = MutableStateFlow(false)
    val quickToggleOpen: StateFlow<Boolean> = _quickToggleOpen.asStateFlow()

    fun openQuickToggle() {
        _menuOpen.value = false
        _quickToggleOpen.value = true
    }

    fun closeQuickToggle() {
        _quickToggleOpen.value = false
    }

    /** 关于页开关 */
    private val _aboutOpen = MutableStateFlow(false)
    val aboutOpen: StateFlow<Boolean> = _aboutOpen.asStateFlow()

    fun openAbout() {
        _menuOpen.value = false
        _aboutOpen.value = true
    }

    fun closeAbout() {
        _aboutOpen.value = false
    }

    fun openAppManage() {
        _menuOpen.value = false
        _appManageOpen.value = true
    }

    fun closeAppManage() {
        _appManageOpen.value = false
    }

    // ═══════════════════════════════════════
    // 初始化 / 刷新
    // ═══════════════════════════════════════

    /** 引擎状态（Shizuku 授权状态 → UI 引导卡片），订阅 EngineManager 变化 */
    private val _engineReady = MutableStateFlow(EngineManager.isEngineReady())
    val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

    /** Shizuku binder 连接状态（区分「未运行」与「未授权」） */
    private val _shizukuRunning = MutableStateFlow(EngineManager.shizukuBinderConnected.value)
    val shizukuRunning: StateFlow<Boolean> = _shizukuRunning.asStateFlow()

    val accessibilityRequirement: StateFlow<AccessibilityRequirementState> =
        accessibilityRequirementUseCase.state.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            accessibilityRequirementUseCase.currentState(),
        )

    init {
        AppIconLoader.iconPackPkg = settingsRepository.iconPack.value
        // 启动预热图标包 appfilter 解析（否则首图标触发时阻塞 2 秒）
        viewModelScope.launch { AppIconLoader.prewarm() }
        // 订阅宫格数据变化，刷新中文名；图标属于 Compose 绘制层，由 HomeScreen 加载。
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                gridRepository.gridItems,
                gridRepository.folderApps,
                AppListRepository.installedApps,
            ) { items, folderApps, apps ->
                // 中文名映射（从预加载的全局应用列表取）
                val infoByPackage = apps.associateBy { it.pkg }
                val labelMap = (items.mapNotNull { it.appTarget } + folderApps.mapNotNull { it.appTarget })
                    .distinct()
                    .associateWith { target -> infoByPackage[target.packageName.value]?.label ?: target.packageName.value }
                _labels.value = labelMap
            }.collect { }
        }
        viewModelScope.launch {
            EngineManager.primaryEngine.collect { _engineReady.value = it != null }
        }
        viewModelScope.launch {
            EngineManager.shizukuBinderConnected.collect { _shizukuRunning.value = it }
        }
    }

    /** 手动刷新引擎状态（从 Shizuku 管理器返回后调用） */
    fun refreshEngineStatus() {
        EngineManager.refresh()
    }

    fun refreshAccessibilityStatus() {
        accessibilityRequirementUseCase.refreshSystemState()
    }

    /** 刷新全部已添加应用的冻结状态（一次批量查询，共享存储） */
    fun refreshFrozenStates() {
        viewModelScope.launch {
            FrozenStateStore.refresh()
        }
    }

    /** 手动/自动同步系统实际状态；自动模式静默，手动模式给结果 Toast */
    fun syncActualStatus(silent: Boolean = false) {
        viewModelScope.launch {
            val result = FrozenStateStore.refresh()
            if (!silent) {
                if (result.success) {
                    val message = if (result.missingCount > 0) {
                        "同步完成：发现 ${result.missingCount} 个已删除应用"
                    } else {
                        "同步完成：状态已更新"
                    }
                    toast(message)
                } else {
                    showMessage("同步失败：${result.errorMessage ?: "无法读取系统状态"}")
                }
            }
        }
    }

    /** 用户从设置切换图标包后刷新（持久化 + 重载全部图标） */
    fun applyIconPack(pkg: String) {
        appearanceSettingsUseCase.setIconPack(pkg) // 持久化（之前缺失，重启丢失）
        AppIconLoader.iconPackPkg = pkg
        AppIconLoader.clearCache()
    }

    fun toggleLock(target: AppTarget) = gridRepository.toggleLock(target)

    fun renameFolder(folderId: Long, name: String) = gridRepository.renameFolder(folderId, name)

    fun deleteFolder(folderId: Long) {
        gridRepository.deleteFolder(folderId)
        folderPageSettingsUseCase.removeFolder(folderId)
    }

    fun setShowAppName(enabled: Boolean) = appearanceSettingsUseCase.setShowAppName(enabled)

    fun setTransparentBg(enabled: Boolean) = appearanceSettingsUseCase.setTransparentBg(enabled)

    fun setWallpaperOverlay(alpha: Float) = appearanceSettingsUseCase.setWallpaperOverlay(alpha)

    fun setAnimationLevel(level: AnimationLevel) = appearanceSettingsUseCase.setAnimationLevel(level)

    fun setFreezeStyle(style: String) = appearanceSettingsUseCase.setFreezeStyle(style)

    fun setIconShape(shape: String) = appearanceSettingsUseCase.setIconShape(shape)

    fun setFolderPageLoopEnabled(enabled: Boolean) = folderPageSettingsUseCase.setLoopEnabled(enabled)

    fun setFolderExcluded(folderId: Long, excluded: Boolean) =
        folderPageSettingsUseCase.setFolderExcluded(folderId, excluded)

    fun setShowReturnHomeButton(enabled: Boolean) =
        folderPageSettingsUseCase.setShowReturnHomeButton(enabled)

    fun setResetHomeOnReentry(enabled: Boolean) =
        folderPageSettingsUseCase.setResetHomeOnReentry(enabled)

    fun setColumns(value: Int) = settingsRepository.setColumns(value)

    fun setIconSize(value: Int) = settingsRepository.setIconSize(value)

    fun setVerticalSpace(value: Int) = settingsRepository.setVerticalSpace(value)

    fun setDockIconSize(value: Int) = settingsRepository.setDockIconSize(value)

    fun setDockActionIconSize(value: Int) = settingsRepository.setDockActionIconSize(value)

    fun setFolderPreview(value: Int) = settingsRepository.setFolderPreview(value)

    // ═══════════════════════════════════════
    // 冻结操作
    // ═══════════════════════════════════════

    /** 切换冻结状态（长按菜单 / 底部栏上划） */
    fun toggleFreeze(target: AppTarget) {
        viewModelScope.launch {
            val frozen = FrozenStateStore.targetStates.value[target] ?: false
            if (!frozen && target in _pendingFreezeTargets.value) return@launch
            if (!frozen) {
                _pendingFreezeTargets.value = _pendingFreezeTargets.value + target
            }
            val result = if (frozen) freezeUseCase.unfreezeApp(target)
            else freezeUseCase.freezeApp(target)
            result.onSuccess {
                FrozenStateStore.applyCommandResult(target, frozen = !frozen)
                if (!frozen) {
                    _pendingFreezeTargets.value = _pendingFreezeTargets.value - target
                }
                refreshFrozenStates()
                HapticController.vibrate(context, HapticType.FREEZE_LOCK)
                // 冻结/解冻成功提示与智能清理统一使用系统 Toast
                val name = _labels.value[target] ?: target.packageName.value
                toast(if (frozen) "已启用：$name" else "已停用：$name")
            }.onFailure {
                if (!frozen) {
                    _pendingFreezeTargets.value = _pendingFreezeTargets.value - target
                }
                showMessage("操作失败：${it.message}")
            }
        }
    }

    /** 移除应用：先解冻再移出列表（设计文档 §3.4「解冻并移出」） */
    fun removeApp(target: AppTarget) {
        viewModelScope.launch {
            freezeUseCase.unfreezeApp(target)
                .onSuccess {
                    gridRepository.removeTarget(target)
                    refreshFrozenStates()
                    // 移除成功提示（含应用名）
                    val name = _labels.value[target] ?: target.packageName.value
                    showMessage("已移除并解冻：$name")
                }
                .onFailure { showMessage("解冻失败：${it.message}") }
        }
    }

    /** ⚠️ 安全特例：移除并卸载（用户明确选择+二次确认后调用） */
    fun uninstallApp(target: AppTarget) {
        viewModelScope.launch {
            freezeUseCase.uninstallApp(target)
                .onSuccess {
                    gridRepository.removeTarget(target)
                    refreshFrozenStates()
                    showMessage("已卸载并移除：${target.packageName.value}")
                }
                .onFailure { showMessage("卸载失败：${it.message}") }
        }
    }

    /** 智能清理：停用底部栏所有打开应用（除锁定） */
    fun quickClean() {
        if (com.nbljsbdk.snowhide.data.repo.BatchProgress.active) return // 批量进行中防重复
        // 权限可能刚刚被用户撤销，先刷新而不是等待旧引擎状态进入 Binder 超时。
        EngineManager.refresh()
        if (!EngineManager.isEngineReady()) {
            toast("智能清理失败：Shizuku 未运行或未授权")
            return
        }
        viewModelScope.launch {
            freezeUseCase.quickCleanTargets()
                .onSuccess { targets ->
                    refreshFrozenStates()
                    HapticController.vibrate(context, HapticType.BATCH)
                    // 批量结果用系统 Toast（Snackbar 在底部易被忽略）
                    toast("智能清理：已停用 ${targets.size} 个应用")
                }
                .onFailure { toast("智能清理失败：${it.message ?: "Shizuku 未运行或未授权"}") }
        }
    }

    /** 启用全部 */
    fun unfreezeAll() {
        if (com.nbljsbdk.snowhide.data.repo.BatchProgress.active) return // 批量进行中防重复
        viewModelScope.launch {
            freezeUseCase.unfreezeAllTargets()
                .onSuccess {
                    n ->
                    refreshFrozenStates()
                    HapticController.vibrate(context, HapticType.BATCH)
                    toast("已启用 $n 个应用")
                }
                .onFailure { showMessage(it.message ?: "操作失败") }
        }
    }

    /** 停用全部（用户拍板：连锁定的也冻结——与智能清理豁免锁定区分开） */
    fun freezeAll() {
        if (com.nbljsbdk.snowhide.data.repo.BatchProgress.active) return // 批量进行中防重复
        viewModelScope.launch {
            freezeUseCase.freezeAllTargets(onlyFolderId = null, exceptLocked = false)
                .onSuccess {
                    n ->
                    refreshFrozenStates()
                    HapticController.vibrate(context, HapticType.BATCH)
                    toast("已停用 $n 个应用")
                }
                .onFailure { showMessage(it.message ?: "操作失败") }
        }
    }

    /** 成功结果使用统一 Toast；失败结果使用主界面 Snackbar。 */
    private fun toast(text: String) {
        FeedbackController.toast(getApplication(), text)
    }

    /** 目录级批量：停用某文件夹内全部应用（文件夹长按菜单「停用目录」） */
    fun freezeFolder(folder: Folder) {
        viewModelScope.launch {
            freezeUseCase.freezeAllTargets(onlyFolderId = folder.id)
                .onSuccess {
                    n ->
                    refreshFrozenStates()
                    HapticController.vibrate(context, HapticType.BATCH)
                    toast("已停用目录「${folder.name}」（$n 个）")
                }
                .onFailure { showMessage(it.message ?: "操作失败") }
        }
    }

    /** 目录级批量：启用某文件夹内全部应用（文件夹长按菜单「启用目录」） */
    fun unfreezeFolder(folder: Folder) {
        viewModelScope.launch {
            val members = gridRepository.folderApps.value
                .filter { it.folderId == folder.id }
                .mapNotNull { it.appTarget }
            freezeUseCase.unfreezeTargets(members)
                .onSuccess { success ->
                    refreshFrozenStates()
                    HapticController.vibrate(context, HapticType.BATCH)
                    toast("已启用目录「${folder.name}」（$success 个）")
                }
                .onFailure { showMessage("启用目录失败：${it.message}") }
        }
    }

    /** 打开应用（已冻结则临时解冻并启动） */
    fun openApp(target: AppTarget) {
        viewModelScope.launch {
            if (FrozenStateStore.targetStates.value[target] == true) {
                freezeUseCase.unfreezeApp(target)
                    .onSuccess { refreshFrozenStates() }
                    .onFailure { showMessage("临时解冻失败：${it.message}"); return@launch }
            }
            if (target.isPrimaryUser) {
                val intent = context.packageManager.getLaunchIntentForPackage(target.packageName.value)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    showMessage("应用无启动入口")
                }
                return@launch
            }
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            // UserHandle 在当前 SDK 只公开按 uid 获取的工厂；Android 的 uid
            // 用户段固定为 100000，因此取该用户段的起始 uid。
            val user = UserHandle.getUserHandleForUid(target.userId * USER_ID_RANGE)
            val activity = runCatching {
                launcherApps?.getActivityList(target.packageName.value, user)?.firstOrNull()
            }.getOrNull()
            if (launcherApps != null && activity != null) {
                runCatching {
                    launcherApps.startMainActivity(activity.componentName, user, null, null)
                }.onFailure { showMessage("无法从雪藏启动分身：${it.message}") }
            } else {
                showMessage("分身应用无可用启动入口")
            }
        }
    }

    /** 请求系统桌面固定一个宫格目标，不修改目标的冻结状态。 */
    fun createDesktopShortcut(target: AppTarget) {
        viewModelScope.launch {
            if (target !in gridRepository.allAddedTargets()) {
                showMessage("应用已不在雪藏宫格中")
                return@launch
            }
            val appLabel = _labels.value[target]
                ?.takeIf { it.isNotBlank() }
                ?: target.packageName.value
            desktopShortcutCreator.requestPin(target, appLabel)
                .onSuccess {
                    val label = DesktopShortcutSpec.longLabel(target, appLabel)
                    showMessage("已请求将 $label 固定到桌面，请在系统提示中确认")
                }
                .onFailure { showMessage("创建桌面快捷方式失败：${it.message}") }
        }
    }

    private companion object {
        private const val USER_ID_RANGE = 100_000
    }

    // ═══════════════════════════════════════
    // 整理目录 / 菜单开关
    // ═══════════════════════════════════════

    fun setOrganizing(enabled: Boolean) {
        _organizing.value = enabled
    }

    fun toggleMenu() {
        _menuOpen.value = !_menuOpen.value
    }

    fun dismissMenu() {
        _menuOpen.value = false
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 从主屏混排顺序投影文件夹页序列，避免 Composable 直接拼装业务排序。 */
internal fun folderIdsInHomeOrder(items: List<GridItem>): List<Long> =
    items.asSequence()
        .filter { it.type == "folder" }
        .sortedBy { it.sortOrder }
        .mapNotNull { it.folderId }
        .distinct()
        .toList()
