package com.nbljsbdk.snowhide.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置仓库——全部用户设置的读写（SharedPreferences 持久化）
 *
 * 布局设置为全局通用一套（主屏与所有文件夹共用，用户拍板）。
 * 单例设计：全工程共享同一实例。
 */
object SettingsRepository {

    private lateinit var prefs: android.content.SharedPreferences

    /** 初始化（Application 启动时调用一次） */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        // 关键：prefs 就绪后重读全部设置。
        // 对象初始化时 prefs 尚未就绪，属性拿到的是默认值——
        // 之前版本重启后设置全部回默认（震动档位 bug 即此）。
        _showToast.value = prefs.getBoolean(KEY_TOAST, true)
        _showAppName.value = prefs.getBoolean(KEY_APP_NAME, true)
        _backToLastDir.value = prefs.getBoolean(KEY_BACK_DIR, true)
        _hapticLevel.value = prefs.getInt(KEY_HAPTIC, 4)
        _columns.value = prefs.getInt(KEY_COLUMNS, 4)
        _iconSize.value = prefs.getInt(KEY_ICON_SIZE, 56)
        _verticalSpace.value = prefs.getInt(KEY_V_SPACE, 12)
        _dockIconSize.value = prefs.getInt(KEY_DOCK_SIZE, 40)
        _iconPack.value = prefs.getString(KEY_ICON_PACK, "") ?: ""
        _freezeStyle.value = prefs.getString(KEY_FREEZE_STYLE, com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE.name) ?: com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE.name
        _transparentBg.value = prefs.getBoolean(KEY_TRANSPARENT, true)
        _bgImagePath.value = prefs.getString(KEY_BG_IMAGE, "") ?: ""
    }

    private fun getBool(key: String, def: Boolean): Boolean =
        if (::prefs.isInitialized) prefs.getBoolean(key, def) else def

    private fun getInt(key: String, def: Int): Int =
        if (::prefs.isInitialized) prefs.getInt(key, def) else def

    private fun getStr(key: String, def: String): String =
        if (::prefs.isInitialized) prefs.getString(key, def) ?: def else def

    // ═══════════════════════════════════════
    // 简单设置（设计文档 §3.11）
    // ═══════════════════════════════════════

    private val _showToast = MutableStateFlow(getBool(KEY_TOAST, true))
    /** 清理应用后是否展示 Toast（默认启用） */
    val showToast: StateFlow<Boolean> = _showToast.asStateFlow()

    private val _showAppName = MutableStateFlow(getBool(KEY_APP_NAME, true))
    /** 是否显示应用名 */
    val showAppName: StateFlow<Boolean> = _showAppName.asStateFlow()

    private val _backToLastDir = MutableStateFlow(getBool(KEY_BACK_DIR, true))
    /** 退出程序后是否回到当前目录（保存最后退出目录） */
    val backToLastDir: StateFlow<Boolean> = _backToLastDir.asStateFlow()

    fun setShowToast(enabled: Boolean) {
        _showToast.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_TOAST, enabled).apply()
    }

    // ═══════════════════════════════════════
    // 震动反馈（dock 锁定/解锁，0-4 档，0=关闭）
    // ═══════════════════════════════════════

    private val _hapticLevel = MutableStateFlow(getInt(KEY_HAPTIC, 4))
    /** 震感档位 0-4（默认最高档，用户拍板） */
    val hapticLevel: StateFlow<Int> = _hapticLevel.asStateFlow()

    fun setHapticLevel(value: Int) = save(KEY_HAPTIC, value) { _hapticLevel.value = it }

    // ═══════════════════════════════════════
    // 冻结滤镜样式（美化设置，默认变蓝）
    // ═══════════════════════════════════════

    private val _freezeStyle = MutableStateFlow(
        getStr(KEY_FREEZE_STYLE, com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE.name)
    )
    /** 冻结滤镜样式（FreezeStyle 枚举名） */
    val freezeStyle: StateFlow<String> = _freezeStyle.asStateFlow()

    fun setFreezeStyle(style: String) {
        _freezeStyle.value = style
        if (::prefs.isInitialized) prefs.edit().putString(KEY_FREEZE_STYLE, style).apply()
    }

    fun setShowAppName(enabled: Boolean) {
        _showAppName.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_APP_NAME, enabled).apply()
    }

    fun setBackToLastDir(enabled: Boolean) {
        _backToLastDir.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_BACK_DIR, enabled).apply()
    }

    // ═══════════════════════════════════════
    // 布局设置（全局通用一套，设计文档 §3.5）
    // ═══════════════════════════════════════

    private val _columns = MutableStateFlow(getInt(KEY_COLUMNS, 4))
    /** 每排数量 */
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _iconSize = MutableStateFlow(getInt(KEY_ICON_SIZE, 56))
    /** 图标大小（dp） */
    val iconSize: StateFlow<Int> = _iconSize.asStateFlow()

    private val _verticalSpace = MutableStateFlow(getInt(KEY_V_SPACE, 12))
    /** 上下间距（dp） */
    val verticalSpace: StateFlow<Int> = _verticalSpace.asStateFlow()

    private val _dockIconSize = MutableStateFlow(getInt(KEY_DOCK_SIZE, 40))
    /** 底部图标大小（dp） */
    val dockIconSize: StateFlow<Int> = _dockIconSize.asStateFlow()

    fun setColumns(value: Int) = save(KEY_COLUMNS, value) { _columns.value = it }
    fun setIconSize(value: Int) = save(KEY_ICON_SIZE, value) { _iconSize.value = it }
    fun setVerticalSpace(value: Int) = save(KEY_V_SPACE, value) { _verticalSpace.value = it }
    fun setDockIconSize(value: Int) = save(KEY_DOCK_SIZE, value) { _dockIconSize.value = it }

    // ═══════════════════════════════════════
    // 图标包 / 壁纸（美化菜单）
    // ═══════════════════════════════════════

    private val _iconPack = MutableStateFlow(getStr(KEY_ICON_PACK, ""))
    /** 当前图标包包名（空=系统默认图标） */
    val iconPack: StateFlow<String> = _iconPack.asStateFlow()

    private val _transparentBg = MutableStateFlow(getBool(KEY_TRANSPARENT, true))
    /** 背景透明（透出壁纸），false=自定义图片背景 */
    val transparentBg: StateFlow<Boolean> = _transparentBg.asStateFlow()

    private val _bgImagePath = MutableStateFlow(getStr(KEY_BG_IMAGE, ""))
    /** 自定义背景图片路径（transparentBg=false 时生效） */
    val bgImagePath: StateFlow<String> = _bgImagePath.asStateFlow()

    fun setIconPack(pkg: String) {
        _iconPack.value = pkg
        if (::prefs.isInitialized) prefs.edit().putString(KEY_ICON_PACK, pkg).apply()
    }

    fun setTransparentBg(enabled: Boolean) {
        _transparentBg.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_TRANSPARENT, enabled).apply()
    }

    fun setBgImagePath(path: String) {
        _bgImagePath.value = path
        if (::prefs.isInitialized) prefs.edit().putString(KEY_BG_IMAGE, path).apply()
    }

    // ═══════════════════════════════════════

    private fun save(key: String, value: Int, apply: (Int) -> Unit) {
        apply(value)
        if (::prefs.isInitialized) prefs.edit().putInt(key, value).apply()
    }

    private const val KEY_TOAST = "show_toast"
    private const val KEY_HAPTIC = "haptic_level"
    private const val KEY_FREEZE_STYLE = "freeze_style"
    private const val KEY_APP_NAME = "show_app_name"
    private const val KEY_BACK_DIR = "back_to_last_dir"
    private const val KEY_COLUMNS = "columns"
    private const val KEY_ICON_SIZE = "icon_size"
    private const val KEY_V_SPACE = "vertical_space"
    private const val KEY_DOCK_SIZE = "dock_icon_size"
    private const val KEY_ICON_PACK = "icon_pack"
    private const val KEY_TRANSPARENT = "transparent_bg"
    private const val KEY_BG_IMAGE = "bg_image_path"
}
