package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.TargetedPowerEngine
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 冻结状态共享存储。
 *
 * 旧包名 StateFlow 继续保留给 user 0 入口；新的 target StateFlow 才是主屏、
 * 文件夹和分身操作的真实身份来源。未知状态不会被当作未冻结。
 */
object FrozenStateStore {

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val stateLock = Any()
    private val refreshMutex = Mutex()
    private val persistenceChannel = Channel<Map<AppTarget, Boolean>>(Channel.CONFLATED)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mutationVersion = 0L

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        persistenceScope.launch {
            for (states in persistenceChannel) persistCacheNow(states)
        }
        publishPairs(loadCache().mapValues { (_, frozen) ->
            frozen to if (frozen) AppRuntimeState.FROZEN else AppRuntimeState.ACTIVE
        })
    }

    private val _targetStates = MutableStateFlow<Map<AppTarget, Boolean>>(emptyMap())
    /** 明确目标 → 是否冻结；只覆盖已添加目标。 */
    val targetStates: StateFlow<Map<AppTarget, Boolean>> = _targetStates.asStateFlow()

    private val _states = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** user 0 兼容投影：包名 → 是否冻结。 */
    val states: StateFlow<Map<String, Boolean>> = _states.asStateFlow()

    private val _targetAppStates = MutableStateFlow<Map<AppTarget, AppRuntimeState>>(emptyMap())
    /** 明确目标 → 实际运行状态。 */
    val targetAppStates: StateFlow<Map<AppTarget, AppRuntimeState>> = _targetAppStates.asStateFlow()

    private val _appStates = MutableStateFlow<Map<String, AppRuntimeState>>(emptyMap())
    /** user 0 兼容投影：包名 → 实际运行状态。 */
    val appStates: StateFlow<Map<String, AppRuntimeState>> = _appStates.asStateFlow()

    /** 单个命令成功后立即更新内存，后台 refresh 再校正真实状态。 */
    fun applyCommandResult(pkg: String, frozen: Boolean) {
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()
            ?.let { applyCommandResults(mapOf(it to frozen)) }
    }

    fun applyCommandResult(target: AppTarget, frozen: Boolean) {
        applyCommandResults(mapOf(target to frozen))
    }

    /** 批量命令完成后一次性发布状态，避免每个目标都触发一轮重组和持久化。 */
    fun applyCommandResults(results: Map<AppTarget, Boolean>) {
        if (results.isEmpty()) return
        synchronized(stateLock) {
            mutationVersion++
            val states = _targetStates.value + results
            val appStates = _targetAppStates.value +
                results.mapValues { (_, frozen) ->
                    if (frozen) AppRuntimeState.FROZEN else AppRuntimeState.ACTIVE
                }
            publish(states, appStates)
            persistCache(states)
        }
    }

    data class StatusSyncResult(
        val success: Boolean,
        val missingCount: Int,
        val correctedCount: Int,
        val errorMessage: String? = null,
    )

    /** 查询全部已添加目标的真实安装/冻结状态。 */
    suspend fun refresh(): StatusSyncResult = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val refreshVersion = synchronized(stateLock) { mutationVersion }
            val targets = GridRepository.allAddedTargets()
            if (targets.isEmpty()) {
                synchronized(stateLock) {
                    if (mutationVersion == refreshVersion) {
                        publish(emptyMap<AppTarget, Boolean>())
                        persistCache(emptyMap())
                    }
                }
                return@withContext StatusSyncResult(true, 0, 0)
            }
            val engine = EngineManager.primaryEngine.value
                ?: return@withContext publishUnknown(targets, "没有可用的权限引擎")

        val observed = linkedMapOf<AppTarget, Pair<Boolean, AppRuntimeState>>()
        var firstError: Throwable? = null

        val primaryTargets = targets.filter { it.isPrimaryUser }
        val primaryFrozen = engine.listFrozenPackages().getOrElse {
            firstError = it
            emptyList()
        }.toSet()
        primaryTargets.forEach { target ->
            val exists = isInstalled(target.packageName.value)
            val state = when {
                !exists -> AppRuntimeState.MISSING
                firstError != null -> AppRuntimeState.UNKNOWN
                target.packageName.value in primaryFrozen -> AppRuntimeState.FROZEN
                else -> AppRuntimeState.ACTIVE
            }
            observed[target] = stateToPair(target, state)
        }

        val cloneTargets = targets.filterNot { it.isPrimaryUser }
        val targeted = engine as? TargetedPowerEngine
        if (cloneTargets.isNotEmpty() && targeted == null) {
            firstError = firstError ?: IllegalStateException("当前权限引擎不支持用户空间操作")
        }
        cloneTargets.groupBy { it.userId }.forEach { (userId, userTargets) ->
            if (targeted == null) {
                userTargets.forEach { observed[it] = stateToPair(it, AppRuntimeState.UNKNOWN) }
                return@forEach
            }
            val installed = targeted.listInstalledPackages(userId).getOrElse {
                firstError = firstError ?: it
                emptyList()
            }.toSet()
            val frozen = targeted.listFrozenPackages(userId).getOrElse {
                firstError = firstError ?: it
                emptyList()
            }.toSet()
            userTargets.forEach { target ->
                val state = when {
                    target.packageName.value !in installed -> AppRuntimeState.MISSING
                    firstError != null -> AppRuntimeState.UNKNOWN
                    target.packageName.value in frozen -> AppRuntimeState.FROZEN
                    else -> AppRuntimeState.ACTIVE
                }
                observed[target] = stateToPair(target, state)
            }
        }

            val currentTargets = GridRepository.allAddedTargets().toSet()
            val actual = observed
                .filterKeys { it in currentTargets }
                .mapValues { it.value.first }
            val actualStates = observed
                .filterKeys { it in currentTargets }
                .mapValues { it.value.second }
            synchronized(stateLock) {
                if (mutationVersion != refreshVersion) {
                    // 命令在查询期间已改变状态，旧结果不能覆盖命令的即时结果。
                    return@withContext StatusSyncResult(true, 0, 0)
                }
                val previous = _targetStates.value
                publish(actual, actualStates)
                persistCache(actual)
                StatusSyncResult(
                    success = firstError == null,
                    missingCount = actualStates.count { it.value == AppRuntimeState.MISSING },
                    correctedCount = actual.count { (target, frozen) -> previous[target] != null && previous[target] != frozen },
                    errorMessage = firstError?.message,
                )
            }
        }
    }

    private fun publishUnknown(
        targets: List<AppTarget>,
        errorMessage: String,
    ): StatusSyncResult {
        val states = targets.associateWith { target ->
            val previous = _targetStates.value[target] ?: false
            previous to AppRuntimeState.UNKNOWN
        }
        publishPairs(states)
        return StatusSyncResult(false, 0, 0, errorMessage)
    }

    private fun stateToPair(target: AppTarget, state: AppRuntimeState): Pair<Boolean, AppRuntimeState> {
        val frozen = when (state) {
            AppRuntimeState.FROZEN -> true
            AppRuntimeState.ACTIVE, AppRuntimeState.MISSING -> false
            AppRuntimeState.UNKNOWN -> _targetStates.value[target] ?: false
        }
        return frozen to state
    }

    private fun publishPairs(
        states: Map<AppTarget, Pair<Boolean, AppRuntimeState>>,
    ) {
        publish(
            states.mapValues { it.value.first },
            states.mapValues { it.value.second },
        )
    }

    private fun publish(
        states: Map<AppTarget, Boolean>,
        appStates: Map<AppTarget, AppRuntimeState> = states.mapValues { (_, frozen) ->
            if (frozen) AppRuntimeState.FROZEN else AppRuntimeState.ACTIVE
        },
    ) {
        _targetStates.value = states
        _targetAppStates.value = appStates
        _states.value = states
            .filterKeys { it.isPrimaryUser }
            .mapKeys { it.key.packageName.value }
        _appStates.value = appStates
            .filterKeys { it.isPrimaryUser }
            .mapKeys { it.key.packageName.value }
    }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    }.getOrDefault(false)

    private fun persistCache(map: Map<AppTarget, Boolean>) {
        if (!::prefs.isInitialized) return
        persistenceChannel.trySend(map.toMap())
    }

    private fun persistCacheNow(map: Map<AppTarget, Boolean>) {
        val arr = JSONArray()
        map.forEach { (target, frozen) ->
            arr.put(
                JSONObject()
                    .put("p", target.packageName.value)
                    .put("u", target.userId)
                    .put("f", frozen),
            )
        }
        prefs.edit().putString(KEY_CACHE, arr.toString()).apply()
    }

    private fun loadCache(): Map<AppTarget, Boolean> {
        if (!::prefs.isInitialized) return emptyMap()
        val json = prefs.getString(KEY_CACHE, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { arr ->
                buildMap {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val pkg = obj.optString("p")
                        val userId = obj.optInt("u", AppTarget.PRIMARY_USER_ID)
                        AppTarget.create(pkg, userId).getOrNull()?.let {
                            put(it, obj.optBoolean("f", false))
                        }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private const val KEY_CACHE = "frozen_cache"
}
