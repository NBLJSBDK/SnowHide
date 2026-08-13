package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 备份仓库——全部数据的导出/导入（设计文档 §3.12 备份菜单）
 *
 * 用途：debug 版导出 → release 版导入（两包 SP 独立，跨包迁移数据）。
 *
 * 数据源：snowhide_grid（宫格/文件夹/锁定）+ snowhide_settings
 * （全部设置 + 快速启停成员/开启批）。导入后单例 StateFlow 已载入
 * 内存，需重启应用生效（UI 提示）。
 */
object BackupRepository {

    private const val PREFS_GRID = "snowhide_grid"
    private const val PREFS_SETTINGS = "snowhide_settings"
    private const val VERSION = 1

    /** 导出全部数据为 JSON 文本 */
    fun exportBackup(context: Context): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("grid", prefsToJson(context.getSharedPreferences(PREFS_GRID, Context.MODE_PRIVATE)))
        root.put("settings", prefsToJson(context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)))
        return root.toString(2)
    }

    /** 导入 JSON 文本并写回 SP（返回写入的键总数） */
    fun importBackup(context: Context, json: String): Int {
        val root = JSONObject(json)
        val grid = root.getJSONObject("grid")
        val settings = root.getJSONObject("settings")
        var count = 0
        count += jsonToPrefs(
            context.getSharedPreferences(PREFS_GRID, Context.MODE_PRIVATE),
            grid,
        )
        count += jsonToPrefs(
            context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE),
            settings,
        )
        return count
    }

    /** SP 全部键值 → JSONObject */
    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String -> obj.put(key, value)
                is Boolean -> obj.put(key, value)
                is Int -> obj.put(key, value)
                is Long -> obj.put(key, value)
                is Float -> obj.put(key, value.toDouble())
            }
        }
        return obj
    }

    /** JSONObject → SP 写入 */
    private fun jsonToPrefs(prefs: SharedPreferences, obj: JSONObject): Int {
        val editor = prefs.edit()
        var count = 0
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                else -> return@forEach
            }
            count++
        }
        editor.apply()
        return count
    }
}
