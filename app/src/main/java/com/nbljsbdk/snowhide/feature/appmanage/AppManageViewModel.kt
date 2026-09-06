package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.ListOrderRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.appclone.AppCloneSnapshot
import com.nbljsbdk.snowhide.domain.appclone.AppCloneUseCase
import com.nbljsbdk.snowhide.domain.appclone.AppCloneUser
import com.nbljsbdk.snowhide.domain.appmanage.AppManageFilterPolicy
import com.nbljsbdk.snowhide.domain.appmanage.AppManageFreezePlanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 增删应用列表项：本体和分身都使用明确的用户空间目标。 */
data class AppManageItem(
    val target: AppTarget,
    val label: String,
    val isSystem: Boolean,
    val installTime: Long,
    val frozen: Boolean,
) {
    val userId: Int
        get() = target.userId

    val pkg: String
        get() = target.packageName.value
}

/**
 * 增删应用界面 ViewModel。
 *
 * 分身模式只改变当前列表使用的用户空间；左右栏、系统筛选、排序、加入/移出
 * 和“应用”按钮对 user 0 与分身完全相同。目标身份始终贯穿到 Grid 和冻结用例。
 */
class AppManageViewModel(
    application: Application,
    private val freezeUseCase: FreezeUseCase,
    private val appCloneUseCase: AppCloneUseCase,
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val derivedSharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)
    private var initialTargets: Set<AppTarget>? = null
    private var sessionGeneration = 0L
    private var applyJob: Job? = null
    private var applyingGeneration: Long? = null

    class Factory(
        private val application: Application,
        private val freezeUseCase: FreezeUseCase,
        private val appCloneUseCase: AppCloneUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppManageViewModel::class.java)) {
                return AppManageViewModel(application, freezeUseCase, appCloneUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    enum class SortMode {
        TIME_DESC,
        TIME_ASC,
        NAME_DESC,
        NAME_ASC,
        RECENT_DESC,
    }

    private data class Filter(
        val query: String,
        val systemUnlocked: Boolean,
        val showSystemOnly: Boolean,
        val leftSort: SortMode,
        val rightSort: SortMode,
    )

    private data class UserZeroCatalog(
        val apps: List<AppManageItem>,
        val added: Set<AppTarget>,
    )

    private val _showPackageName = MutableStateFlow(false)
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    private val _cloneMode = MutableStateFlow(false)
    val cloneMode: StateFlow<Boolean> = _cloneMode.asStateFlow()

    private val _cloneSnapshot = MutableStateFlow(
        AppCloneSnapshot(users = emptyList(), selectedUserId = null, apps = emptyList()),
    )
    val cloneUsers: StateFlow<List<AppCloneUser>> = _cloneSnapshot
        .map { it.users }
        .stateIn(viewModelScope, derivedSharing, emptyList())
    val selectedCloneUserId: StateFlow<Int?> = _cloneSnapshot
        .map { it.selectedUserId }
        .stateIn(viewModelScope, derivedSharing, null)
    private val _cloneLoading = MutableStateFlow(false)
    private val _cloneError = MutableStateFlow<String?>(null)
    /** 左滑移出期间的乐观 UI 状态；解冻失败时恢复到右栏。 */
    private val _pendingRemovalTargets = MutableStateFlow<Set<AppTarget>>(emptySet())
    private var cloneJob: Job? = null

    val cloneLoading: StateFlow<Boolean> = _cloneLoading.asStateFlow()
    val cloneError: StateFlow<String?> = _cloneError.asStateFlow()

    private val _filter = MutableStateFlow(
        Filter(
            query = "",
            systemUnlocked = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
                .getBoolean(KEY_SYSTEM_UNLOCKED, false),
            showSystemOnly = false,
            leftSort = SortMode.TIME_DESC,
            rightSort = SortMode.NAME_ASC,
        ),
    )

    private val userZeroCatalog: StateFlow<UserZeroCatalog> = combine(
        AppListRepository.installedApps,
        GridRepository.gridItems,
        GridRepository.folderApps,
        FrozenStateStore.targetStates,
    ) { apps, _, _, frozenStates ->
        UserZeroCatalog(
            apps = apps.mapNotNull { app ->
                AppTarget.create(app.pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let { target ->
                    AppManageItem(
                        target,
                        app.label,
                        app.isSystem,
                        app.installTime,
                        frozenStates[target] == true,
                    )
                }
            },
            // 必须包含全部用户空间目标：分身加入 Grid 时，左右栏也要立即重新计算。
            added = GridRepository.allAddedTargets().toSet(),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, derivedSharing, UserZeroCatalog(emptyList(), emptySet()))

    /** 当前用户空间的未添加列表；分身模式下仍然是左栏。 */
    val leftApps: StateFlow<List<AppManageItem>> = combine(
        userZeroCatalog,
        _cloneMode,
        _cloneSnapshot,
        _filter,
        _pendingRemovalTargets,
    ) { userZero, cloneMode, snapshot, filter, pendingRemovals ->
        val all = if (cloneMode) cloneItems(snapshot) else userZero.apps
        val added = (if (cloneMode) {
            userZero.added.filter { it.userId == snapshot.selectedUserId }.toSet()
        } else {
            userZero.added.filter { it.isPrimaryUser }.toSet()
        }) - pendingRemovals
        sortItems(
            all.filter { systemOk(it.isSystem, filter) && queryOk(it, filter.query) && it.target !in added },
            filter.leftSort,
            recentOrder = { ListOrderRepository.appManageRemoved.value[it.key] ?: 0L },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, derivedSharing, emptyList())

    /** 当前用户空间的已添加列表；分身和系统筛选同样影响右栏。 */
    val rightApps: StateFlow<List<AppManageItem>> = combine(
        userZeroCatalog,
        _cloneMode,
        _cloneSnapshot,
        _filter,
        _pendingRemovalTargets,
    ) { userZero, cloneMode, snapshot, filter, pendingRemovals ->
        val all = if (cloneMode) cloneItems(snapshot) else userZero.apps
        val added = (if (cloneMode) {
            userZero.added.filter { it.userId == snapshot.selectedUserId }.toSet()
        } else {
            userZero.added.filter { it.isPrimaryUser }.toSet()
        }) - pendingRemovals
        sortItems(
            all.filter { systemOk(it.isSystem, filter) && queryOk(it, filter.query) && it.target in added },
            filter.rightSort,
            recentOrder = { ListOrderRepository.appManageAdded.value[it.key] ?: 0L },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, derivedSharing, emptyList())

    val searchQuery: StateFlow<String> = _filter
        .map { it.query }
        .stateIn(viewModelScope, derivedSharing, "")
    val systemUnlocked: StateFlow<Boolean> = _filter
        .map { it.systemUnlocked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _filter.value.systemUnlocked)
    val showSystemOnly: StateFlow<Boolean> = _filter
        .map { it.showSystemOnly }
        .stateIn(viewModelScope, derivedSharing, false)
    val leftSort: StateFlow<SortMode> = _filter
        .map { it.leftSort }
        .stateIn(viewModelScope, derivedSharing, SortMode.TIME_DESC)
    val rightSort: StateFlow<SortMode> = _filter
        .map { it.rightSort }
        .stateIn(viewModelScope, derivedSharing, SortMode.NAME_ASC)

    fun setSearchQuery(q: String) {
        _filter.update { it.copy(query = q) }
    }

    fun setCloneMode(enabled: Boolean) {
        if (_cloneMode.value == enabled) return
        _cloneMode.value = enabled
        _filter.update { it.copy(query = "") }
        if (enabled) refreshCloneApps()
    }

    fun refreshCloneApps() {
        loadCloneSnapshot { appCloneUseCase.refresh() }
    }

    fun selectCloneUser(userId: Int) {
        loadCloneSnapshot { appCloneUseCase.selectUser(userId) }
    }

    private fun cloneItems(snapshot: AppCloneSnapshot): List<AppManageItem> {
        val metadata = AppListRepository.installedApps.value.associateBy { it.pkg }
        return snapshot.apps.map { app ->
            val info = metadata[app.packageName]
            AppManageItem(
                target = app.target,
                label = info?.label ?: app.packageName,
                isSystem = app.isSystem,
                installTime = info?.installTime ?: 0L,
                frozen = app.frozen,
            )
        }
    }

    private fun systemOk(isSystem: Boolean, filter: Filter): Boolean =
        AppManageFilterPolicy.allowsSystemApp(
            isSystem = isSystem,
            systemUnlocked = filter.systemUnlocked,
            showSystemOnly = filter.showSystemOnly,
        )

    private fun queryOk(item: AppManageItem, query: String): Boolean =
        query.isEmpty() || item.label.contains(query, ignoreCase = true) ||
            item.pkg.contains(query, ignoreCase = true)

    private fun sortItems(
        list: List<AppManageItem>,
        mode: SortMode,
        recentOrder: (AppTarget) -> Long,
    ): List<AppManageItem> = when (mode) {
        SortMode.TIME_DESC -> list.sortedWith(compareByDescending<AppManageItem> { it.installTime }.thenBy { it.pkg })
        SortMode.TIME_ASC -> list.sortedWith(compareBy<AppManageItem> { it.installTime }.thenBy { it.pkg })
        SortMode.NAME_DESC -> list.sortedWith(compareByDescending<AppManageItem> { it.label }.thenBy { it.target.key })
        SortMode.NAME_ASC -> list.sortedWith(compareBy<AppManageItem> { it.label }.thenBy { it.target.key })
        SortMode.RECENT_DESC -> list.sortedWith(
            compareByDescending<AppManageItem> { recentOrder(it.target) }.thenByDescending { it.target.key },
        )
    }

    private fun loadCloneSnapshot(load: suspend () -> Result<AppCloneSnapshot>) {
        if (cloneJob?.isActive == true) return
        cloneJob = viewModelScope.launch {
            _cloneLoading.value = true
            _cloneError.value = null
            load().fold(
                onSuccess = { _cloneSnapshot.value = it },
                onFailure = { _cloneError.value = it.message ?: "读取用户空间应用失败" },
            )
            _cloneLoading.value = false
        }
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    fun unlockSystemApps() {
        if (_filter.value.systemUnlocked) return
        _filter.update { it.copy(systemUnlocked = true) }
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYSTEM_UNLOCKED, true).apply()
    }

    fun relockSystemApps() {
        if (!_filter.value.systemUnlocked) return
        _filter.update { it.copy(systemUnlocked = false, showSystemOnly = false) }
        context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SYSTEM_UNLOCKED, false).apply()
    }

    fun toggleSystemOnly() {
        _filter.update { it.copy(showSystemOnly = !_filter.value.showSystemOnly) }
    }

    fun cycleLeftSort() {
        _filter.update { it.copy(leftSort = it.leftSort.next()) }
    }

    fun cycleRightSort() {
        _filter.update { it.copy(rightSort = it.rightSort.next()) }
    }

    private fun SortMode.next(): SortMode = when (this) {
        SortMode.TIME_DESC -> SortMode.TIME_ASC
        SortMode.TIME_ASC -> SortMode.NAME_DESC
        SortMode.NAME_DESC -> SortMode.NAME_ASC
        SortMode.NAME_ASC -> SortMode.RECENT_DESC
        SortMode.RECENT_DESC -> SortMode.TIME_DESC
    }

    /** 每次进入增删页建立本体和分身的完整目标基线。 */
    fun beginSession() {
        sessionGeneration++
        initialTargets = GridRepository.allAddedTargets().toSet()
        _cloneMode.value = false
        _filter.update { it.copy(query = "") }
    }

    /** 左栏右滑：本体或分身都写入主屏 Grid。 */
    fun addApp(item: AppManageItem) {
        if (item.target in _pendingRemovalTargets.value) return
        GridRepository.addTargetToHome(item.target)
        FrozenStateStore.applyCommandResult(
            item.target,
            frozen = FrozenStateStore.targetStates.value[item.target] ?: item.frozen,
        )
    }

    /** 右栏左滑：立即移出右栏并显示到左栏，后台解冻；失败时恢复右栏。 */
    fun removeApp(item: AppManageItem) {
        if (item.target in _pendingRemovalTargets.value) return
        _pendingRemovalTargets.update { it + item.target }
        viewModelScope.launch {
            runCatching { freezeUseCase.unfreezeApp(item.target) }
                .getOrElse { Result.failure(it) }
                .onSuccess {
                    GridRepository.removeTarget(item.target)
                    FrozenStateStore.applyCommandResult(item.target, frozen = false)
                    _cloneSnapshot.update { snapshot ->
                        snapshot.copy(
                            apps = snapshot.apps.map { app ->
                                if (app.target == item.target) app.copy(frozen = false) else app
                            },
                        )
                    }
                    FrozenStateStore.refresh()
                    _pendingRemovalTargets.update { it - item.target }
                    _message.value = "已移除并解冻：${item.label}"
                }
                .onFailure {
                    _pendingRemovalTargets.update { it - item.target }
                    _message.value = "解冻失败：${it.message ?: item.pkg}"
                }
        }
    }

    /**
     * “应用”：只冻结本次进入页面后新增、且当前状态明确为 ACTIVE 的目标。
     *
     * baseline 和 generation 必须在启动协程前捕获，避免旧页面的协程读取新会话。
     * 结果通过回调交给 UI；失败时保留页面，由 message/Snackbar 展示原因。
     */
    fun applyAndFreeze(onResult: (Result<Int>) -> Unit = {}) {
        val initial = initialTargets
        val generation = sessionGeneration
        if (applyingGeneration != null || applyJob?.isActive == true) return
        if (initial == null) {
            val result = Result.failure<Int>(IllegalStateException("应用管理会话未开始"))
            _message.value = "冻结失败：${result.exceptionOrNull()?.message ?: "操作失败"}"
            onResult(result)
            return
        }

        applyingGeneration = generation
        _applying.value = true
        applyJob = viewModelScope.launch {
            try {
                if (generation != sessionGeneration) return@launch

                var targets = emptyList<AppTarget>()
                val result = runCatching {
                    targets = AppManageFreezePlanner.newlyAddedUnfrozenTargets(
                        initialTargets = initial,
                        currentTargets = GridRepository.allAddedTargets(),
                        runtimeStates = FrozenStateStore.targetAppStates.value,
                    )
                    freezeUseCase.freezeTargets(targets)
                }.getOrElse { Result.failure(it) }

                // 页面已重新进入时，旧操作的结果不能驱动新会话退出或显示错误。
                if (generation != sessionGeneration) return@launch

                result.onSuccess {
                    FrozenStateStore.applyCommandResults(targets.associateWith { true })
                }
                FrozenStateStore.refresh()
                result.onFailure {
                    _message.value = "冻结失败：${it.message ?: "操作失败"}"
                }
                onResult(result)
            } finally {
                if (applyingGeneration == generation) {
                    applyingGeneration = null
                    _applying.value = false
                }
            }
        }
    }

    fun displayLabel(item: AppManageItem): String = item.label

    companion object {
        private const val KEY_SYSTEM_UNLOCKED = "system_apps_unlocked"
    }
}
