package com.nbljsbdk.snowhide.ui.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/**
 * 统一处理短暂提示和后台失败反馈。
 *
 * Toast 是否显示由设置统一控制；后台入口没有 Snackbar 容器时使用通知提示失败。
 */
object FeedbackController {

    private const val FAILURE_CHANNEL_ID = "operation_failure"
    private const val FAILURE_CHANNEL_NAME = "操作失败"
    private const val FAILURE_NOTIFICATION_ID = 3001
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 显示受设置控制的 Toast；enabled=false 可用于场景级开关。 */
    fun toast(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_SHORT,
        enabled: Boolean = true,
    ) {
        val appContext = context.applicationContext
        SettingsRepository.init(appContext)
        if (!enabled || !SettingsRepository.showToast.value) return
        mainHandler.post { Toast.makeText(appContext, message, duration).show() }
    }

    /** 后台入口失败时使用通知，避免关闭 Toast 后完全没有结果。 */
    fun notifyFailure(context: Context, title: String, message: String) {
        val appContext = context.applicationContext
        runCatching {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    FAILURE_CHANNEL_ID,
                    FAILURE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            manager.notify(
                FAILURE_NOTIFICATION_ID,
                Notification.Builder(appContext, FAILURE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_snowflake)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
