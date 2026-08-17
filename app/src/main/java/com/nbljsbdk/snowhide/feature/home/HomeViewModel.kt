package com.nbljsbdk.snowhide.feature.home

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.ui.util.AppIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 主屏幕 ViewModel（P0）
 *
 * 组合引擎状态、宫格数据、冻结状态、图标加载，
 * 是 feature/home 的唯一状态中枢（UI 无逻辑）。
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    val engineManager = EngineManager
    val gridRepository = GridRepository
    val settingsRepository = SettingsRepository
    private val freezeUseCase = FreezeUseCase(FreezeExecutor(engineManager), gridRepository, engineManager)

    // ═══════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════

    /** 冻结状态映射（pkg → 是否冻结，共享存储：磁贴/增删界面操作后同样刷新） */
    val frozenStates: StateFlow<Map<String, Boolean>> =
        com.nbljsbdk.snowhide.data.repo.FrozenStateStore.states

    /** 图标缓存（pkg → 图标） */
    private val _icons = MutableStateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>> = _icons.asStateFlow()

    /**
     * 图标加载完成队列（性能：100+ 应用逐个更新 _icons 会触发
     * 100 次全屏重组导致卡死/ANR——合并成批提交，每批至多隔 100ms）。
     * 必须声明在 init 之前（init 里 startIconFlusher 引用它）。
     */
    private val iconQueue = kotlinx.coroutines.channels.Channel<Pair<String, androidx.compose.ui.graphics.ImageBitmap>>(
        kotlinx.coroutines.channels.Channel.UNLIMITED
    )

    /** 应用显示名映射（pkg → 中文名，来自全局预加载列表） */
    private val _labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val labels: StateFlow<Map<String, String>> = _labels.asStateFlow()

    /** 操作提示（Snackbar 文案） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    init {
        startIconFlusher()
        AppIconLoader.iconPackPkg = settingsRepository.iconPack.value
        // 启动预热图标包 appfilter 解析（否则首图标触发时阻塞 2 秒）
        viewModelScope.launch { AppIconLoader.prewarm() }
        // 订阅宫格数据变化：新加入的应用自动加载图标 + 刷新中文名
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                gridRepository.gridItems,
                gridRepository.folderApps,
                AppListRepository.installedApps,
            ) { items, folderApps, apps ->
                val pkgs = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).distinct()
                pkgs.forEach { pkg ->
                    if (pkg !in _icons.value) {
                        launch { loadIcon(pkg) }
                    }
                }
                // 中文名映射（从预加载的全局应用列表取）
                val labelMap = apps.associate { it.pkg to it.label }
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

    private suspend fun loadIcon(pkg: String) {
        runCatching {
            AppIconLoader.loadIcon(pkg)
        }.onSuccess { icon ->
            iconQueue.send(pkg to icon)
        }
    }

    /** 批量提交协程（在 init 里启动） */
    private fun startIconFlusher() {
        viewModelScope.launch {
            while (true) {
                val first = iconQueue.receive()
                val batch = mutableListOf(first)
                // 100ms 内到达的图标合并成一批，避免逐个重组
                withTimeoutOrNull(100) {
                    while (true) batch.add(iconQueue.receive())
                }
                if (batch.isNotEmpty()) {
                    _icons.value = _icons.value + batch
                }
            }
        }
    }

    /** 手动刷新引擎状态（从 Shizuku 管理器返回后调用） */
    fun refreshEngineStatus() {
        EngineManager.refresh()
    }

    /** 刷新全部已添加应用的冻结状态（一次批量查询，共享存储） */
    fun refreshFrozenStates() {
        viewModelScope.launch {
            com.nbljsbdk.snowhide.data.repo.FrozenStateStore.refresh()
        }
    }

    /** 用户从设置切换图标包后刷新（持久化 + 重载全部图标） */
    fun applyIconPack(pkg: String) {
        settingsRepository.setIconPack(pkg) // 持久化（之前缺失，重启丢失）
        AppIconLoader.iconPackPkg = pkg
        AppIconLoader.clearCache()
        _icons.value = emptyMap()
        android.util.Log.d("SnowHideIcon", "applyIconPack: $pkg (${gridRepository.allAddedPackages().size} apps)")
        // 重新加载全部已添加应用图标
        gridRepository.allAddedPackages().forEach { p ->
            viewModelScope.launch { loadIcon(p) }
        }
    }

    // ═══════════════════════════════════════
    // 冻结操作
    // ═══════════════════════════════════════

    /** 切换冻结状态（长按菜单 / 底部栏上划） */
    fun toggleFreeze(pkg: String) {
        viewModelScope.launch {
            val frozen = com.nbljsbdk.snowhide.data.repo.FrozenStateStore.states.value[pkg] ?: false
            val result = if (frozen) freezeUseCase.unfreezeApp(pkg)
            else freezeUseCase.freezeApp(pkg)
            result.onSuccess {
                refreshFrozenStates()
                // 冻结/解冻成功提示（用户拍板：长按菜单需要反馈）
                val name = _labels.value[pkg] ?: pkg
                showMessage(if (frozen) "已解冻：$name" else "已冻结：$name")
            }.onFailure { showMessage("操作失败：${it.message}") }
        }
    }

    /** 移除应用：先解冻再移出列表（设计文档 §3.4「解冻并移出」） */
    fun removeApp(pkg: String) {
        viewModelScope.launch {
            freezeUseCase.unfreezeApp(pkg)
                .onFailure { showMessage("解冻失败：${it.message}") }
            gridRepository.removeApp(pkg)
            refreshFrozenStates()
            // 移除成功提示（含应用名）
            val name = _labels.value[pkg] ?: pkg
            showMessage("已移除并解冻：$name")
        }
    }

    /** ⚠️ 安全特例：移除并卸载（用户明确选择+二次确认后调用） */
    fun uninstallApp(pkg: String) {
        viewModelScope.launch {
            freezeUseCase.uninstallApp(pkg)
                .onSuccess {
                    gridRepository.removeApp(pkg)
                    refreshFrozenStates()
                    showMessage("已卸载并移除：$pkg")
                }
                .onFailure { showMessage("卸载失败：${it.message}") }
        }
    }

    /** 智能清理：停用底部栏所有打开应用（除锁定） */
    fun quickClean() {
        if (com.nbljsbdk.snowhide.data.repo.BatchProgress.active) return // 批量进行中防重复
        viewModelScope.launch {
            freezeUseCase.quickClean()
                .onSuccess { n ->
                    refreshFrozenStates()
                    // 批量结果用系统 Toast（Snackbar 在底部易被忽略）
                    toast("智能清理：已停用 $n 个应用")
                }
                .onFailure { toast("智能清理失败：${it.message}") }
        }
    }

    /** 启用全部 */
    fun unfreezeAll() {
        if (com.nbljsbdk.snowhide.data.repo.BatchProgress.active) return // 批量进行中防重复
        viewModelScope.launch {
            freezeUseCase.unfreezeAll()
                .onSuccess { n -> refreshFrozenStates(); toast("已启用 $n 个应用") }
                .onFailure { toast(it.message ?: "操作失败") }
        }
    }

    /** 停用全部（用户拍板：连锁定的也冻结——与智能清理豁免锁定区分开） */
    fun freezeAll() {
        if (com.nbljsbdk.snowhide.data.repo.BatchProgress.active) return // 批量进行中防重复
        viewModelScope.launch {
            freezeUseCase.freezeAll(onlyFolderId = null, exceptLocked = false)
                .onSuccess { n -> refreshFrozenStates(); toast("已停用 $n 个应用") }
                .onFailure { toast(it.message ?: "操作失败") }
        }
    }

    /** 系统 Toast（批量结果提示，用户拍板用 Toast 弹窗） */
    private fun toast(text: String) {
        android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 目录级批量：停用某文件夹内全部应用（文件夹长按菜单「停用目录」） */
    fun freezeFolder(folder: Folder) {
        viewModelScope.launch {
            freezeUseCase.freezeAll(onlyFolderId = folder.id)
                .onSuccess { n -> refreshFrozenStates(); showMessage("已停用目录「${folder.name}」（$n 个）") }
                .onFailure { showMessage(it.message ?: "操作失败") }
        }
    }

    /** 目录级批量：启用某文件夹内全部应用（文件夹长按菜单「启用目录」） */
    fun unfreezeFolder(folder: Folder) {
        viewModelScope.launch {
            val members = gridRepository.folderApps.value
                .filter { it.folderId == folder.id }
                .map { it.pkg }
            var success = 0
            members.forEach { pkg ->
                freezeUseCase.unfreezeApp(pkg).onSuccess { success++ }
            }
            refreshFrozenStates()
            showMessage("已启用目录「${folder.name}」（$success 个）")
        }
    }

    /** 打开应用（已冻结则临时解冻并启动） */
    fun openApp(pkg: String) {
        viewModelScope.launch {
            if (com.nbljsbdk.snowhide.data.repo.FrozenStateStore.states.value[pkg] == true) {
                freezeUseCase.unfreezeApp(pkg)
                    .onSuccess { refreshFrozenStates() }
                    .onFailure { showMessage("临时解冻失败：${it.message}"); return@launch }
            }
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                showMessage("应用无启动入口")
            }
        }
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
