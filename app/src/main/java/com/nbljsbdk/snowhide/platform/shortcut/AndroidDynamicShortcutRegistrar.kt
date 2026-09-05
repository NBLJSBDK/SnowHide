package com.nbljsbdk.snowhide.platform.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutSpec
import com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity

/** 注册雪藏图标长按菜单中的动态快捷方式。 */
class AndroidDynamicShortcutRegistrar(context: Context) {
    private val appContext = context.applicationContext

    fun register(): Result<Unit> = runCatching {
        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)
            ?: error("系统不支持快捷方式管理")
        shortcutManager.setDynamicShortcuts(
            listOf(
                shortcut(
                    id = DesktopShortcutSpec.SMART_CLEAN_ID,
                    label = "智能清理",
                    icon = R.drawable.ic_sc_smart_clean,
                    action = ShortcutActionActivity.ACTION_SMART_CLEAN,
                ),
                shortcut(
                    id = DesktopShortcutSpec.FREEZE_ALL_ID,
                    label = "全部停用",
                    icon = R.drawable.ic_sc_freeze_all,
                    action = ShortcutActionActivity.ACTION_FREEZE_ALL,
                ),
                shortcut(
                    id = DesktopShortcutSpec.TOGGLE_QUICK_ID,
                    label = "快速启停",
                    icon = R.drawable.ic_sc_toggle,
                    action = ShortcutActionActivity.ACTION_TOGGLE_QUICK,
                ),
                shortcut(
                    id = DesktopShortcutSpec.ENABLE_ALL_ID,
                    label = "启用全部",
                    icon = R.drawable.ic_sc_enable_all,
                    action = ShortcutActionActivity.ACTION_ENABLE_ALL,
                ),
            ),
        )
    }

    private fun shortcut(
        id: String,
        label: String,
        icon: Int,
        action: String,
    ): ShortcutInfo = ShortcutInfo.Builder(appContext, id)
        .setShortLabel(label)
        .setIcon(Icon.createWithResource(appContext, icon))
        .setIntent(
            Intent(appContext, ShortcutActionActivity::class.java)
                .setAction(action),
        )
        .build()
}
