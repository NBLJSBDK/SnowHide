package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.accessibility.AccessibilityFeatureSettings
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceConnection
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceConnectionStatus
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceSettingsReader
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementStatus
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityRequirementUseCaseTest {

    @Test
    fun statusOnlyPromptsWhenRequiredServiceIsUnavailable() {
        val settings = FakeAccessibilitySettings()
        val system = FakeAccessibilitySettingsReader()
        val connection = FakeAccessibilityConnection()
        val useCase = AccessibilityRequirementUseCase(settings, system, connection)

        assertEquals(AccessibilityRequirementStatus.NOT_REQUIRED, useCase.currentState().status)

        settings.swipe.value = true
        assertEquals(AccessibilityRequirementStatus.NOT_ENABLED, useCase.currentState().status)

        system.enabled.value = true
        assertEquals(
            AccessibilityRequirementStatus.CHECKING_CONNECTION,
            useCase.currentState().status,
        )

        connection.connectionStatus.value = AccessibilityServiceConnectionStatus.DISCONNECTED
        assertEquals(
            AccessibilityRequirementStatus.ENABLED_NOT_CONNECTED,
            useCase.currentState().status,
        )

        connection.connectionStatus.value = AccessibilityServiceConnectionStatus.CONNECTED
        assertEquals(AccessibilityRequirementStatus.READY, useCase.currentState().status)

        system.enabled.value = false
        assertEquals(AccessibilityRequirementStatus.NOT_ENABLED, useCase.currentState().status)

        settings.swipe.value = false
        assertEquals(AccessibilityRequirementStatus.NOT_REQUIRED, useCase.currentState().status)
    }

    @Test
    fun coldStartCheckingStateDoesNotPromptBeforeConnectionResult() {
        val settings = FakeAccessibilitySettings().apply { swipe.value = true }
        val system = FakeAccessibilitySettingsReader().apply { enabled.value = true }
        val useCase = AccessibilityRequirementUseCase(
            settings,
            system,
            FakeAccessibilityConnection(),
        )

        assertEquals(AccessibilityRequirementStatus.CHECKING_CONNECTION, useCase.currentState().status)
        assertEquals(false, useCase.currentState().shouldPrompt)
    }

    @Test
    fun refreshDelegatesToSystemReader() {
        val reader = FakeAccessibilitySettingsReader()
        val useCase = AccessibilityRequirementUseCase(
            FakeAccessibilitySettings(),
            reader,
            FakeAccessibilityConnection(),
        )

        useCase.refreshSystemState()

        assertEquals(1, reader.refreshCount)
    }

    private class FakeAccessibilitySettings : AccessibilityFeatureSettings {
        val swipe = MutableStateFlow(false)
        val lockClean = MutableStateFlow(false)
        override val swipeDisableEnabled: StateFlow<Boolean> = swipe
        override val lockCleanEnabled: StateFlow<Boolean> = lockClean
    }

    private class FakeAccessibilitySettingsReader : AccessibilityServiceSettingsReader {
        val enabled = MutableStateFlow(false)
        override val enabledInSystem: StateFlow<Boolean> = enabled
        var refreshCount = 0

        override fun refresh() {
            refreshCount += 1
        }
    }

    private class FakeAccessibilityConnection : AccessibilityServiceConnection {
        val connectionStatus = MutableStateFlow(AccessibilityServiceConnectionStatus.CHECKING)
        override val status: StateFlow<AccessibilityServiceConnectionStatus> = connectionStatus
    }
}
