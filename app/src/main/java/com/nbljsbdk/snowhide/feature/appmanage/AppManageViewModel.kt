package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.ListOrderRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.appmanage.AppManageFreezePlanner
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
 * - 排序 5 档：时间正序/倒序、名字正序/倒序、最近添加（排序档位不持久化）
 * - 滑动移动：左栏右滑=加入；右栏左滑=解冻并移出
 * - 系统应用按钮（默认隐藏，关于页版本号 7 次解锁）：
 *   未解锁 → 只显示用户应用；解锁后按钮选中=只显示系统应用，不选=只显示用户应用
 *
 * 列表用 combine 派生 StateFlow 直接驱动 UI（滑动后立即刷新；
 * 触发器方案被 Compose 强跳过优化掉，不可用）。
 */
class AppManageViewModel(
    application: Application,
    private val freezeUseCase: FreezeUseCase,
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private var initialPackages: Set<String>? = null

    /**
     * ViewModel 工厂由组合根持有业务用例，页面只负责传入依赖。
     */
    class Factory(
        private val application: Application,
        private val freezeUseCase: FreezeUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppManageViewModel::class.java)) {
                return AppManageViewModel(application, freezeUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    /** 排序方式（左右栏各自独立，不持久化） */
    enum class SortMode {
        TIME_DESC,  // 安装时间倒序（默认左栏）
        TIME_ASC,   // 安装时间正序
        NAME_DESC,  // 名字倒序
        NAME_ASC,   // 名字正序（默认右栏）
        RECENT_DESC, // 最近进入当前列表倒序
    }

    /** 过滤参数聚合（combine 输入合并用） */
    private data class Filter(
        val query: String,
        val systemUnlocked: Boolean,
        val showSystemOnly: Boolean,
        val leftSort: SortMode,
        val rightSort: SortMode,
    )

    private val _showPackageName = MutableStateFlow(false)
    /** 显示隐藏包名（按钮切换：应用名下方追加一行包名） */
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    /** 一次性提示（移出成功等，UI Snackbar） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

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

    /** 左栏：未添加应用（combine 派生，滑动加入/移出即时生效） */
    val leftApps: StateFlow<List<AppListRepository.AppInfo>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        _filter,
    ) { apps, items, folderApps, filter ->
        val added = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).toSet()
        sortApps(
            apps.filter { app ->
                systemOk(app, filter) && queryOk(app, filter.query) && app.pkg !in added
            },
            filter.leftSort,
            recentOrder = { ListOrderRepository.appManageRemoved.value[it] ?: 0L },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 右栏：已添加应用（combine 派生，滑动加入/移出即时生效） */
    val rightApps: StateFlow<List<AppListRepository.AppInfo>> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        _filter,
    ) { apps, items, folderApps, filter ->
        val added = (items.mapNotNull { it.pkg } + folderApps.map { it.pkg }).toSet()
        sortApps(
            apps.filter { app ->
                systemOk(app, filter) && queryOk(app, filter.query) && app.pkg in added
            },
            filter.rightSort,
            recentOrder = { ListOrderRepository.appManageAdded.value[it] ?: 0L },
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

    private fun sortApps(
        list: List<AppListRepository.AppInfo>,
        mode: SortMode,
        recentOrder: (String) -> Long,
    ): List<AppListRepository.AppInfo> =
        when (mode) {
            SortMode.TIME_DESC -> list.sortedByDescending { it.installTime }
            SortMode.TIME_ASC -> list.sortedBy { it.installTime }
            SortMode.NAME_DESC -> list.sortedByDescending { it.label }
            SortMode.NAME_ASC -> list.sortedBy { it.label }
            // 没有滑动记录的旧数据按包名自然倒序兜底。
            SortMode.RECENT_DESC -> list.sortedWith(
                compareByDescending<AppListRepository.AppInfo> { recentOrder(it.pkg) }
                    .thenByDescending { it.pkg },
            )
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

    /** 每次进入增删页建立基线，避免「应用」误处理之前已存在的应用。 */
    fun beginSession() {
        initialPackages = GridRepository.allAddedPackages().toSet()
    }

    private fun SortMode.next(): SortMode = when (this) {
        SortMode.TIME_DESC -> SortMode.TIME_ASC
        SortMode.TIME_ASC -> SortMode.NAME_DESC
        SortMode.NAME_DESC -> SortMode.NAME_ASC
        SortMode.NAME_ASC -> SortMode.RECENT_DESC
        SortMode.RECENT_DESC -> SortMode.TIME_DESC
    }

    /** 右滑加入（即时落盘，列表立即更新） */
    fun addApp(pkg: String) {
        GridRepository.addAppToHome(pkg)
    }

    /** 左滑移出（即时解冻并移出，列表立即更新，toast 提示） */
    fun removeApp(pkg: String) {
        val name = AppListRepository.installedApps.value
            .firstOrNull { it.pkg == pkg }?.label ?: pkg
        viewModelScope.launch {
            freezeUseCase.unfreezeApp(pkg)
            GridRepository.removeApp(pkg)
            com.nbljsbdk.snowhide.data.repo.FrozenStateStore.refresh()
            _message.value = "已移除并解冻：$name"
        }
    }

    /**
     * 「应用」按钮：只冻结本次进入页面后新增且未冻结的应用。
     * 移出是即时生效的，本按钮不处理已有应用。
     */
    fun applyAndFreeze() {
        viewModelScope.launch {
            val initial = initialPackages ?: return@launch
            val states = com.nbljsbdk.snowhide.data.repo.FrozenStateStore.states.value
            val targets = AppManageFreezePlanner.newlyAddedUnfrozenPackages(
                initialPackages = initial,
                currentPackages = GridRepository.allAddedPackages(),
                frozenStates = states,
            )
            targets.forEach { pkg -> freezeUseCase.freezeApp(pkg) }
            com.nbljsbdk.snowhide.data.repo.FrozenStateStore.refresh()
        }
    }

    companion object {
        private const val KEY_SYSTEM_UNLOCKED = "system_apps_unlocked"
    }
}
