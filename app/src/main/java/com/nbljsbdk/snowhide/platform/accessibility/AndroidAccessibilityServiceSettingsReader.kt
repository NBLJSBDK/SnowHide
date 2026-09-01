package com.nbljsbdk.snowhide.platform.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceSettingsReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 精确读取雪藏无障碍组件是否在系统设置中启用。 */
class AndroidAccessibilityServiceSettingsReader(
    context: Context,
    private val serviceComponent: ComponentName,
) : AccessibilityServiceSettingsReader {
    private val appContext = context.applicationContext
    private val _enabledInSystem = MutableStateFlow(false)
    override val enabledInSystem: StateFlow<Boolean> = _enabledInSystem.asStateFlow()

    init {
        refresh()
    }

    override fun refresh() {
        _enabledInSystem.value = runCatching {
            val globallyEnabled = Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
            if (!globallyEnabled) return@runCatching false
            val enabledServices = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            enabledServices.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == serviceComponent }
        }.getOrDefault(false)
    }
}
