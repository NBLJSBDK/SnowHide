package com.nbljsbdk.snowhide.data.prefs

import android.content.Context
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityFeatureSettings
import com.nbljsbdk.snowhide.core.feedback.HapticType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 设置仓库——全部用户设置的读写（SharedPreferences 持久化）
 *
 * 布局设置为全局通用一套（主屏与所有文件夹共用，用户拍板）。
 * 单例设计：全工程共享同一实例。
 */
object SettingsRepository : AccessibilityFeatureSettings {

    private lateinit var prefs: android.content.SharedPreferences

    /** 当前个人设备确认后的默认设置；应用数据和列表状态不在这里。 */
    private object DefaultSettings {
        const val SHOW_TOAST = true
        const val SHOW_REENTRY_TOAST = false
        const val SWIPE_DISABLE_ENABLED = true
        const val SHOW_APP_NAME = true
        const val SHOW_RETURN_HOME_BUTTON = true
        const val RESET_HOME_ON_REENTRY = true
        const val HAPTIC_LEVEL = 4
        const val HAPTIC_ENABLED = true
        const val ICON_SIZE = 47
        const val VERTICAL_SPACE = 0
        const val DOCK_ICON_SIZE = 47
        const val DOCK_ACTION_ICON_SIZE = 26
        const val FOLDER_PREVIEW = 2
        const val ICON_PACK = "me.morirain.dev.iconpack.pure"
        const val ICON_SHAPE = "circle"
        const val LOCK_CLEAN_ENABLED = true
        const val LOCK_CLEAN_DELAY = 60
        const val LOCK_CLEAN_NOTIFY = true
        const val WALLPAPER_OVERLAY = 0.1f
        const val TRANSPARENT_BACKGROUND = true
        const val ANIMATION_LEVEL = 2 // 中档；旧 animations_enabled=true 也映射到这里
        const val AUTO_SYNC_STATUS = true
        const val COLUMNS = 4
        const val BACKGROUND_IMAGE_PATH = ""
        const val FOLDER_PAGE_LOOP_ENABLED = true
        val FREEZE_STYLE = com.nbljsbdk.snowhide.ui.util.FreezeStyle.NONE.name
    }

    /** 初始化（Application 启动时调用一次） */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        // 关键：prefs 就绪后重读全部设置。
        // 对象初始化时 prefs 尚未就绪，属性拿到的是默认值——
        // 之前版本重启后设置全部回默认（震动档位 bug 即此）。
        _showToast.value = prefs.getBoolean(KEY_TOAST, DefaultSettings.SHOW_TOAST)
        _showReentryToast.value = prefs.getBoolean(KEY_REENTRY_TOAST, DefaultSettings.SHOW_REENTRY_TOAST)
        _swipeDisableEnabled.value = prefs.getBoolean(KEY_SWIPE_DISABLE, DefaultSettings.SWIPE_DISABLE_ENABLED)
        _showAppName.value = prefs.getBoolean(KEY_APP_NAME, DefaultSettings.SHOW_APP_NAME)
        _showReturnHomeButton.value = prefs.getBoolean(KEY_RETURN_HOME_BUTTON, DefaultSettings.SHOW_RETURN_HOME_BUTTON)
        _resetHomeOnReentry.value = prefs.getBoolean(KEY_BACK_DIR, DefaultSettings.RESET_HOME_ON_REENTRY)
        val legacyHapticLevel = prefs.getInt(KEY_HAPTIC, DefaultSettings.HAPTIC_LEVEL).coerceIn(0, 4)
        _hapticLevel.value = legacyHapticLevel
        _hapticEnabled.value = prefs.getBoolean(KEY_HAPTIC_ENABLED, legacyHapticLevel > 0)
        _hapticNavigationLevel.value = prefs.getInt(KEY_HAPTIC_NAVIGATION, legacyHapticLevel).coerceIn(0, 4)
        _hapticFreezeLockLevel.value = prefs.getInt(KEY_HAPTIC_FREEZE_LOCK, legacyHapticLevel).coerceIn(0, 4)
        _hapticOrganizeListLevel.value = prefs.getInt(KEY_HAPTIC_ORGANIZE_LIST, legacyHapticLevel).coerceIn(0, 4)
        _hapticBatchLevel.value = prefs.getInt(KEY_HAPTIC_BATCH, legacyHapticLevel).coerceIn(0, 4)
        _columns.value = prefs.getInt(KEY_COLUMNS, DefaultSettings.COLUMNS)
        _iconSize.value = prefs.getInt(KEY_ICON_SIZE, DefaultSettings.ICON_SIZE)
        _verticalSpace.value = prefs.getInt(KEY_V_SPACE, DefaultSettings.VERTICAL_SPACE)
        _dockIconSize.value = prefs.getInt(KEY_DOCK_SIZE, DefaultSettings.DOCK_ICON_SIZE)
        _dockActionIconSize.value = prefs.getInt(KEY_DOCK_ACTION_SIZE, DefaultSettings.DOCK_ACTION_ICON_SIZE)
        _folderPreview.value = prefs.getInt(KEY_FOLDER_PREVIEW, DefaultSettings.FOLDER_PREVIEW)
        _iconPack.value = prefs.getString(KEY_ICON_PACK, DefaultSettings.ICON_PACK) ?: DefaultSettings.ICON_PACK
        _freezeStyle.value = prefs.getString(KEY_FREEZE_STYLE, DefaultSettings.FREEZE_STYLE) ?: DefaultSettings.FREEZE_STYLE
        _iconShape.value = prefs.getString(KEY_ICON_SHAPE, DefaultSettings.ICON_SHAPE) ?: DefaultSettings.ICON_SHAPE
        _lockCleanEnabled.value = prefs.getBoolean(KEY_LOCK_CLEAN, DefaultSettings.LOCK_CLEAN_ENABLED)
        _lockCleanDelay.value = prefs.getInt(KEY_LOCK_CLEAN_DELAY, DefaultSettings.LOCK_CLEAN_DELAY)
        _lockCleanNotify.value = prefs.getBoolean(KEY_LOCK_CLEAN_NOTIFY, DefaultSettings.LOCK_CLEAN_NOTIFY)
        _wallpaperOverlay.value = prefs.getFloat(KEY_WALLPAPER_OVERLAY, DefaultSettings.WALLPAPER_OVERLAY)
        _transparentBg.value = prefs.getBoolean(KEY_TRANSPARENT, DefaultSettings.TRANSPARENT_BACKGROUND)
        _animationLevel.value = readAnimationLevel()
        _autoSyncStatus.value = prefs.getBoolean(KEY_AUTO_SYNC_STATUS, DefaultSettings.AUTO_SYNC_STATUS)
        _bgImagePath.value = prefs.getString(KEY_BG_IMAGE, DefaultSettings.BACKGROUND_IMAGE_PATH) ?: DefaultSettings.BACKGROUND_IMAGE_PATH
        _folderPageLoopEnabled.value = prefs.getBoolean(
            KEY_FOLDER_PAGE_LOOP_ENABLED,
            DefaultSettings.FOLDER_PAGE_LOOP_ENABLED,
        )
        _excludedFolderIds.value = decodeLongSet(
            prefs.getString(KEY_EXCLUDED_FOLDER_IDS, "") ?: "",
        )
    }

    private fun getBool(key: String, def: Boolean): Boolean =
        if (::prefs.isInitialized) prefs.getBoolean(key, def) else def

    private fun getInt(key: String, def: Int): Int =
        if (::prefs.isInitialized) prefs.getInt(key, def) else def

    private fun getFloat(key: String, def: Float): Float =
        if (::prefs.isInitialized) prefs.getFloat(key, def) else def

    private fun getStr(key: String, def: String): String =
        if (::prefs.isInitialized) prefs.getString(key, def) ?: def else def

    // ═══════════════════════════════════════
    // 简单设置（设计文档 §3.11）
    // ═══════════════════════════════════════

    private val _showToast = MutableStateFlow(getBool(KEY_TOAST, DefaultSettings.SHOW_TOAST))
    /** 操作完成后是否展示 Toast（默认启用） */
    val showToast: StateFlow<Boolean> = _showToast.asStateFlow()

    private val _showReentryToast = MutableStateFlow(getBool(KEY_REENTRY_TOAST, DefaultSettings.SHOW_REENTRY_TOAST))
    /** 离开应用超过阈值后重进时是否展示提示 Toast（默认关闭） */
    val showReentryToast: StateFlow<Boolean> = _showReentryToast.asStateFlow()

    private val _showAppName = MutableStateFlow(getBool(KEY_APP_NAME, DefaultSettings.SHOW_APP_NAME))
    /** 是否显示图标下方文字（应用、文件夹、返回主屏按钮） */
    val showAppName: StateFlow<Boolean> = _showAppName.asStateFlow()

    private val _showReturnHomeButton = MutableStateFlow(getBool(KEY_RETURN_HOME_BUTTON, DefaultSettings.SHOW_RETURN_HOME_BUTTON))
    /** 文件夹内是否显示返回主屏按钮 */
    val showReturnHomeButton: StateFlow<Boolean> = _showReturnHomeButton.asStateFlow()

    private val _folderPageLoopEnabled = MutableStateFlow(
        getBool(KEY_FOLDER_PAGE_LOOP_ENABLED, DefaultSettings.FOLDER_PAGE_LOOP_ENABLED),
    )
    /** 文件夹页面是否首尾循环滑动 */
    val folderPageLoopEnabled: StateFlow<Boolean> = _folderPageLoopEnabled.asStateFlow()

    private val _excludedFolderIds = MutableStateFlow(
        decodeLongSet(getStr(KEY_EXCLUDED_FOLDER_IDS, "")),
    )
    /** 只从左右滑动页面排除的文件夹 ID */
    val excludedFolderIds: StateFlow<Set<Long>> = _excludedFolderIds.asStateFlow()

    private val _resetHomeOnReentry = MutableStateFlow(getBool(KEY_BACK_DIR, DefaultSettings.RESET_HOME_ON_REENTRY))
    /** 离开应用超过阈值后重新进入是否回到主屏 */
    val resetHomeOnReentry: StateFlow<Boolean> = _resetHomeOnReentry.asStateFlow()

    fun setShowToast(enabled: Boolean) {
        _showToast.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_TOAST, enabled).apply()
    }

    fun setShowReentryToast(enabled: Boolean) {
        _showReentryToast.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_REENTRY_TOAST, enabled).apply()
    }

    // ═══════════════════════════════════════
    // Recent 划卡停用
    // ═══════════════════════════════════════

    private val _swipeDisableEnabled = MutableStateFlow(
        getBool(KEY_SWIPE_DISABLE, DefaultSettings.SWIPE_DISABLE_ENABLED),
    )
    /** 划掉 Recent 卡片后，立即冻结已添加且未锁定应用。 */
    override val swipeDisableEnabled: StateFlow<Boolean> = _swipeDisableEnabled.asStateFlow()

    fun setSwipeDisableEnabled(enabled: Boolean) {
        _swipeDisableEnabled.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_SWIPE_DISABLE, enabled).apply()
    }

    // ═══════════════════════════════════════
    // 震动反馈（总开关 + 四类场景，0-4 档，0=关闭）
    // ═══════════════════════════════════════

    private val _hapticLevel = MutableStateFlow(getInt(KEY_HAPTIC, DefaultSettings.HAPTIC_LEVEL))
    /** 旧版全局档位，仅用于兼容旧设置 */
    val hapticLevel: StateFlow<Int> = _hapticLevel.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(getBool(KEY_HAPTIC_ENABLED, DefaultSettings.HAPTIC_ENABLED))
    /** 震动总开关 */
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _hapticNavigationLevel = MutableStateFlow(getInt(KEY_HAPTIC_NAVIGATION, getInt(KEY_HAPTIC, DefaultSettings.HAPTIC_LEVEL)))
    /** 导航操作震感档位 */
    val hapticNavigationLevel: StateFlow<Int> = _hapticNavigationLevel.asStateFlow()

    private val _hapticFreezeLockLevel = MutableStateFlow(getInt(KEY_HAPTIC_FREEZE_LOCK, getInt(KEY_HAPTIC, DefaultSettings.HAPTIC_LEVEL)))
    /** 冻结与锁定震感档位 */
    val hapticFreezeLockLevel: StateFlow<Int> = _hapticFreezeLockLevel.asStateFlow()

    private val _hapticOrganizeListLevel = MutableStateFlow(getInt(KEY_HAPTIC_ORGANIZE_LIST, getInt(KEY_HAPTIC, DefaultSettings.HAPTIC_LEVEL)))
    /** 整理与列表操作震感档位 */
    val hapticOrganizeListLevel: StateFlow<Int> = _hapticOrganizeListLevel.asStateFlow()

    private val _hapticBatchLevel = MutableStateFlow(getInt(KEY_HAPTIC_BATCH, getInt(KEY_HAPTIC, DefaultSettings.HAPTIC_LEVEL)))
    /** 批量操作震感档位 */
    val hapticBatchLevel: StateFlow<Int> = _hapticBatchLevel.asStateFlow()

    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    /** 兼容旧版调用：只更新旧全局键，新代码使用按场景设置。 */
    fun setHapticLevel(value: Int) = save(KEY_HAPTIC, value.coerceIn(0, 4)) { _hapticLevel.value = it }

    fun setHapticLevel(type: HapticType, value: Int) {
        val level = value.coerceIn(0, 4)
        when (type) {
            HapticType.NAVIGATION -> _hapticNavigationLevel.value = level
            HapticType.FREEZE_LOCK -> _hapticFreezeLockLevel.value = level
            HapticType.ORGANIZE_LIST -> _hapticOrganizeListLevel.value = level
            HapticType.BATCH -> _hapticBatchLevel.value = level
        }
        if (::prefs.isInitialized) prefs.edit().putInt(hapticKey(type), level).apply()
    }

    fun hapticLevel(type: HapticType): Int = when (type) {
        HapticType.NAVIGATION -> _hapticNavigationLevel.value
        HapticType.FREEZE_LOCK -> _hapticFreezeLockLevel.value
        HapticType.ORGANIZE_LIST -> _hapticOrganizeListLevel.value
        HapticType.BATCH -> _hapticBatchLevel.value
    }

    private fun hapticKey(type: HapticType): String = when (type) {
        HapticType.NAVIGATION -> KEY_HAPTIC_NAVIGATION
        HapticType.FREEZE_LOCK -> KEY_HAPTIC_FREEZE_LOCK
        HapticType.ORGANIZE_LIST -> KEY_HAPTIC_ORGANIZE_LIST
        HapticType.BATCH -> KEY_HAPTIC_BATCH
    }

    // ═══════════════════════════════════════
    // 冻结滤镜样式（美化设置，默认原色）
    // ═══════════════════════════════════════

    private val _freezeStyle = MutableStateFlow(
        getStr(KEY_FREEZE_STYLE, DefaultSettings.FREEZE_STYLE)
    )
    /** 冻结滤镜样式（FreezeStyle 枚举名，默认原色） */
    val freezeStyle: StateFlow<String> = _freezeStyle.asStateFlow()

    fun setFreezeStyle(style: String) {
        _freezeStyle.value = style
        if (::prefs.isInitialized) prefs.edit().putString(KEY_FREEZE_STYLE, style).apply()
    }

    // ═══════════════════════════════════════
    // 图标形状（美化设置：未收录图标包的应用也裁成圆形）
    // ═══════════════════════════════════════

    private val _iconShape = MutableStateFlow(getStr(KEY_ICON_SHAPE, DefaultSettings.ICON_SHAPE))
    /** 图标形状："round"=圆角方形/"circle"=圆形（默认） */
    val iconShape: StateFlow<String> = _iconShape.asStateFlow()

    fun setIconShape(shape: String) {
        _iconShape.value = shape
        if (::prefs.isInitialized) prefs.edit().putString(KEY_ICON_SHAPE, shape).apply()
    }

    // ═══════════════════════════════════════
    // 锁屏自动清理（设置页卡片，用户拍板语义）
    // ═══════════════════════════════════════

    private val _lockCleanEnabled = MutableStateFlow(getBool(KEY_LOCK_CLEAN, DefaultSettings.LOCK_CLEAN_ENABLED))
    /** 锁屏后自动清理开关 */
    override val lockCleanEnabled: StateFlow<Boolean> = _lockCleanEnabled.asStateFlow()

    private val _lockCleanDelay = MutableStateFlow(getInt(KEY_LOCK_CLEAN_DELAY, DefaultSettings.LOCK_CLEAN_DELAY))
    /** 息屏后延迟分钟（0=立即，10 分钟一档 0..120） */
    val lockCleanDelay: StateFlow<Int> = _lockCleanDelay.asStateFlow()

    private val _lockCleanNotify = MutableStateFlow(getBool(KEY_LOCK_CLEAN_NOTIFY, DefaultSettings.LOCK_CLEAN_NOTIFY))
    /** 清理完成通知开关 */
    val lockCleanNotify: StateFlow<Boolean> = _lockCleanNotify.asStateFlow()

    fun setLockCleanEnabled(enabled: Boolean) {
        _lockCleanEnabled.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_LOCK_CLEAN, enabled).apply()
    }

    fun setLockCleanDelay(minutes: Int) = save(KEY_LOCK_CLEAN_DELAY, minutes) { _lockCleanDelay.value = it }

    fun setLockCleanNotify(enabled: Boolean) {
        _lockCleanNotify.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_LOCK_CLEAN_NOTIFY, enabled).apply()
    }

    // ═══════════════════════════════════════
    // 壁纸遮罩浓度（透明背景：0=不遮 1=全遮，0.05 步进，默认 0.1）
    // ═══════════════════════════════════════

    private val _wallpaperOverlay = MutableStateFlow(getFloat(KEY_WALLPAPER_OVERLAY, DefaultSettings.WALLPAPER_OVERLAY))
    /** 壁纸遮罩透明度（0f..1f，默认 0.1） */
    val wallpaperOverlay: StateFlow<Float> = _wallpaperOverlay.asStateFlow()

    fun setWallpaperOverlay(alpha: Float) {
        _wallpaperOverlay.value = alpha
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_WALLPAPER_OVERLAY, alpha).apply()
    }

    fun setShowAppName(enabled: Boolean) {
        _showAppName.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_APP_NAME, enabled).apply()
    }

    fun setShowReturnHomeButton(enabled: Boolean) {
        _showReturnHomeButton.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_RETURN_HOME_BUTTON, enabled).apply()
    }

    fun setFolderPageLoopEnabled(enabled: Boolean) {
        _folderPageLoopEnabled.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_FOLDER_PAGE_LOOP_ENABLED, enabled).apply()
    }

    fun setExcludedFolderIds(ids: Set<Long>) {
        val normalized = ids.filter { it > 0L }.toSet()
        _excludedFolderIds.value = normalized
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_EXCLUDED_FOLDER_IDS, encodeLongSet(normalized))
                .apply()
        }
    }

    fun setResetHomeOnReentry(enabled: Boolean) {
        _resetHomeOnReentry.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_BACK_DIR, enabled).apply()
    }

    // ═══════════════════════════════════════
    // 布局设置（全局通用一套，设计文档 §3.5）
    // ═══════════════════════════════════════

    private val _columns = MutableStateFlow(getInt(KEY_COLUMNS, DefaultSettings.COLUMNS))
    /** 每排数量 */
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _iconSize = MutableStateFlow(getInt(KEY_ICON_SIZE, DefaultSettings.ICON_SIZE))
    /** 图标大小（dp） */
    val iconSize: StateFlow<Int> = _iconSize.asStateFlow()

    private val _verticalSpace = MutableStateFlow(getInt(KEY_V_SPACE, DefaultSettings.VERTICAL_SPACE))
    /** 上下间距（dp） */
    val verticalSpace: StateFlow<Int> = _verticalSpace.asStateFlow()

    private val _dockIconSize = MutableStateFlow(getInt(KEY_DOCK_SIZE, DefaultSettings.DOCK_ICON_SIZE))
    /** Dock 槽位大小（dp），应用图标和右侧操作槽共用 */
    val dockIconSize: StateFlow<Int> = _dockIconSize.asStateFlow()

    private val _dockActionIconSize = MutableStateFlow(getInt(KEY_DOCK_ACTION_SIZE, DefaultSettings.DOCK_ACTION_ICON_SIZE))
    /** Dock 右侧操作槽内图标大小（dp） */
    val dockActionIconSize: StateFlow<Int> = _dockActionIconSize.asStateFlow()

    fun setColumns(value: Int) = save(KEY_COLUMNS, value) { _columns.value = it }
    fun setIconSize(value: Int) = save(KEY_ICON_SIZE, value) { _iconSize.value = it }
    fun setVerticalSpace(value: Int) = save(KEY_V_SPACE, value) { _verticalSpace.value = it }
    fun setDockIconSize(value: Int) = save(KEY_DOCK_SIZE, value) { _dockIconSize.value = it }
    fun setDockActionIconSize(value: Int) = save(KEY_DOCK_ACTION_SIZE, value) {
        _dockActionIconSize.value = it
    }

    // ═══════════════════════════════════════
    // 文件夹拼贴（2×2 / 3×3，布局设置里选）
    // ═══════════════════════════════════════

    private val _folderPreview = MutableStateFlow(getInt(KEY_FOLDER_PREVIEW, DefaultSettings.FOLDER_PREVIEW))
    /** 文件夹拼贴行列数（2=2×2 四个预览，3=3×3 九个预览） */
    val folderPreview: StateFlow<Int> = _folderPreview.asStateFlow()

    fun setFolderPreview(value: Int) = save(KEY_FOLDER_PREVIEW, value) { _folderPreview.value = it }

    // ═══════════════════════════════════════
    // 图标包 / 壁纸（美化菜单）
    // ═══════════════════════════════════════

    private val _iconPack = MutableStateFlow(getStr(KEY_ICON_PACK, DefaultSettings.ICON_PACK))
    /** 当前图标包包名（空=系统默认图标） */
    val iconPack: StateFlow<String> = _iconPack.asStateFlow()

    private val _transparentBg = MutableStateFlow(getBool(KEY_TRANSPARENT, DefaultSettings.TRANSPARENT_BACKGROUND))
    /** 背景透明（透出壁纸），false=自定义图片背景 */
    val transparentBg: StateFlow<Boolean> = _transparentBg.asStateFlow()

    private val _bgImagePath = MutableStateFlow(getStr(KEY_BG_IMAGE, DefaultSettings.BACKGROUND_IMAGE_PATH))
    /** 自定义背景图片路径（transparentBg=false 时生效） */
    val bgImagePath: StateFlow<String> = _bgImagePath.asStateFlow()

    private val _animationLevel = MutableStateFlow(readAnimationLevel())
    /** 应用显式动画速度档位：0=关，1=高，2=中，3=低。 */
    val animationLevel: StateFlow<Int> = _animationLevel.asStateFlow()

    fun setAnimationLevel(level: Int) {
        val normalized = normalizeAnimationLevel(level)
        _animationLevel.value = normalized
        if (::prefs.isInitialized) {
            prefs.edit()
                .putInt(KEY_ANIMATION_LEVEL, normalized)
                // 保留旧开关字段，便于旧版本继续读取“是否开启动画”。
                .putBoolean(KEY_ANIMATIONS, normalized != ANIMATION_LEVEL_OFF)
                .apply()
        }
    }

    private val _autoSyncStatus = MutableStateFlow(getBool(KEY_AUTO_SYNC_STATUS, DefaultSettings.AUTO_SYNC_STATUS))
    /** 是否在应用回到前台时静默同步系统实际状态 */
    val autoSyncStatus: StateFlow<Boolean> = _autoSyncStatus.asStateFlow()

    fun setAutoSyncStatus(enabled: Boolean) {
        _autoSyncStatus.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_AUTO_SYNC_STATUS, enabled).apply()
    }

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

    private fun readAnimationLevel(): Int {
        val stored = if (::prefs.isInitialized) prefs.all[KEY_ANIMATION_LEVEL] else null
        if (stored is Number) return normalizeAnimationLevel(stored.toInt())
        val legacyEnabled = runCatching {
            getBool(KEY_ANIMATIONS, true)
        }.getOrDefault(true)
        return if (legacyEnabled) DefaultSettings.ANIMATION_LEVEL else ANIMATION_LEVEL_OFF
    }

    private fun normalizeAnimationLevel(value: Int): Int = when (value) {
        ANIMATION_LEVEL_OFF,
        ANIMATION_LEVEL_HIGH,
        ANIMATION_LEVEL_MEDIUM,
        ANIMATION_LEVEL_LOW,
        -> value
        else -> DefaultSettings.ANIMATION_LEVEL
    }

    private fun encodeLongSet(values: Set<Long>): String = JSONArray().apply {
        values.sorted().forEach(::put)
    }.toString()

    private fun decodeLongSet(raw: String): Set<Long> = runCatching {
        val array = JSONArray(raw)
        buildSet {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                if (value is Number) {
                    val id = value.toLong()
                    if (id > 0L) add(id)
                }
            }
        }
    }.getOrDefault(emptySet())

    private const val KEY_TOAST = "show_toast"
    private const val KEY_REENTRY_TOAST = "show_reentry_toast"
    private const val KEY_SWIPE_DISABLE = "swipe_disable_enabled"
    private const val KEY_HAPTIC = "haptic_level"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    private const val KEY_HAPTIC_NAVIGATION = "haptic_navigation_level"
    private const val KEY_HAPTIC_FREEZE_LOCK = "haptic_freeze_lock_level"
    private const val KEY_HAPTIC_ORGANIZE_LIST = "haptic_organize_list_level"
    private const val KEY_HAPTIC_BATCH = "haptic_batch_level"
    private const val KEY_FREEZE_STYLE = "freeze_style"
    private const val KEY_ICON_SHAPE = "icon_shape"
    private const val KEY_LOCK_CLEAN = "lock_clean_enabled"
    private const val KEY_LOCK_CLEAN_DELAY = "lock_clean_delay"
    private const val KEY_LOCK_CLEAN_NOTIFY = "lock_clean_notify"
    private const val KEY_WALLPAPER_OVERLAY = "wallpaper_overlay"
    private const val KEY_APP_NAME = "show_app_name"
    private const val KEY_RETURN_HOME_BUTTON = "show_return_home_button"
    private const val KEY_BACK_DIR = "back_to_last_dir"
    private const val KEY_COLUMNS = "columns"
    private const val KEY_ICON_SIZE = "icon_size"
    private const val KEY_V_SPACE = "vertical_space"
    private const val KEY_DOCK_SIZE = "dock_icon_size"
    private const val KEY_DOCK_ACTION_SIZE = "dock_action_icon_size"
    private const val KEY_FOLDER_PREVIEW = "folder_preview"
    private const val KEY_ICON_PACK = "icon_pack"
    private const val KEY_TRANSPARENT = "transparent_bg"
    private const val KEY_BG_IMAGE = "bg_image_path"
    private const val KEY_ANIMATIONS = "animations_enabled"
    private const val KEY_ANIMATION_LEVEL = "animation_level"
    private const val KEY_AUTO_SYNC_STATUS = "auto_sync_status"
    private const val KEY_FOLDER_PAGE_LOOP_ENABLED = "folder_page_loop_enabled"
    private const val KEY_EXCLUDED_FOLDER_IDS = "excluded_folder_ids"
    private const val ANIMATION_LEVEL_OFF = 0
    private const val ANIMATION_LEVEL_HIGH = 1
    private const val ANIMATION_LEVEL_MEDIUM = 2
    private const val ANIMATION_LEVEL_LOW = 3
}
