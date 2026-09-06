package com.nbljsbdk.snowhide.feature.quicktoggle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.ListOrderRepository
import com.nbljsbdk.snowhide.data.repo.QuickToggleRepository
import com.nbljsbdk.snowhide.core.model.AppTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
 * 成员持久化由 QuickToggleRepository 负责（SharedPreferences + JSON 数组）。
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

    /** 快速启停成员（仓库唯一数据源） */
    val members: StateFlow<List<AppTarget>> = QuickToggleRepository.members

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
    val leftApps: StateFlow<List<AppTarget>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        QuickToggleRepository.members,
        _filter,
    ) { apps, items, folderApps, members, filter ->
        val added = (items.mapNotNull { it.appTarget } + folderApps.mapNotNull { it.appTarget }).toSet()
        val infoByPkg = apps.associateBy { it.pkg }
        sortTargets(
            targets = added.filter { it !in members }
                .filter { target -> queryOk(target, filter.query, infoByPkg) },
            infoByPkg = infoByPkg,
            mode = filter.leftSort,
            recentOrder = { ListOrderRepository.quickToggleRemoved.value[it.key] ?: 0L },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** 右栏：快速启停成员（已添加里移出的自动剔除） */
    val rightApps: StateFlow<List<AppTarget>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        QuickToggleRepository.members,
        _filter,
    ) { apps, items, folderApps, members, filter ->
        val added = (items.mapNotNull { it.appTarget } + folderApps.mapNotNull { it.appTarget }).toSet()
        val infoByPkg = apps.associateBy { it.pkg }
        sortTargets(
            targets = members.filter { it in added }
                .filter { target -> queryOk(target, filter.query, infoByPkg) },
            infoByPkg = infoByPkg,
            mode = filter.rightSort,
            recentOrder = { ListOrderRepository.quickToggleAdded.value[it.key] ?: 0L },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    init {
        ListOrderRepository.seedQuickToggleTargets(QuickToggleRepository.members.value)
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
        val added = GridRepository.allAddedTargets().toSet()
        val current = QuickToggleRepository.members.value
        val cleaned = current.filter { it in added }.distinct()
        if (cleaned.size != current.size) {
            current.filter { it !in cleaned }
                .forEach { ListOrderRepository.recordQuickToggleRemoved(it.key) }
            QuickToggleRepository.replaceTargetMembers(cleaned)
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
        target: AppTarget,
        query: String,
        infoByPkg: Map<String, AppListRepository.AppInfo>,
    ): Boolean {
        if (query.isEmpty()) return true
        return target.packageName.value.contains(query, ignoreCase = true) ||
            infoByPkg[target.packageName.value]?.label?.contains(query, ignoreCase = true) == true ||
            (!target.isPrimaryUser && target.userId.toString().contains(query))
    }

    private fun sortTargets(
        targets: List<AppTarget>,
        infoByPkg: Map<String, AppListRepository.AppInfo>,
        mode: SortMode,
        recentOrder: (AppTarget) -> Long,
    ): List<AppTarget> = when (mode) {
        SortMode.TIME_DESC -> targets.sortedWith(
            compareByDescending<AppTarget> { infoByPkg[it.packageName.value]?.installTime ?: 0L }
                .thenByDescending { it.key },
        )
        SortMode.TIME_ASC -> targets.sortedWith(
            compareBy<AppTarget> { infoByPkg[it.packageName.value]?.installTime ?: 0L }
                .thenBy { it.key },
        )
        SortMode.NAME_DESC -> targets.sortedWith(
            compareByDescending<AppTarget> { infoByPkg[it.packageName.value]?.label ?: it.packageName.value }
                .thenByDescending { it.key },
        )
        SortMode.NAME_ASC -> targets.sortedWith(
            compareBy<AppTarget> { infoByPkg[it.packageName.value]?.label ?: it.packageName.value }
                .thenBy { it.key },
        )
        SortMode.RECENT_DESC -> targets.sortedWith(
            compareByDescending<AppTarget> { recentOrder(it) }
                .thenByDescending { it.key },
        )
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    /** 加入快速启停（左栏右滑） */
    fun addMember(target: AppTarget) {
        if (QuickToggleRepository.addMember(target)) {
            ListOrderRepository.recordQuickToggleAdded(target.key)
        }
    }

    /** 移出快速启停（右栏左滑） */
    fun removeMember(target: AppTarget) {
        if (QuickToggleRepository.removeMember(target)) {
            ListOrderRepository.recordQuickToggleRemoved(target.key)
        }
    }

    /** 应用显示名（包名由 UI 追加显示在名称下方，不再二选一） */
    fun displayLabel(target: AppTarget): String {
        val pkg = target.packageName.value
        val label = AppListRepository.installedApps.value
            .firstOrNull { it.pkg == pkg }
            ?.label
            ?: pkg
        return label.let {
            if (target.isPrimaryUser) it else "$it（用户 ${target.userId}）"
        }
    }

}
