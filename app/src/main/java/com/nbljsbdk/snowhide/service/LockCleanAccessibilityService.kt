package com.nbljsbdk.snowhide.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nbljsbdk.snowhide.app.CompositionRoot
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceConnectionState

/**
 * 锁屏自动清理保活载体（AccessibilityService）
 *
 * 系统信任组件：进程被杀后系统自动重启，ColorOS/realme 默认不杀——
 * 保证 SCREEN_OFF/USER_PRESENT 动态广播持续注册，锁屏清理计时可靠。
 *
 * 读取 Recent 窗口用于划卡停用，同时继续承担锁屏清理保活和广播注册。
 * 不另起常驻前台服务：该系统托管服务在任务移除后仍保持独立生命周期。
 * 用户需在系统无障碍设置里手动开启。
 */
class LockCleanAccessibilityService : AccessibilityService() {

    private val recentSwipeController by lazy { RecentSwipeController(this) }
    private var connectionInitialized = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        initializeConnection()
    }

    /** 首次连接和系统重绑定共用同一条幂等初始化路径。 */
    private fun initializeConnection() {
        if (connectionInitialized) {
            AccessibilityServiceConnectionState.markConnected()
            return
        }
        try {
            CompositionRoot.init(this)
            // 服务常驻：在这里注册息屏/解锁广播（比 MainActivity 更可靠）
            LockCleanReceiver.register(this)
            recentSwipeController.onServiceConnected()
            connectionInitialized = true
            AccessibilityServiceConnectionState.markConnected()
        } catch (error: Throwable) {
            connectionInitialized = false
            AccessibilityServiceConnectionState.markDisconnected()
            runCatching { recentSwipeController.onServiceDisconnected() }
            LockCleanReceiver.unregister(this)
            Log.e(TAG, "无障碍服务初始化失败", error)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching { recentSwipeController.onAccessibilityEvent(event) }
            .onFailure { error -> Log.e(TAG, "无障碍事件接收失败", error) }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connectionInitialized = false
        AccessibilityServiceConnectionState.markDisconnected()
        recentSwipeController.onServiceDisconnected()
        LockCleanReceiver.unregister(this)
        // 允许系统在应用更新或服务短暂断开后通过 onRebind 恢复连接。
        super.onUnbind(intent)
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        initializeConnection()
    }

    override fun onDestroy() {
        connectionInitialized = false
        AccessibilityServiceConnectionState.markDisconnected()
        recentSwipeController.onDestroy()
        LockCleanReceiver.unregister(this)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SnowHideAccessibility"
    }
}
