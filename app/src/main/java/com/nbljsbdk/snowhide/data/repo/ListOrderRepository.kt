package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 左右列表的最近进入顺序。
 *
 * 排序按钮本身不持久化；这里持久化的是应用实际滑入某个列表的顺序，
 * 这样“最近添加”在关闭页面或重启应用后仍然有意义。
 */
object ListOrderRepository {

    private lateinit var prefs: android.content.SharedPreferences
    private var counter = 0L

    private val _appManageAdded = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _appManageRemoved = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _quickToggleAdded = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _quickToggleRemoved = MutableStateFlow<Map<String, Long>>(emptyMap())

    val appManageAdded: StateFlow<Map<String, Long>> = _appManageAdded.asStateFlow()
    val appManageRemoved: StateFlow<Map<String, Long>> = _appManageRemoved.asStateFlow()
    val quickToggleAdded: StateFlow<Map<String, Long>> = _quickToggleAdded.asStateFlow()
    val quickToggleRemoved: StateFlow<Map<String, Long>> = _quickToggleRemoved.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        _appManageAdded.value = load(KEY_APP_MANAGE_ADDED)
        _appManageRemoved.value = load(KEY_APP_MANAGE_REMOVED)
        _quickToggleAdded.value = load(KEY_QUICK_TOGGLE_ADDED)
        _quickToggleRemoved.value = load(KEY_QUICK_TOGGLE_REMOVED)
        counter = maxOf(
            prefs.getLong(KEY_COUNTER, 0L),
            _appManageAdded.value.values.maxOrNull() ?: 0L,
            _appManageRemoved.value.values.maxOrNull() ?: 0L,
            _quickToggleAdded.value.values.maxOrNull() ?: 0L,
            _quickToggleRemoved.value.values.maxOrNull() ?: 0L,
        )
    }

    fun recordAppManageAdded(pkg: String) {
        record(_appManageAdded, pkg)
    }

    fun recordAppManageRemoved(pkg: String) {
        record(_appManageRemoved, pkg)
    }

    fun recordQuickToggleAdded(pkg: String) {
        record(_quickToggleAdded, pkg)
    }

    fun recordQuickToggleRemoved(pkg: String) {
        record(_quickToggleRemoved, pkg)
    }

    /** 为旧版只保存成员数组的数据补一份稳定的加入顺序。 */
    fun seedQuickToggleAdded(packages: List<String>) {
        var current = _quickToggleAdded.value
        var changed = false
        packages.distinct().forEach { pkg ->
            if (pkg !in current) {
                current = current + (pkg to nextOrder())
                changed = true
            }
        }
        if (changed) {
            _quickToggleAdded.value = current
            persist()
        }
    }

    private fun record(flow: MutableStateFlow<Map<String, Long>>, pkg: String) {
        if (pkg.isBlank()) return
        flow.value = flow.value + (pkg to nextOrder())
        persist()
    }

    private fun nextOrder(): Long {
        counter = maxOf(counter + 1L, System.currentTimeMillis())
        return counter
    }

    private fun load(key: String): Map<String, Long> {
        if (!::prefs.isInitialized) return emptyMap()
        val json = prefs.getString(key, "{}") ?: "{}"
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.optLong(it, 0L) }
        }.getOrDefault(emptyMap())
    }

    private fun encode(values: Map<String, Long>): String {
        val obj = JSONObject()
        values.forEach { (pkg, order) -> obj.put(pkg, order) }
        return obj.toString()
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_APP_MANAGE_ADDED, encode(_appManageAdded.value))
            .putString(KEY_APP_MANAGE_REMOVED, encode(_appManageRemoved.value))
            .putString(KEY_QUICK_TOGGLE_ADDED, encode(_quickToggleAdded.value))
            .putString(KEY_QUICK_TOGGLE_REMOVED, encode(_quickToggleRemoved.value))
            .putLong(KEY_COUNTER, counter)
            .apply()
    }

    private const val KEY_COUNTER = "list_order_counter"
    private const val KEY_APP_MANAGE_ADDED = "app_manage_added_order"
    private const val KEY_APP_MANAGE_REMOVED = "app_manage_removed_order"
    private const val KEY_QUICK_TOGGLE_ADDED = "quick_toggle_added_order"
    private const val KEY_QUICK_TOGGLE_REMOVED = "quick_toggle_removed_order"
}
