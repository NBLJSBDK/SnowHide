package com.nbljsbdk.snowhide.service

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import kotlinx.coroutines.runBlocking

/**
 * 锁屏自动清理（用户拍板语义）
 *
 * - 首次息屏（SCREEN_OFF）→ 设闹钟（延迟 N 分钟；0=立即）；已挂起计时再次息屏忽略
 * - 解锁（USER_PRESENT）→ 取消闹钟；下次息屏重新计时
 * - 闹钟触发 → 若仍处于锁屏 → 执行一次智能清理（豁免锁定）→ 通知（开关控制）
 *
 * 生命周期设计：
 * - SCREEN_OFF/USER_PRESENT 动态注册（MainActivity，进程存活期间生效）
 * - 闹钟触发静态注册（进程被杀后闹钟仍能唤醒执行清理）
 * - 进程死后闹钟无法被解锁取消 → 触发时检查 Keyguard 兜底
 */
class LockCleanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenOff(context)
            Intent.ACTION_USER_PRESENT -> cancelAlarm(context)
            ACTION_LOCK_CLEAN -> onAlarm(context)
            Intent.ACTION_BOOT_COMPLETED -> {
                // 开机自启动：注册息屏/解锁广播（进程存活期间锁屏清理生效）
                register(context)
            }
        }
    }

    /** 首次息屏：启用且无挂起计时 → 设闹钟（0=立即执行） */
    private fun onScreenOff(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        if (prefs.getBoolean(KEY_PENDING, false)) return // 已挂起计时：双击亮屏后再灭不重置
        val delayMinutes = prefs.getInt(KEY_DELAY, 30)
        val delayMs = delayMinutes * 60_000L
        if (delayMs <= 0) {
            executeClean(context)
            return
        }
        prefs.edit().putBoolean(KEY_PENDING, true).apply()
        val alarm = alarmManager(context)
        alarm.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMs,
            alarmPending(context),
        )
    }

    /** 解锁：取消闹钟，清挂起标记 */
    private fun cancelAlarm(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING, false).apply()
        alarmManager(context).cancel(alarmPending(context))
    }

    /** 闹钟触发：仍锁屏才清理（进程死后闹钟没被解锁取消，Keyguard 兜底） */
    private fun onAlarm(context: Context) {
        val prefs = prefs(context)
        prefs.edit().putBoolean(KEY_PENDING, false).apply()
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val km = context.getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return // 已解锁：不清理
        executeClean(context)
    }

    /** 执行一次智能清理（豁免锁定），完成后按开关通知 */
    private fun executeClean(context: Context) {
        // 冷启动初始化（闹钟可能拉起新进程，不经过 MainActivity）
        EngineRegistry.init(context)
        GridRepository.init(context)
        FrozenStateStore.init(context)
        SettingsRepository.init(context)
        val useCase = FreezeUseCase(FreezeExecutor(EngineManager), GridRepository, EngineManager)
        Thread {
            val n = runCatching { runBlocking { useCase.quickClean() } }
                .getOrNull()?.getOrNull() ?: -1
            runCatching { runBlocking { FrozenStateStore.refresh() } }
            if (prefs(context).getBoolean(KEY_NOTIFY, true)) {
                notifyResult(context, n)
            }
        }.start()
    }

    /** 清理结果通知（后台 Toast 被吞，通知兜底） */
    private fun notifyResult(context: Context, n: Int) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "锁屏自动清理", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val message = if (n >= 0) "已停用 $n 个应用" else "清理执行失败"
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_snowflake)
                .setContentTitle("锁屏自动清理")
                .setContentText(message)
                // 无动作 PendingIntent 仅用于让点击触发 autoCancel，不打开任何界面
                .setContentIntent(notificationClickPending(context))
                .setAutoCancel(true)
                .build()
            nm.notify(2001, notification)
        }
    }

    companion object {
        const val ACTION_LOCK_CLEAN = "com.nbljsbdk.snowhide.action.LOCK_CLEAN"
        private const val KEY_ENABLED = "lock_clean_enabled"
        private const val KEY_DELAY = "lock_clean_delay"
        private const val KEY_NOTIFY = "lock_clean_notify"
        private const val KEY_PENDING = "lock_clean_pending"
        private const val CHANNEL_ID = "lock_clean"
        private const val ACTION_NOTIFICATION_CLICK =
            "com.nbljsbdk.snowhide.action.LOCK_CLEAN_NOTIFICATION_CLICK"

        /** 动态注册 SCREEN_OFF/USER_PRESENT（MainActivity 调用） */
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(LockCleanReceiver(), filter)
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)

        private fun alarmManager(context: Context) =
            context.getSystemService(AlarmManager::class.java)

        private fun alarmPending(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, LockCleanReceiver::class.java).setAction(ACTION_LOCK_CLEAN),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** 点击通知不执行任何业务，仅配合 autoCancel 清除通知 */
        private fun notificationClickPending(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                2002,
                Intent(context, LockCleanReceiver::class.java).setAction(ACTION_NOTIFICATION_CLICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
