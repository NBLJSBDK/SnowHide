package com.nbljsbdk.snowhide.domain.shortcut

import com.nbljsbdk.snowhide.core.model.AppTarget

/** 快捷方式的稳定身份和代理 Intent 协议。 */
object DesktopShortcutSpec {
    const val ACTION_OPEN_TARGET = "com.nbljsbdk.snowhide.shortcut.OPEN_TARGET"
    const val EXTRA_PACKAGE_NAME = "com.nbljsbdk.snowhide.shortcut.PACKAGE_NAME"
    const val EXTRA_USER_ID = "com.nbljsbdk.snowhide.shortcut.USER_ID"

    private const val ID_PREFIX = "app_target:"
    private const val MAX_SHORT_LABEL_LENGTH = 25

    /** 包名相同但用户空间不同的目标必须生成不同的桌面快捷方式 ID。 */
    fun shortcutId(target: AppTarget): String = "$ID_PREFIX${target.key}"

    /** 用桌面短标签明确标出分身用户空间，同时遵守系统短标签长度限制。 */
    fun shortLabel(target: AppTarget, appLabel: String): String {
        if (target.isPrimaryUser) return appLabel.take(MAX_SHORT_LABEL_LENGTH)

        val suffix = "（分身${target.userId}）"
        val labelLimit = (MAX_SHORT_LABEL_LENGTH - suffix.length).coerceAtLeast(0)
        return appLabel.take(labelLimit) + suffix
    }

    fun longLabel(target: AppTarget, appLabel: String): String =
        if (target.isPrimaryUser) appLabel else "$appLabel（分身${target.userId}）"
}
