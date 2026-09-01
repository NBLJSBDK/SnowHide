package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import com.nbljsbdk.snowhide.core.model.AppTarget
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recent 划卡后的短期停用队列。
 *
 * 保存明确的应用目标，不保存应用文件；用于无障碍服务重连后补执行上一次已确认的
 * 划卡动作。旧的包名-only 队列会在版本迁移时清除，避免把分身误映射到 user 0。
 */
object RecentFreezeQueueRepository {

    private const val PREFS_NAME = "snowhide_settings"
    private const val KEY_QUEUE = "swipe_freeze_queue"
    private const val KEY_SCHEMA = "swipe_freeze_queue_schema"
    private const val CURRENT_SCHEMA = 2

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
    fun enqueueTargets(targets: Collection<AppTarget>) {
        if (!::prefs.isInitialized) return
        val merged = read().toMutableSet().apply { addAll(targets) }
        write(merged)
    }

    @Synchronized
    fun peekTargets(): List<AppTarget> = if (::prefs.isInitialized) read() else emptyList()

    @Synchronized
    fun removeTargets(targets: Collection<AppTarget>) {
        if (!::prefs.isInitialized) return
        val remaining = read().toMutableSet().apply { removeAll(targets.toSet()) }
        write(remaining)
    }

    @Synchronized
    fun clear() {
        if (::prefs.isInitialized) prefs.edit().remove(KEY_QUEUE).commit()
    }

    private fun read(): List<AppTarget> {
        val json = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { array ->
                (0 until array.length())
                    .mapNotNull { index ->
                        val value = array.opt(index)
                        if (value !is JSONObject) return@mapNotNull null
                        AppTarget.create(
                            value.optString("pkg"),
                            value.optInt("userId", AppTarget.PRIMARY_USER_ID),
                        ).getOrNull()
                    }
                    .distinct()
            }
        }.getOrDefault(emptyList())
    }

    private fun write(targets: Collection<AppTarget>) {
        val array = JSONArray()
        targets.distinct().forEach { target ->
            array.put(
                JSONObject()
                    .put("pkg", target.packageName.value)
                    .put("userId", target.userId),
            )
        }
        prefs.edit().putString(KEY_QUEUE, array.toString()).commit()
    }
}
