package com.nbljsbdk.snowhide.core.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 依赖无障碍服务的用户设置端口，持久化实现仍由 data 层唯一拥有。 */
interface AccessibilityFeatureSettings {
    val swipeDisableEnabled: StateFlow<Boolean>
    val lockCleanEnabled: StateFlow<Boolean>
}

/** 系统设置中本应用无障碍服务的启用状态。 */
interface AccessibilityServiceSettingsReader {
    val enabledInSystem: StateFlow<Boolean>
    fun refresh()
}

enum class AccessibilityServiceConnectionStatus {
    CHECKING,
    CONNECTED,
    DISCONNECTED,
}

/** 当前进程中无障碍服务的实际连接状态。 */
interface AccessibilityServiceConnection {
    val status: StateFlow<AccessibilityServiceConnectionStatus>
}

/** 服务生命周期写入、业务层只读的进程级瞬时状态。 */
object AccessibilityServiceConnectionState : AccessibilityServiceConnection {
    private val _status = MutableStateFlow(AccessibilityServiceConnectionStatus.CHECKING)
    override val status: StateFlow<AccessibilityServiceConnectionStatus> = _status.asStateFlow()

    fun markConnected() {
        _status.value = AccessibilityServiceConnectionStatus.CONNECTED
    }

    fun markDisconnected() {
        _status.value = AccessibilityServiceConnectionStatus.DISCONNECTED
    }

    /** Activity 恢复时重查；已连接服务不回退到检查中。 */
    fun markChecking() {
        if (_status.value != AccessibilityServiceConnectionStatus.CONNECTED) {
            _status.value = AccessibilityServiceConnectionStatus.CHECKING
        }
    }

    /** 宽限期结束仍无连接回调时，才确认服务未启动。 */
    fun markDisconnectedIfChecking() {
        if (_status.value == AccessibilityServiceConnectionStatus.CHECKING) {
            markDisconnected()
        }
    }
}
