package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 增删应用界面 ViewModel（设计文档 §3.8 左右分栏，用户拍板终版）
 *
 * - 左栏「未添加应用」：默认安装时间倒序（新装最前），左下排序选项
 * - 右栏「已添加应用」：默认名称正序，右下排序选项
 * - 排序 4 档：时间正序/倒序、名字正序/倒序（不持久化，会话级）
 * - 滑动移动：左栏右滑=加入；右栏左滑=解冻并移出
 * - 系统应用按钮（默认隐藏，关于页版本号 7 次解锁）：
 *   未解锁 → 只显示用户应用；解锁后按钮选中=只显示系统应用，不选=只显示用户应用
 *
 * 列表用 combine 派生 StateFlow 直接驱动 UI（滑动后立即刷新；
 * 触发器方案被 Compose 强跳过优化掉，不可用）。
 */
class AppManageViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    /** 排序方式（左右栏各自独立，不持久化） */
    enum class SortMode {
        TIME_DESC,  // 安装时间倒序（默认左栏）
        TIME_ASC,   // 安装时间正序
        NAME_DESC,  // 名字倒序
        NAME_ASC,   // 名字正序（默认右栏）
    }

    /** 过滤参数聚合（combine 输入合并用） */
    private data class Filter(
        val query: String,
        val systemUnlocked: Boolean,
        val showSystemOnly: Boolean,
        val leftSort: SortMode,
        val rightSort: SortMode,
    )

    /** 暂存改动（本次会话未确认的增删） */
    data class Pending(
        val added: Set<String> = emptySet(),
        val removed: Set<String> = emptySet(),
    )

    private val _pending = MutableStateFlow(Pending())

    val pendingAdded: StateFlow<Set<String>> =
        _pending.map { it.added }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val pendingRemoved: StateFlow<Set<String>> =
        _pending.map { it.removed }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 是否有未确认的改动（取消二次确认用） */
    val hasPendingChanges: StateFlow<Boolean> =
        _pending.map { it.added.isNotEmpty() || it.removed.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _showPackageName = MutableStateFlow(false)
    /** 显示隐藏包名（按钮切换：应用名下方追加一行包名） */
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    private val _filter = MutableStateFlow(
        Filter(
            query = "",
            systemUnlocked = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
                .getBoolean(KEY_SYSTEM_UNLOCKED, false),
            showSystemOnly = false,
            leftSort = SortMode.TIME_DESC,
            rightSort = SortMode.NAME_ASC,
        )
    )

    /** 左栏：未添加应用（combine 派生，含暂存状态；确认前不改动宫格数据） */
    val leftApps: StateFlow<List<AppListRepository.AppInfo>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        _filter,
        _pending,
    ) { apps, items, folderApps, filter, pending ->
        val pendingAdd = pending.added
        val pendingRemove = pending.removed
        val base = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).toSet()
        // 有效已添加 = 现有 - 待移出 + 待加入
        val effective = base - pendingRemove + pendingAdd
        sortApps(
            apps.filter { app ->
                systemOk(app, filter) && queryOk(app, filter.query) && app.pkg !in effective
            },
            filter.leftSort,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 右栏：已添加应用（combine 派生，含暂存状态） */
    val rightApps: StateFlow<List<AppListRepository.AppInfo>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        _filter,
        _pending,
    ) { apps, items, folderApps, filter, pending ->
        val pendingAdd = pending.added
        val pendingRemove = pending.removed
        val base = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).toSet()
        val effective = base - pendingRemove + pendingAdd
        sortApps(
            apps.filter { app ->
                systemOk(app, filter) && queryOk(app, filter.query) && app.pkg in effective
            },
            filter.rightSort,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun systemOk(app: AppListRepository.AppInfo, filter: Filter): Boolean {
        return if (!filter.systemUnlocked) {
            !app.isSystem
        } else if (filter.showSystemOnly) {
            app.isSystem
        } else {
            !app.isSystem
        }
    }

    private fun queryOk(app: AppListRepository.AppInfo, query: String): Boolean =
        query.isEmpty() || app.label.contains(query, ignoreCase = true) ||
            app.pkg.contains(query, ignoreCase = true)

    private fun sortApps(list: List<AppListRepository.AppInfo>, mode: SortMode): List<AppListRepository.AppInfo> =
        when (mode) {
            SortMode.TIME_DESC -> list.sortedByDescending { it.installTime }
            SortMode.TIME_ASC -> list.sortedBy { it.installTime }
            SortMode.NAME_DESC -> list.sortedByDescending { it.label }
            SortMode.NAME_ASC -> list.sortedBy { it.label }
        }

    init {
        // 数据源走全局 AppListRepository（MainActivity 启动时已预加载，
        // 解决首次打开界面空白问题）
    }

    fun setSearchQuery(q: String) {
        _filter.value = _filter.value.copy(query = q)
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    /** 关于页彩蛋：解锁系统应用按钮（持久化） */
    fun unlockSystemApps() {
        if (_filter.value.systemUnlocked) return
        _filter.value = _filter.value.copy(systemUnlocked = true)
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYSTEM_UNLOCKED, true).apply()
    }

    /** 关于页彩蛋：再次 7 次关闭（持久化） */
    fun relockSystemApps() {
        if (!_filter.value.systemUnlocked) return
        _filter.value = _filter.value.copy(systemUnlocked = false, showSystemOnly = false)
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYSTEM_UNLOCKED, false).apply()
    }

    /** 切换系统应用按钮（选中=只显示系统应用，不选=只显示用户应用） */
    fun toggleSystemOnly() {
        _filter.value = _filter.value.copy(showSystemOnly = !_filter.value.showSystemOnly)
    }

    /** 搜索词（TextField 双向绑定用） */
    val searchQuery: StateFlow<String> =
        _filter.map { it.query }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** 系统应用按钮解锁状态（关于页 UI 展示用） */
    val systemUnlocked: StateFlow<Boolean> =
        _filter.map { it.systemUnlocked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, _filter.value.systemUnlocked)

    /** 系统应用按钮选中状态 */
    val showSystemOnly: StateFlow<Boolean> =
        _filter.map { it.showSystemOnly }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 左栏排序状态 */
    val leftSort: StateFlow<SortMode> =
        _filter.map { it.leftSort }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.TIME_DESC)

    /** 右栏排序状态 */
    val rightSort: StateFlow<SortMode> =
        _filter.map { it.rightSort }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.NAME_ASC)

    fun cycleLeftSort() {
        _filter.value = _filter.value.copy(leftSort = _filter.value.leftSort.next())
    }

    fun cycleRightSort() {
        _filter.value = _filter.value.copy(rightSort = _filter.value.rightSort.next())
    }

    private fun SortMode.next(): SortMode = when (this) {
        SortMode.TIME_DESC -> SortMode.TIME_ASC
        SortMode.TIME_ASC -> SortMode.NAME_DESC
        SortMode.NAME_DESC -> SortMode.NAME_ASC
        SortMode.NAME_ASC -> SortMode.TIME_DESC
    }

    /** 右滑加入（暂存，确认后落盘） */
    fun addApp(pkg: String) {
        val cur = _pending.value
        _pending.value = cur.copy(added = cur.added + pkg, removed = cur.removed - pkg)
    }

    /** 左滑移出（暂存，确认后落盘） */
    fun removeApp(pkg: String) {
        val cur = _pending.value
        _pending.value = if (pkg in cur.added) {
            cur.copy(added = cur.added - pkg) // 撤销暂存加入
        } else {
            cur.copy(removed = cur.removed + pkg)
        }
    }

    /** 确认：暂存改动全部落盘（不冻结） */
    fun confirmChanges() {
        val cur = _pending.value
        cur.added.forEach { pkg -> GridRepository.addAppToHome(pkg) }
        cur.removed.forEach { pkg -> GridRepository.removeApp(pkg) }
        _pending.value = Pending()
    }

    /** 取消：丢弃暂存改动 */
    fun cancelChanges() {
        _pending.value = Pending()
    }

    /** 「应用」按钮：加入列表并立即冻结（待加入的全部冻结） */
    fun applyAndFreeze() {
        val freezeUseCase = FreezeUseCase(FreezeExecutor(EngineManager), GridRepository, EngineManager)
        val cur = _pending.value
        val toAdd = cur.added.toList()
        toAdd.forEach { pkg -> GridRepository.addAppToHome(pkg) }
        cur.removed.forEach { pkg -> GridRepository.removeApp(pkg) }
        _pending.value = Pending()
        toAdd.forEach { pkg ->
            viewModelScope.launch { freezeUseCase.freezeApp(pkg) }
        }
    }

    companion object {
        private const val KEY_SYSTEM_UNLOCKED = "system_apps_unlocked"
    }
}
