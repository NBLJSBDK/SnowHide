package com.nbljsbdk.snowhide.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务常驻：在这里注册息屏/解锁广播（比 MainActivity 更可靠）
        LockCleanReceiver.register(this)
        recentSwipeController.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        recentSwipeController.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        recentSwipeController.onDestroy()
        super.onDestroy()
    }
}
