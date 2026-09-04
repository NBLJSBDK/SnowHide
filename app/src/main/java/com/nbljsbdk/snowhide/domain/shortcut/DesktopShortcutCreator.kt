package com.nbljsbdk.snowhide.domain.shortcut

import com.nbljsbdk.snowhide.core.model.AppTarget

/** 桌面固定快捷方式的 Android 适配端口。 */
interface DesktopShortcutCreator {
    /** 请求系统桌面固定一个明确用户空间中的应用目标。 */
    suspend fun requestPin(target: AppTarget, appLabel: String): Result<Unit>
}
