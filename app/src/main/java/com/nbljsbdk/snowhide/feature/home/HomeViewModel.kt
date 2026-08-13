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
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.ui.util.AppIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val iconLoader = AppIconLoader(context)

    /** 应用显示名（宫格/列表展示） */
    data class AppInfo(val pkg: String, val label: String, val icon: android.graphics.drawable.Drawable?)

    // ═══════════════════════════════════════
    // 状态
    // ═══════════════════════════════════════

    /** 全部已安装应用信息（增加应用界面数据源，含系统应用） */
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    /** 冻结状态映射（pkg → 是否冻结） */
    private val _frozenStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val frozenStates: StateFlow<Map<String, Boolean>> = _frozenStates.asStateFlow()

    /** 图标缓存（pkg → 图标） */
    private val _icons = MutableStateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>> = _icons.asStateFlow()

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

    init {
        refreshInstalledApps()
        refreshIcons()
        iconLoader.iconPackPkg = settingsRepository.iconPack.value
        viewModelScope.launch {
            EngineManager.primaryEngine.collect { _engineReady.value = it != null }
        }
    }

    /** 刷新已安装应用列表（P0 不含系统应用，显示隐藏包名默认关） */
    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .sortedByDescending { info ->
                        runCatching {
                            pm.getPackageInfo(info.packageName, 0).firstInstallTime
                        }.getOrDefault(0L)
                    }
                    .map { info ->
                        AppInfo(
                            pkg = info.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                            icon = info.loadIcon(pm),
                        )
                    }
            }
            _installedApps.value = apps
        }
    }

    /** 刷新全部已添加应用的冻结状态（一次批量查询） */
    fun refreshFrozenStates() {
        viewModelScope.launch {
            val pkgs = gridRepository.allAddedPackages()
            if (pkgs.isEmpty()) {
                _frozenStates.value = emptyMap()
                return@launch
            }
            val frozen = engineManager.primaryEngine.value
                ?.listFrozenPackages()?.getOrDefault(emptyList())
                ?: emptyList()
            _frozenStates.value = pkgs.associateWith { it in frozen }
        }
    }

    /** 异步加载图标（图标包优先，系统回退） */
    fun refreshIcons(pkgs: List<String> = gridRepository.allAddedPackages()) {
        viewModelScope.launch {
            pkgs.forEach { pkg ->
                val icon = iconLoader.loadIcon(pkg)
                _icons.value = _icons.value + (pkg to icon)
            }
        }
    }

    /** 用户从设置切换图标包后刷新 */
    fun applyIconPack(pkg: String) {
        iconLoader.iconPackPkg = pkg
        iconLoader.clearCache()
        _icons.value = emptyMap()
        refreshIcons()
    }

    // ═══════════════════════════════════════
    // 冻结操作
    // ═══════════════════════════════════════

    /** 切换冻结状态（长按菜单 / 底部栏上划） */
    fun toggleFreeze(pkg: String) {
        viewModelScope.launch {
            val frozen = _frozenStates.value[pkg] ?: false
            val result = if (frozen) freezeUseCase.unfreezeApp(pkg)
            else freezeUseCase.freezeApp(pkg)
            result.onSuccess {
                refreshFrozenStates()
                showMessage(if (frozen) "已解冻" else "已冻结：$pkg")
            }.onFailure { showMessage("操作失败：${it.message}") }
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

    /** 启用应用（仅解冻，不自动打开——与直接点击不同，设计文档 §3.4） */
    fun enableApp(pkg: String) {
        viewModelScope.launch {
            freezeUseCase.unfreezeApp(pkg)
                .onSuccess { refreshFrozenStates(); showMessage("已启用") }
                .onFailure { showMessage("启用失败：${it.message}") }
        }
    }

    /** 快速清理：停用底部栏所有打开应用（除锁定） */
    fun quickClean() {
        viewModelScope.launch {
            freezeUseCase.quickClean()
                .onSuccess { n ->
                    refreshFrozenStates()
                    if (settingsRepository.showToast.value) showMessage("快速清理完成，停用 $n 个应用")
                }
                .onFailure { showMessage("快速清理失败：${it.message}") }
        }
    }

    /** 启用全部 */
    fun unfreezeAll() {
        viewModelScope.launch {
            freezeUseCase.unfreezeAll()
                .onSuccess { n -> refreshFrozenStates(); showMessage("已启用 $n 个应用") }
                .onFailure { showMessage(it.message ?: "操作失败") }
        }
    }

    /** 停用全部（除锁定） */
    fun freezeAll() {
        viewModelScope.launch {
            freezeUseCase.freezeAll(onlyFolderId = null, exceptLocked = true)
                .onSuccess { n -> refreshFrozenStates(); showMessage("已停用 $n 个应用") }
                .onFailure { showMessage(it.message ?: "操作失败") }
        }
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
            if (_frozenStates.value[pkg] == true) {
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
