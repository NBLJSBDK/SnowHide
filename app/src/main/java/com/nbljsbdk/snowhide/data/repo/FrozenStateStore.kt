package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 冻结状态共享存储（全局单例）
 *
 * 背景：冻结/解冻操作分散在多个入口（主屏上划、增删界面「应用」按钮、
 * 快速启停磁贴、齿轮批量），各自改完系统状态后主屏的霜化/dock 过滤
 * 必须同步。集中在此：任意入口操作后调 [refresh]，订阅 [states] 的
 * UI 自动更新。
 *
 * 附带持久化缓存：启动瞬间先渲染上次状态（避免「开应用一瞬间
 * 全部无霜化/dock 全显示」），后台刷新后再校正。
 */
object FrozenStateStore {

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    /** 初始化（MainActivity 启动时调用一次，与 GridRepository 同批） */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        // 启动先用缓存渲染（消灭闪烁），随后各入口会再 refresh
        _states.value = loadCache()
        _appStates.value = _states.value.mapValues { (_, frozen) ->
            if (frozen) AppRuntimeState.FROZEN else AppRuntimeState.ACTIVE
        }
    }

    private val _states = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** pkg → 是否冻结（只覆盖已添加应用） */
    val states: StateFlow<Map<String, Boolean>> = _states.asStateFlow()

    private val _appStates = MutableStateFlow<Map<String, AppRuntimeState>>(emptyMap())
    /** pkg → 系统实际状态（主屏、文件夹、Dock 共用） */
    val appStates: StateFlow<Map<String, AppRuntimeState>> = _appStates.asStateFlow()

    /** 命令成功后先更新内存和缓存，后台 refresh 再用系统真实状态校正。 */
    fun applyCommandResult(pkg: String, frozen: Boolean) {
        val states = _states.value + (pkg to frozen)
        _states.value = states
        _appStates.value = _appStates.value +
            (pkg to if (frozen) AppRuntimeState.FROZEN else AppRuntimeState.ACTIVE)
        persistCache(states)
    }

    data class StatusSyncResult(
        val success: Boolean,
        val missingCount: Int,
        val correctedCount: Int,
        val errorMessage: String? = null,
    )

    /** 批量查询全部已添加应用的真实安装/冻结状态并更新缓存 */
    suspend fun refresh(): StatusSyncResult = withContext(Dispatchers.IO) {
        val pkgs = GridRepository.allAddedPackages()
        if (pkgs.isEmpty()) {
            _states.value = emptyMap()
            _appStates.value = emptyMap()
            persistCache(emptyMap())
            return@withContext StatusSyncResult(true, 0, 0)
        }
        val installed = pkgs.associateWith { isInstalled(it) }
        val frozenResult = EngineManager.primaryEngine.value
            ?.listFrozenPackages()
            ?: Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozen = frozenResult.getOrElse { error ->
            val unknown = installed.mapValues { (_, exists) ->
                if (exists) AppRuntimeState.UNKNOWN else AppRuntimeState.MISSING
            }
            _appStates.value = unknown
            return@withContext StatusSyncResult(
                success = false,
                missingCount = unknown.count { it.value == AppRuntimeState.MISSING },
                correctedCount = 0,
                errorMessage = error.message,
            )
        }.toSet()
        val appStates = installed.mapValues { (pkg, exists) ->
            when {
                !exists -> AppRuntimeState.MISSING
                pkg in frozen -> AppRuntimeState.FROZEN
                else -> AppRuntimeState.ACTIVE
            }
        }
        val map = installed.mapValues { (pkg, exists) -> exists && pkg in frozen }
        val previous = _states.value
        val correctedCount = map.count { (pkg, value) -> previous[pkg] != null && previous[pkg] != value }
        _states.value = map
        _appStates.value = appStates
        persistCache(map)
        StatusSyncResult(
            success = true,
            missingCount = appStates.count { it.value == AppRuntimeState.MISSING },
            correctedCount = correctedCount,
        )
    }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    }.getOrDefault(false)

    private fun persistCache(map: Map<String, Boolean>) {
        if (!::prefs.isInitialized) return
        val arr = JSONArray()
        map.forEach { (pkg, frozen) ->
            arr.put(JSONObject().put("p", pkg).put("f", frozen))
        }
        prefs.edit().putString(KEY_CACHE, arr.toString()).apply()
    }

    private fun loadCache(): Map<String, Boolean> {
        if (!::prefs.isInitialized) return emptyMap()
        val json = prefs.getString(KEY_CACHE, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { arr ->
                buildMap {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        put(obj.getString("p"), obj.getBoolean("f"))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private const val KEY_CACHE = "frozen_cache"
}
