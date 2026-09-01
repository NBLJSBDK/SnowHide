package com.nbljsbdk.snowhide.domain.accessibility

import com.nbljsbdk.snowhide.core.accessibility.AccessibilityFeatureSettings
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceConnection
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceConnectionStatus
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceSettingsReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class AccessibilityRequirementStatus {
    NOT_REQUIRED,
    NOT_ENABLED,
    CHECKING_CONNECTION,
    ENABLED_NOT_CONNECTED,
    READY,
}

/** 主屏提示所需的纯业务状态；不会阻塞任何基础冻结能力。 */
data class AccessibilityRequirementState(
    val status: AccessibilityRequirementStatus,
    val swipeDisableEnabled: Boolean,
    val lockCleanEnabled: Boolean,
    val enabledInSystem: Boolean,
    val serviceConnected: Boolean,
) {
    val shouldPrompt: Boolean
        get() = status == AccessibilityRequirementStatus.NOT_ENABLED ||
            status == AccessibilityRequirementStatus.ENABLED_NOT_CONNECTED
}

/** 合并用户开关、系统启用状态和服务实际连接状态。 */
class AccessibilityRequirementUseCase(
    private val settings: AccessibilityFeatureSettings,
    private val settingsReader: AccessibilityServiceSettingsReader,
    private val connection: AccessibilityServiceConnection,
) {
    val state: Flow<AccessibilityRequirementState> = combine(
        settings.swipeDisableEnabled,
        settings.lockCleanEnabled,
        settingsReader.enabledInSystem,
        connection.status,
        ::reduce,
    )

    fun currentState(): AccessibilityRequirementState = reduce(
        settings.swipeDisableEnabled.value,
        settings.lockCleanEnabled.value,
        settingsReader.enabledInSystem.value,
        connection.status.value,
    )

    fun refreshSystemState() {
        settingsReader.refresh()
    }

    private fun reduce(
        swipeDisableEnabled: Boolean,
        lockCleanEnabled: Boolean,
        enabledInSystem: Boolean,
        connectionStatus: AccessibilityServiceConnectionStatus,
    ): AccessibilityRequirementState {
        val status = when {
            !swipeDisableEnabled && !lockCleanEnabled -> AccessibilityRequirementStatus.NOT_REQUIRED
            !enabledInSystem -> AccessibilityRequirementStatus.NOT_ENABLED
            connectionStatus == AccessibilityServiceConnectionStatus.CHECKING ->
                AccessibilityRequirementStatus.CHECKING_CONNECTION
            connectionStatus == AccessibilityServiceConnectionStatus.DISCONNECTED ->
                AccessibilityRequirementStatus.ENABLED_NOT_CONNECTED
            else -> AccessibilityRequirementStatus.READY
        }
        return AccessibilityRequirementState(
            status = status,
            swipeDisableEnabled = swipeDisableEnabled,
            lockCleanEnabled = lockCleanEnabled,
            enabledInSystem = enabledInSystem,
            serviceConnected = connectionStatus == AccessibilityServiceConnectionStatus.CONNECTED,
        )
    }
}
