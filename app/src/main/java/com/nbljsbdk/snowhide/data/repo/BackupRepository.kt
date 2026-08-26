package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.nbljsbdk.snowhide.core.model.PackageName
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份仓库——只负责 Backup v1 的 JSON/SP 编解码和原子落盘。
 *
 * 业务入口是 [com.nbljsbdk.snowhide.domain.backup.BackupUseCase]；本类不处理
 * SAF、Toast 或重启。瞬态状态不进入备份：冻结缓存、Recent 队列、锁屏 pending、
 * 快速启停 opened 快照和进行中的批量进度。
 */
object BackupRepository {

    private const val PREFS_GRID = "snowhide_grid"
    private const val PREFS_SETTINGS = "snowhide_settings"
    private const val VERSION = 1

    private lateinit var gridPrefs: SharedPreferences
    private lateinit var settingsPrefs: SharedPreferences

    private enum class ValueType {
        STRING,
        BOOLEAN,
        INT,
        LONG,
        FLOAT,
    }

    private val gridSchema = mapOf(
        "grid_items" to ValueType.STRING,
        "folders" to ValueType.STRING,
        "folder_apps" to ValueType.STRING,
        "locked_packages" to ValueType.STRING,
    )

    private val settingsSchema = mapOf(
        "show_toast" to ValueType.BOOLEAN,
        "show_reentry_toast" to ValueType.BOOLEAN,
        "swipe_disable_enabled" to ValueType.BOOLEAN,
        "haptic_level" to ValueType.INT,
        "haptic_enabled" to ValueType.BOOLEAN,
        "haptic_navigation_level" to ValueType.INT,
        "haptic_freeze_lock_level" to ValueType.INT,
        "haptic_organize_list_level" to ValueType.INT,
        "haptic_batch_level" to ValueType.INT,
        "freeze_style" to ValueType.STRING,
        "icon_shape" to ValueType.STRING,
        "lock_clean_enabled" to ValueType.BOOLEAN,
        "lock_clean_delay" to ValueType.INT,
        "lock_clean_notify" to ValueType.BOOLEAN,
        "wallpaper_overlay" to ValueType.FLOAT,
        "show_app_name" to ValueType.BOOLEAN,
        "show_return_home_button" to ValueType.BOOLEAN,
        "back_to_last_dir" to ValueType.BOOLEAN,
        "columns" to ValueType.INT,
        "icon_size" to ValueType.INT,
        "vertical_space" to ValueType.INT,
        "dock_icon_size" to ValueType.INT,
        "dock_action_icon_size" to ValueType.INT,
        "folder_preview" to ValueType.INT,
        "icon_pack" to ValueType.STRING,
        "transparent_bg" to ValueType.BOOLEAN,
        "bg_image_path" to ValueType.STRING,
        "animations_enabled" to ValueType.BOOLEAN,
        "animation_level" to ValueType.INT,
        "auto_sync_status" to ValueType.BOOLEAN,
        "folder_page_loop_enabled" to ValueType.BOOLEAN,
        "excluded_folder_ids" to ValueType.STRING,
        "app_manage_added_order" to ValueType.STRING,
        "app_manage_removed_order" to ValueType.STRING,
        "quick_toggle_added_order" to ValueType.STRING,
        "quick_toggle_removed_order" to ValueType.STRING,
        "list_order_counter" to ValueType.LONG,
        "quick_toggle_members" to ValueType.STRING,
        "swipe_recent_packages" to ValueType.STRING,
        "swipe_recent_window_package" to ValueType.STRING,
        "swipe_recent_window_class" to ValueType.STRING,
    )

    /** 旧 v1 文件中可能存在，但导入时必须丢弃的瞬态字段。 */
    private val ignoredGridKeys = setOf("frozen_cache")
    private val ignoredSettingsKeys = setOf(
        "quick_toggle_opened",
        "swipe_freeze_queue",
        "swipe_freeze_queue_schema",
        "lock_clean_pending",
    )

    /** 初始化两个 SP 文件（幂等）。 */
    @Synchronized
    fun init(context: Context) {
        if (::gridPrefs.isInitialized) return
        val app = context.applicationContext
        gridPrefs = app.getSharedPreferences(PREFS_GRID, Context.MODE_PRIVATE)
        settingsPrefs = app.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
    }

    /** 导出全部持久化配置为 JSON 文本。 */
    @Synchronized
    fun exportBackup(): String {
        ensureInitialized()
        return JSONObject()
            .put("version", VERSION)
            .put("grid", prefsToJson(gridPrefs, gridSchema))
            .put("settings", prefsToJson(settingsPrefs, settingsSchema))
            .toString(2)
    }

    /** 只导出目录数据（宫格、文件夹、锁定）。 */
    @Synchronized
    fun exportGrid(): String {
        ensureInitialized()
        return JSONObject()
            .put("version", VERSION)
            .put("grid", prefsToJson(gridPrefs, gridSchema))
            .toString(2)
    }

    /** 只导出设置数据。 */
    @Synchronized
    fun exportSettings(): String {
        ensureInitialized()
        return JSONObject()
            .put("version", VERSION)
            .put("settings", prefsToJson(settingsPrefs, settingsSchema))
            .toString(2)
    }

    /**
     * 完整校验后导入 JSON，并用 commit 确认两个 SP 的落盘结果。
     * 两个 SP 不是同一事务，第二个提交失败时尽力回滚第一个，避免留下半份配置。
     */
    @Synchronized
    fun importBackup(json: String): Int {
        ensureInitialized()
        val sections = parseAndValidate(json)
        val oldGrid = snapshot(gridPrefs, sections.grid.keys)
        val oldSettings = snapshot(settingsPrefs, sections.settings.keys)
        return try {
            if (sections.grid.isNotEmpty() && !commitValues(gridPrefs, sections.grid)) {
                error("目录数据落盘失败")
            }
            if (sections.settings.isNotEmpty() && !commitValues(settingsPrefs, sections.settings)) {
                error("设置数据落盘失败")
            }
            sections.grid.size + sections.settings.size
        } catch (error: Throwable) {
            runCatching { restore(gridPrefs, oldGrid) }
            runCatching { restore(settingsPrefs, oldSettings) }
            throw error
        }
    }

    private data class Sections(
        val grid: Map<String, Any>,
        val settings: Map<String, Any>,
    )

    private fun parseAndValidate(json: String): Sections {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("备份不是合法 JSON", it) }
        val rootKeys = keys(root)
        if (rootKeys.any { it !in setOf("version", "grid", "settings") }) {
            throw IllegalArgumentException("备份包含未知顶层字段")
        }
        val version = root.opt("version")
        if (version !is Number || integerValue(version, "version") != VERSION.toLong()) {
            throw IllegalArgumentException("不支持的备份版本")
        }
        if (!root.has("grid") && !root.has("settings")) {
            throw IllegalArgumentException("备份缺少数据 section")
        }
        return Sections(
            grid = if (root.has("grid")) {
                parseSection(root.get("grid"), gridSchema, ignoredGridKeys, "grid")
            } else emptyMap(),
            settings = if (root.has("settings")) {
                parseSection(root.get("settings"), settingsSchema, ignoredSettingsKeys, "settings")
            } else emptyMap(),
        )
    }

    private fun parseSection(
        value: Any?,
        schema: Map<String, ValueType>,
        ignoredKeys: Set<String>,
        section: String,
    ): Map<String, Any> {
        val obj = value as? JSONObject
            ?: throw IllegalArgumentException("$section 必须是对象")
        val parsed = linkedMapOf<String, Any>()
        keys(obj).forEach { key ->
            if (key in ignoredKeys) return@forEach
            val type = schema[key]
                ?: throw IllegalArgumentException("$section 包含未知字段：$key")
            val normalized = normalizeValue(obj.opt(key), type, "$section.$key")
            if (normalized is String) validateStructuredValue(key, normalized)
            parsed[key] = normalized
        }
        return parsed
    }

    private fun prefsToJson(
        prefs: SharedPreferences,
        schema: Map<String, ValueType>,
    ): JSONObject {
        val obj = JSONObject()
        schema.forEach { (key, type) ->
            if (!prefs.contains(key)) return@forEach
            val value = prefs.all[key]
                ?: throw IllegalStateException("备份字段为空：$key")
            val normalized = normalizeStoredValue(value, type, key)
            if (normalized is String) validateStructuredValue(key, normalized)
            obj.put(key, normalized)
        }
        return obj
    }

    private fun normalizeValue(value: Any?, type: ValueType, field: String): Any = when (type) {
        ValueType.STRING -> value as? String
            ?: throw IllegalArgumentException("$field 类型错误")
        ValueType.BOOLEAN -> value as? Boolean
            ?: throw IllegalArgumentException("$field 类型错误")
        ValueType.INT -> integerValue(value, field).let {
            if (it !in Int.MIN_VALUE..Int.MAX_VALUE) {
                throw IllegalArgumentException("$field 超出范围")
            }
            it.toInt()
        }
        ValueType.LONG -> integerValue(value, field)
        ValueType.FLOAT -> {
            val number = value as? Number
                ?: throw IllegalArgumentException("$field 类型错误")
            val double = number.toDouble()
            if (!double.isFinite() || double < -Float.MAX_VALUE || double > Float.MAX_VALUE) {
                throw IllegalArgumentException("$field 超出范围")
            }
            double.toFloat()
        }
    }

    private fun normalizeStoredValue(value: Any, type: ValueType, field: String): Any = when (type) {
        ValueType.STRING -> value as? String
            ?: throw IllegalStateException("备份字段类型错误：$field")
        ValueType.BOOLEAN -> value as? Boolean
            ?: throw IllegalStateException("备份字段类型错误：$field")
        ValueType.INT -> value as? Int
            ?: throw IllegalStateException("备份字段类型错误：$field")
        ValueType.LONG -> value as? Long
            ?: throw IllegalStateException("备份字段类型错误：$field")
        ValueType.FLOAT -> value as? Float
            ?: throw IllegalStateException("备份字段类型错误：$field")
    }

    private fun integerValue(value: Any?, field: String): Long {
        val number = value as? Number
            ?: throw IllegalArgumentException("$field 类型错误")
        return when (number) {
            is Byte, is Short, is Int, is Long -> number.toLong()
            is Float, is Double -> {
                val double = number.toDouble()
                if (!double.isFinite() || double != double.toLong().toDouble()) {
                    throw IllegalArgumentException("$field 必须是整数")
                }
                double.toLong()
            }
            else -> throw IllegalArgumentException("$field 类型错误")
        }
    }

    private fun validateStructuredValue(key: String, raw: String) {
        when (key) {
            "grid_items" -> validateGridItems(raw)
            "folders" -> validateFolders(raw)
            "folder_apps" -> validateFolderApps(raw)
            "locked_packages", "quick_toggle_members", "swipe_recent_packages" ->
                validatePackageArray(raw, key)
            "app_manage_added_order", "app_manage_removed_order",
            "quick_toggle_added_order", "quick_toggle_removed_order" ->
                validateOrderMap(raw, key)
            "excluded_folder_ids" -> validateLongArray(raw, key)
            "icon_pack", "swipe_recent_window_package" -> {
                if (raw.isNotEmpty() && !PackageName.isValid(raw)) {
                    throw IllegalArgumentException("$key 包名非法")
                }
            }
        }
    }

    private fun validateGridItems(raw: String) {
        val array = parseArray(raw, "grid_items")
        val allowed = setOf("id", "type", "pkg", "folderId", "sortOrder", "frozenMode", "locked")
        for (index in 0 until array.length()) {
            val obj = array.opt(index) as? JSONObject
                ?: throw IllegalArgumentException("grid_items[$index] 必须是对象")
            validateKeys(obj, allowed, "grid_items[$index]")
            optionalInteger(obj, "id", "grid_items[$index]")
            optionalInteger(obj, "folderId", "grid_items[$index]", nullable = true)
            optionalInteger(obj, "sortOrder", "grid_items[$index]")
            optionalString(obj, "type", "grid_items[$index]")
            optionalString(obj, "frozenMode", "grid_items[$index]")
            optionalBoolean(obj, "locked", "grid_items[$index]")
            if (obj.has("pkg") && !obj.isNull("pkg")) {
                val pkg = obj.opt("pkg") as? String
                    ?: throw IllegalArgumentException("grid_items[$index].pkg 类型错误")
                requirePackage(pkg, "grid_items[$index].pkg")
            }
        }
    }

    private fun validateFolders(raw: String) {
        val array = parseArray(raw, "folders")
        val allowed = setOf("id", "name", "sortOrder")
        for (index in 0 until array.length()) {
            val obj = array.opt(index) as? JSONObject
                ?: throw IllegalArgumentException("folders[$index] 必须是对象")
            validateKeys(obj, allowed, "folders[$index]")
            requiredInteger(obj, "id", "folders[$index]")
            requiredString(obj, "name", "folders[$index]")
            requiredInteger(obj, "sortOrder", "folders[$index]")
        }
    }

    private fun validateFolderApps(raw: String) {
        val array = parseArray(raw, "folder_apps")
        val allowed = setOf("folderId", "pkg", "sortOrder")
        for (index in 0 until array.length()) {
            val obj = array.opt(index) as? JSONObject
                ?: throw IllegalArgumentException("folder_apps[$index] 必须是对象")
            validateKeys(obj, allowed, "folder_apps[$index]")
            requiredInteger(obj, "folderId", "folder_apps[$index]")
            requirePackage(requiredString(obj, "pkg", "folder_apps[$index]"), "folder_apps[$index].pkg")
            requiredInteger(obj, "sortOrder", "folder_apps[$index]")
        }
    }

    private fun validatePackageArray(raw: String, field: String) {
        val array = parseArray(raw, field)
        for (index in 0 until array.length()) {
            val pkg = array.opt(index) as? String
                ?: throw IllegalArgumentException("$field[$index] 类型错误")
            requirePackage(pkg, "$field[$index]")
        }
    }

    private fun validateLongArray(raw: String, field: String) {
        val array = parseArray(raw, field)
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            val id = value as? Number
                ?: throw IllegalArgumentException("$field[$index] 类型错误")
            val longValue = id.toLong()
            if (longValue <= 0L || id.toDouble() != longValue.toDouble()) {
                throw IllegalArgumentException("$field[$index] 必须是正整数")
            }
        }
    }

    private fun validateOrderMap(raw: String, field: String) {
        val obj = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("$field 不是合法对象", it) }
        keys(obj).forEach { pkg ->
            requirePackage(pkg, "$field.$pkg")
            integerValue(obj.opt(pkg), "$field.$pkg")
        }
    }

    private fun parseArray(raw: String, field: String): JSONArray = runCatching { JSONArray(raw) }
        .getOrElse { throw IllegalArgumentException("$field 不是合法数组", it) }

    private fun validateKeys(obj: JSONObject, allowed: Set<String>, field: String) {
        keys(obj).firstOrNull { it !in allowed }?.let {
            throw IllegalArgumentException("$field 包含未知字段：$it")
        }
    }

    private fun optionalInteger(obj: JSONObject, key: String, field: String, nullable: Boolean = false) {
        if (!obj.has(key) || (nullable && obj.isNull(key))) return
        integerValue(obj.opt(key), "$field.$key")
    }

    private fun optionalString(obj: JSONObject, key: String, field: String) {
        if (obj.has(key) && !obj.isNull(key) && obj.opt(key) !is String) {
            throw IllegalArgumentException("$field.$key 类型错误")
        }
    }

    private fun optionalBoolean(obj: JSONObject, key: String, field: String) {
        if (obj.has(key) && !obj.isNull(key) && obj.opt(key) !is Boolean) {
            throw IllegalArgumentException("$field.$key 类型错误")
        }
    }

    private fun requiredInteger(obj: JSONObject, key: String, field: String) {
        if (!obj.has(key) || obj.isNull(key)) throw IllegalArgumentException("$field 缺少 $key")
        integerValue(obj.opt(key), "$field.$key")
    }

    private fun requiredString(obj: JSONObject, key: String, field: String): String {
        val value = obj.opt(key) as? String
            ?: throw IllegalArgumentException("$field.$key 类型错误")
        return value
    }

    private fun requirePackage(pkg: String, field: String) {
        if (!PackageName.isValid(pkg)) throw IllegalArgumentException("$field 包名非法")
    }

    private data class StoredValue(val present: Boolean, val value: Any?)

    private fun snapshot(prefs: SharedPreferences, keys: Set<String>): Map<String, StoredValue> =
        keys.associateWith { key ->
            StoredValue(prefs.contains(key), prefs.all[key])
        }

    private fun commitValues(prefs: SharedPreferences, values: Map<String, Any>): Boolean {
        val editor = prefs.edit()
        values.forEach { (key, value) -> putValue(editor, key, value) }
        return editor.commit()
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, StoredValue>) {
        val editor = prefs.edit()
        values.forEach { (key, stored) ->
            if (stored.present) {
                putValue(editor, key, stored.value ?: error("备份回滚字段为空：$key"))
            } else {
                editor.remove(key)
            }
        }
        if (!editor.commit()) error("备份回滚失败")
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            else -> error("不支持的备份字段类型：$key")
        }
    }

    private fun keys(obj: JSONObject): List<String> = buildList {
        val iterator = obj.keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    private fun ensureInitialized() {
        check(::gridPrefs.isInitialized && ::settingsPrefs.isInitialized) {
            "BackupRepository 尚未初始化"
        }
    }
}
