package com.nbljsbdk.snowhide.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置仓库——全部用户设置的读写（SharedPreferences 持久化）
 *
 * 布局设置为全局通用一套（主屏与所有文件夹共用，用户拍板）。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)

    // ═══════════════════════════════════════
    // 简单设置（设计文档 §3.11）
    // ═══════════════════════════════════════

    private val _showToast = MutableStateFlow(prefs.getBoolean(KEY_TOAST, true))
    /** 清理应用后是否展示 Toast（默认启用） */
    val showToast: StateFlow<Boolean> = _showToast.asStateFlow()

    private val _showAppName = MutableStateFlow(prefs.getBoolean(KEY_APP_NAME, true))
    /** 是否显示应用名 */
    val showAppName: StateFlow<Boolean> = _showAppName.asStateFlow()

    private val _backToLastDir = MutableStateFlow(prefs.getBoolean(KEY_BACK_DIR, true))
    /** 退出程序后是否回到当前目录（保存最后退出目录） */
    val backToLastDir: StateFlow<Boolean> = _backToLastDir.asStateFlow()

    fun setShowToast(enabled: Boolean) {
        _showToast.value = enabled
        prefs.edit().putBoolean(KEY_TOAST, enabled).apply()
    }

    fun setShowAppName(enabled: Boolean) {
        _showAppName.value = enabled
        prefs.edit().putBoolean(KEY_APP_NAME, enabled).apply()
    }

    fun setBackToLastDir(enabled: Boolean) {
        _backToLastDir.value = enabled
        prefs.edit().putBoolean(KEY_BACK_DIR, enabled).apply()
    }

    // ═══════════════════════════════════════
    // 布局设置（全局通用一套，设计文档 §3.5）
    // ═══════════════════════════════════════

    private val _columns = MutableStateFlow(prefs.getInt(KEY_COLUMNS, 4))
    /** 每排数量 */
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _iconSize = MutableStateFlow(prefs.getInt(KEY_ICON_SIZE, 56))
    /** 图标大小（dp） */
    val iconSize: StateFlow<Int> = _iconSize.asStateFlow()

    private val _verticalSpace = MutableStateFlow(prefs.getInt(KEY_V_SPACE, 12))
    /** 上下间距（dp） */
    val verticalSpace: StateFlow<Int> = _verticalSpace.asStateFlow()

    private val _dockIconSize = MutableStateFlow(prefs.getInt(KEY_DOCK_SIZE, 40))
    /** 底部图标大小（dp） */
    val dockIconSize: StateFlow<Int> = _dockIconSize.asStateFlow()

    fun setColumns(value: Int) = save(KEY_COLUMNS, value) { _columns.value = it }
    fun setIconSize(value: Int) = save(KEY_ICON_SIZE, value) { _iconSize.value = it }
    fun setVerticalSpace(value: Int) = save(KEY_V_SPACE, value) { _verticalSpace.value = it }
    fun setDockIconSize(value: Int) = save(KEY_DOCK_SIZE, value) { _dockIconSize.value = it }

    // ═══════════════════════════════════════
    // 图标包 / 壁纸（美化菜单）
    // ═══════════════════════════════════════

    private val _iconPack = MutableStateFlow(prefs.getString(KEY_ICON_PACK, "") ?: "")
    /** 当前图标包包名（空=系统默认图标） */
    val iconPack: StateFlow<String> = _iconPack.asStateFlow()

    private val _transparentBg = MutableStateFlow(prefs.getBoolean(KEY_TRANSPARENT, true))
    /** 背景透明（透出壁纸），false=自定义图片背景 */
    val transparentBg: StateFlow<Boolean> = _transparentBg.asStateFlow()

    private val _bgImagePath = MutableStateFlow(prefs.getString(KEY_BG_IMAGE, "") ?: "")
    /** 自定义背景图片路径（transparentBg=false 时生效） */
    val bgImagePath: StateFlow<String> = _bgImagePath.asStateFlow()

    fun setIconPack(pkg: String) {
        _iconPack.value = pkg
        prefs.edit().putString(KEY_ICON_PACK, pkg).apply()
    }

    fun setTransparentBg(enabled: Boolean) {
        _transparentBg.value = enabled
        prefs.edit().putBoolean(KEY_TRANSPARENT, enabled).apply()
    }

    fun setBgImagePath(path: String) {
        _bgImagePath.value = path
        prefs.edit().putString(KEY_BG_IMAGE, path).apply()
    }

    // ═══════════════════════════════════════

    private fun save(key: String, value: Int, apply: (Int) -> Unit) {
        apply(value)
        prefs.edit().putInt(key, value).apply()
    }

    companion object {
        private const val KEY_TOAST = "show_toast"
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
}
