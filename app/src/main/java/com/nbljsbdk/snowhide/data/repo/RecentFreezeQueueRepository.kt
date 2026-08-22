package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import org.json.JSONArray

/**
 * Recent 划卡后的短期停用队列。
 *
 * 只保存包名，不保存应用文件；用于无障碍服务重连后补执行上一次已确认的划卡动作。
 */
object RecentFreezeQueueRepository {

    private const val PREFS_NAME = "snowhide_settings"
    private const val KEY_QUEUE = "swipe_freeze_queue"
    private const val KEY_SCHEMA = "swipe_freeze_queue_schema"
    private const val CURRENT_SCHEMA = 1

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SCHEMA, 0) != CURRENT_SCHEMA) {
            // 旧版立即停用逻辑可能留下误判队列，不能在修复版启动时继续执行。
            prefs.edit()
                .remove(KEY_QUEUE)
                .putInt(KEY_SCHEMA, CURRENT_SCHEMA)
                .commit()
        }
    }

    @Synchronized
    fun enqueue(packages: Collection<String>) {
        if (!::prefs.isInitialized) return
        val merged = read().toMutableSet().apply { addAll(packages) }
        write(merged)
    }

    @Synchronized
    fun peek(): List<String> = if (::prefs.isInitialized) read() else emptyList()

    @Synchronized
    fun remove(packages: Collection<String>) {
        if (!::prefs.isInitialized) return
        val remaining = read().toMutableSet().apply { removeAll(packages.toSet()) }
        write(remaining)
    }

    @Synchronized
    fun clear() {
        if (::prefs.isInitialized) prefs.edit().remove(KEY_QUEUE).commit()
    }

    private fun read(): List<String> {
        val json = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { array ->
                (0 until array.length())
                    .map { array.getString(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
        }.getOrDefault(emptyList())
    }

    private fun write(packages: Collection<String>) {
        val array = JSONArray()
        packages.filter { it.isNotBlank() }.distinct().forEach(array::put)
        prefs.edit().putString(KEY_QUEUE, array.toString()).commit()
    }
}
