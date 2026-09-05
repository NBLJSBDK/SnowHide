package com.nbljsbdk.snowhide.domain.shortcut

/** 桌面快捷方式记录清理的 Android 适配端口。 */
interface DesktopShortcutMaintenance {
    /** 清除系统保存的全部快捷方式记录。 */
    suspend fun clearAllShortcuts(): Result<Int>
}
