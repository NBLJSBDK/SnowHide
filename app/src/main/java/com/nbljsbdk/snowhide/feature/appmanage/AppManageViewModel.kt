package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.repo.GridRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 增删应用界面 ViewModel（设计文档 §3.8 左右分栏，用户拍板终版）
 *
 * - 左栏「未添加应用」：默认安装时间倒序（新装最前），左下排序选项
 * - 右栏「已添加应用」：默认名称正序，右下排序选项
 * - 排序 4 档：时间正序/倒序、名字正序/倒序（不持久化，会话级）
 * - 滑动移动：左栏右滑=加入；右栏左滑=解冻并移出
 * - 系统应用按钮（默认隐藏）：
 *   未解锁 → 左栏只显示用户应用；
 *   解锁后 → 按钮选中=只显示系统应用，不选=只显示用户应用（二选一）
 */
class AppManageViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    data class AppInfo(
        val pkg: String,
        val label: String,
        val isSystem: Boolean,
        val installTime: Long,
    )

    /** 排序方式（左右栏各自独立，不持久化） */
    enum class SortMode {
        TIME_DESC,  // 安装时间倒序（默认左栏）
        TIME_ASC,   // 安装时间正序
        NAME_DESC,  // 名字倒序
        NAME_ASC,   // 名字正序（默认右栏）
    }

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** 全部已安装应用（含系统），原始列表 */
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showPackageName = MutableStateFlow(false)
    /** 显示隐藏包名（按钮切换，替代应用名显示包名） */
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    // ── 系统应用彩蛋（设计文档 §3.8）──
    /** 是否已解锁系统应用按钮（关于页点版本号 7 次，持久化） */
    private val _systemUnlocked = MutableStateFlow(
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .getBoolean(KEY_SYSTEM_UNLOCKED, false)
    )
    val systemUnlocked: StateFlow<Boolean> = _systemUnlocked.asStateFlow()

    /** 解锁后按钮选中状态：true=只显示系统应用，false=只显示用户应用 */
    private val _showSystemOnly = MutableStateFlow(false)
    val showSystemOnly: StateFlow<Boolean> = _showSystemOnly.asStateFlow()

    /** 左栏排序（默认安装时间倒序） */
    private val _leftSort = MutableStateFlow(SortMode.TIME_DESC)
    val leftSort: StateFlow<SortMode> = _leftSort.asStateFlow()

    /** 右栏排序（默认名称正序） */
    private val _rightSort = MutableStateFlow(SortMode.NAME_ASC)
    val rightSort: StateFlow<SortMode> = _rightSort.asStateFlow()

    /**
     * 刷新触发器：GridRepository 数据变化时 +1，UI 订阅它触发重组
     * （增删操作后列表立即刷新的关键——否则派生函数不会被重新调用）
     */
    private val _refresh = MutableStateFlow(0)
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    init {
        refreshInstalledApps()
        viewModelScope.launch {
            GridRepository.gridItems.collect { _refresh.value++ }
        }
        viewModelScope.launch {
            GridRepository.folderApps.collect { _refresh.value++ }
        }
    }

    /** 加载全部已装应用（含系统，含安装时间） */
    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.packageName != context.packageName } // 排除自己
                    .map { info ->
                        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        AppInfo(
                            pkg = info.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                            isSystem = isSystem,
                            installTime = runCatching {
                                pm.getPackageInfo(info.packageName, 0).firstInstallTime
                            }.getOrDefault(0L),
                        )
                    }
            }
            _installedApps.value = apps
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    // ── 系统应用切换 ──

    /** 关于页彩蛋：解锁系统应用按钮（持久化） */
    fun unlockSystemApps() {
        if (_systemUnlocked.value) return
        _systemUnlocked.value = true
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYSTEM_UNLOCKED, true).apply()
    }

    /** 关于页彩蛋：再次 7 次关闭（持久化） */
    fun relockSystemApps() {
        if (!_systemUnlocked.value) return
        _systemUnlocked.value = false
        _showSystemOnly.value = false
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYSTEM_UNLOCKED, false).apply()
    }

    /** 切换系统应用按钮（选中=只显示系统应用，不选=只显示用户应用） */
    fun toggleSystemOnly() {
        _showSystemOnly.value = !_showSystemOnly.value
    }

    // ── 排序 ──

    fun cycleLeftSort() {
        _leftSort.value = _leftSort.value.next()
    }

    fun cycleRightSort() {
        _rightSort.value = _rightSort.value.next()
    }

    private fun SortMode.next(): SortMode = when (this) {
        SortMode.TIME_DESC -> SortMode.TIME_ASC
        SortMode.TIME_ASC -> SortMode.NAME_DESC
        SortMode.NAME_DESC -> SortMode.NAME_ASC
        SortMode.NAME_ASC -> SortMode.TIME_DESC
    }

    // ── 数据派生 ──

    /** 过滤：搜索词 + 系统应用规则 */
    fun filteredApps(): List<AppInfo> {
        val query = _searchQuery.value.trim()
        return _installedApps.value.filter { app ->
            // 系统应用规则：未解锁→只用户应用；解锁→按按钮二选一
            val systemOk = if (!_systemUnlocked.value) {
                !app.isSystem
            } else if (_showSystemOnly.value) {
                app.isSystem
            } else {
                !app.isSystem
            }
            systemOk && (
                query.isEmpty() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.pkg.contains(query, ignoreCase = true)
                )
        }
    }

    /** 左栏：未添加应用（按左栏排序） */
    fun notAddedApps(): List<AppInfo> =
        sortApps(filteredApps().filter { !isAdded(it.pkg) }, _leftSort.value)

    /** 右栏：已添加应用（按右栏排序） */
    fun addedApps(): List<AppInfo> =
        sortApps(filteredApps().filter { isAdded(it.pkg) }, _rightSort.value)

    private fun sortApps(list: List<AppInfo>, mode: SortMode): List<AppInfo> =
        when (mode) {
            SortMode.TIME_DESC -> list.sortedByDescending { it.installTime }
            SortMode.TIME_ASC -> list.sortedBy { it.installTime }
            SortMode.NAME_DESC -> list.sortedByDescending { it.label }
            SortMode.NAME_ASC -> list.sortedBy { it.label }
        }

    // ── 增删操作 ──

    fun isAdded(pkg: String): Boolean = GridRepository.isAppAdded(pkg)

    /** 右滑加入（左栏 → 已添加） */
    fun addApp(pkg: String) {
        GridRepository.addAppToHome(pkg)
    }

    /** 左滑移出（已添加 → 解冻并移出） */
    fun removeApp(pkg: String) {
        GridRepository.removeApp(pkg)
    }

    /** 应用显示名（包名由 UI 按 showPackageName 开关追加显示在名称下方，不再二选一） */
    fun displayLabel(app: AppInfo): String = app.label

    companion object {
        private const val KEY_SYSTEM_UNLOCKED = "system_apps_unlocked"
    }
}
