package com.nbljsbdk.snowhide.platform.shortcut

import android.content.Context
import android.content.pm.ShortcutManager
import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutMaintenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android 快捷方式记录清理适配。 */
class AndroidDesktopShortcutMaintenance(
    context: Context,
    private val engineProvider: EngineProvider,
    private val dynamicShortcutRegistrar: AndroidDynamicShortcutRegistrar,
) : DesktopShortcutMaintenance {
    private val appContext = context.applicationContext

    override suspend fun clearAllShortcuts(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val shortcutManager = shortcutManager()
            clearSystemShortcutRecords(shortcutManager.pinnedShortcuts.size)
        }
    }

    /** 系统没有按 shortcut ID 清除 pinned 记录的公开接口，统一清理后恢复动态菜单。 */
    private suspend fun clearSystemShortcutRecords(count: Int): Int {
        val engine = engineProvider.primaryEngine.value
            ?: error("请先启用 Shizuku")
        engine.exec(clearAllShortcutsCommand()).getOrThrow()
        // clear-shortcuts 会连同动态快捷方式一起清理；立即恢复雪藏长按菜单。
        dynamicShortcutRegistrar.register().getOrThrow()
        return count
    }

    private fun shortcutManager(): ShortcutManager =
        appContext.getSystemService(ShortcutManager::class.java)
            ?: error("系统不支持快捷方式管理")

    private fun clearAllShortcutsCommand(): String =
        "cmd shortcut clear-shortcuts --user $PRIMARY_USER_ID ${appContext.packageName}"

    private companion object {
        const val PRIMARY_USER_ID = 0
    }
}
