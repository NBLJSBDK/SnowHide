package com.nbljsbdk.snowhide.platform.shortcut

import android.graphics.Bitmap
import com.nbljsbdk.snowhide.core.model.AppTarget

/** 快捷方式图标适配端口，允许组合根注入当前 UI 选择的图标包。 */
fun interface ShortcutIconProvider {
    suspend fun load(target: AppTarget): Bitmap?
}
