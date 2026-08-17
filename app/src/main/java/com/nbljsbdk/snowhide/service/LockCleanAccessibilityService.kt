package com.nbljsbdk.snowhide.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 锁屏自动清理保活载体（AccessibilityService）
 *
 * 系统信任组件：进程被杀后系统自动重启，ColorOS/realme 默认不杀——
 * 保证 SCREEN_OFF/USER_PRESENT 动态广播持续注册，锁屏清理计时可靠。
 *
 * 不读取任何窗口内容（canRetrieveWindowContent=false，隐私友好），
 * 纯保活 + 广播注册载体。用户需在系统无障碍设置里手动开启。
 */
class LockCleanAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务常驻：在这里注册息屏/解锁广播（比 MainActivity 更可靠）
        LockCleanReceiver.register(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
