package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.nbljsbdk.snowhide.core.engine.EngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** 初始化（MainActivity 启动时调用一次，与 GridRepository 同批） */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        // 启动先用缓存渲染（消灭闪烁），随后各入口会再 refresh
        _states.value = loadCache()
    }

    private val _states = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** pkg → 是否冻结（只覆盖已添加应用） */
    val states: StateFlow<Map<String, Boolean>> = _states.asStateFlow()

    /** 批量查询全部已添加应用的冻结状态并更新缓存 */
    suspend fun refresh() {
        val pkgs = GridRepository.allAddedPackages()
        if (pkgs.isEmpty()) {
            _states.value = emptyMap()
            persistCache(emptyMap())
            return
        }
        val frozen = EngineManager.primaryEngine.value
            ?.listFrozenPackages()?.getOrDefault(emptyList())
            ?: emptyList()
        val map = pkgs.associateWith { it in frozen }
        _states.value = map
        persistCache(map)
    }

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
