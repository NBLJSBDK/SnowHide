package com.nbljsbdk.snowhide.feature.quicktoggle

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.ListOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 快速启停管理 ViewModel（设计文档 §3.9，用户拍板终版）
 *
 * - 界面仿增删应用的左右分栏
 * - 数据源 = **已添加应用**（不是全部已装应用）
 * - 左栏 = 已添加但未加入快速启停的应用
 * - 右栏 = 快速启停成员
 * - 滑动加入/移出
 * - **数据一致性**：应用从「已添加」被移出（增删界面操作）→ 成员自动同步移出
 * - 排序 5 档与增删应用一致；排序档位不持久化，滑动进入顺序持久化
 *
 * 成员持久化：SharedPreferences（JSON 数组）。
 * 触发（点亮/熄灭）走下拉磁贴，P1 实现。
 */
class QuickToggleViewModel(application: Application) : AndroidViewModel(application) {

    enum class SortMode {
        TIME_DESC,
        TIME_ASC,
        NAME_DESC,
        NAME_ASC,
        RECENT_DESC,
    }

    private data class Filter(
        val query: String,
        val leftSort: SortMode,
        val rightSort: SortMode,
    )

    private val context get() = getApplication<Application>()
    private val prefs = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)

    /** 快速启停成员（包名列表，持久化） */
    private val _members = MutableStateFlow(loadMembers())
    val members: StateFlow<List<String>> = _members.asStateFlow()

    private val _filter = MutableStateFlow(
        Filter(
            query = "",
            leftSort = SortMode.TIME_DESC,
            rightSort = SortMode.NAME_ASC,
        )
    )

    val searchQuery: StateFlow<String> = _filter
        .map { it.query }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _showPackageName = MutableStateFlow(false)
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    /** 左栏：已添加但未加入快速启停（combine 派生，数据变化立即刷新） */
    val leftApps: StateFlow<List<String>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        _members,
        _filter,
    ) { apps, items, folderApps, members, filter ->
        val added = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).toSet()
        val infoByPkg = apps.associateBy { it.pkg }
        sortPackages(
            packages = added.filter { it !in members }
                .filter { pkg -> queryOk(pkg, filter.query, infoByPkg) },
            infoByPkg = infoByPkg,
            mode = filter.leftSort,
            recentOrder = { ListOrderRepository.quickToggleRemoved.value[it] ?: 0L },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 右栏：快速启停成员（已添加里移出的自动剔除） */
    val rightApps: StateFlow<List<String>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        _members,
        _filter,
    ) { apps, items, folderApps, members, filter ->
        val added = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).toSet()
        val infoByPkg = apps.associateBy { it.pkg }
        sortPackages(
            packages = members.filter { it in added }
                .filter { pkg -> queryOk(pkg, filter.query, infoByPkg) },
            infoByPkg = infoByPkg,
            mode = filter.rightSort,
            recentOrder = { ListOrderRepository.quickToggleAdded.value[it] ?: 0L },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        ListOrderRepository.seedQuickToggleAdded(_members.value)
        // 数据一致性：宫格数据变化时同步剔除已移出成员并持久化
        viewModelScope.launch {
            GridRepository.gridItems.collect { _ -> syncMembers() }
        }
        viewModelScope.launch {
            GridRepository.folderApps.collect { _ -> syncMembers() }
        }
    }

    /** 剔除已不在「已添加」里的成员 */
    private fun syncMembers() {
        val added = GridRepository.allAddedPackages().toSet()
        val cleaned = _members.value.filter { it in added }.distinct()
        if (cleaned.size != _members.value.size) {
            _members.value.filter { it !in cleaned }
                .forEach { ListOrderRepository.recordQuickToggleRemoved(it) }
            _members.value = cleaned
            persist()
        }
    }

    fun setSearchQuery(q: String) {
        _filter.value = _filter.value.copy(query = q)
    }

    val leftSort: StateFlow<SortMode> = _filter
        .map { it.leftSort }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.TIME_DESC)

    val rightSort: StateFlow<SortMode> = _filter
        .map { it.rightSort }
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
        SortMode.NAME_ASC -> SortMode.RECENT_DESC
        SortMode.RECENT_DESC -> SortMode.TIME_DESC
    }

    private fun queryOk(
        pkg: String,
        query: String,
        infoByPkg: Map<String, AppListRepository.AppInfo>,
    ): Boolean {
        if (query.isEmpty()) return true
        return pkg.contains(query, ignoreCase = true) ||
            infoByPkg[pkg]?.label?.contains(query, ignoreCase = true) == true
    }

    private fun sortPackages(
        packages: List<String>,
        infoByPkg: Map<String, AppListRepository.AppInfo>,
        mode: SortMode,
        recentOrder: (String) -> Long,
    ): List<String> = when (mode) {
        SortMode.TIME_DESC -> packages.sortedWith(
            compareByDescending<String> { infoByPkg[it]?.installTime ?: 0L }
                .thenByDescending { it },
        )
        SortMode.TIME_ASC -> packages.sortedWith(
            compareBy<String> { infoByPkg[it]?.installTime ?: 0L }
                .thenBy { it },
        )
        SortMode.NAME_DESC -> packages.sortedWith(
            compareByDescending<String> { infoByPkg[it]?.label ?: it }
                .thenByDescending { it },
        )
        SortMode.NAME_ASC -> packages.sortedWith(
            compareBy<String> { infoByPkg[it]?.label ?: it }
                .thenBy { it },
        )
        SortMode.RECENT_DESC -> packages.sortedWith(
            compareByDescending<String> { recentOrder(it) }
                .thenByDescending { it },
        )
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    /** 加入快速启停（左栏右滑） */
    fun addMember(pkg: String) {
        if (pkg !in _members.value) {
            ListOrderRepository.recordQuickToggleAdded(pkg)
            _members.value = _members.value + pkg
            persist()
        }
    }

    /** 移出快速启停（右栏左滑） */
    fun removeMember(pkg: String) {
        if (pkg in _members.value) ListOrderRepository.recordQuickToggleRemoved(pkg)
        _members.value = _members.value.filterNot { it == pkg }
        persist()
    }

    /** 应用显示名（包名由 UI 追加显示在名称下方，不再二选一） */
    fun displayLabel(pkg: String): String {
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    private fun loadMembers(): List<String> {
        val json = prefs.getString(KEY_MEMBERS, "[]") ?: "[]"
        return runCatching {
            org.json.JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.distinct()
            }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        val arr = org.json.JSONArray()
        _members.value.forEach { arr.put(it) }
        prefs.edit().putString(KEY_MEMBERS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_MEMBERS = "quick_toggle_members"
    }
}
