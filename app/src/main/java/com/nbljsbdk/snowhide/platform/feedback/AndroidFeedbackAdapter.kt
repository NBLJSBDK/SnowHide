package com.nbljsbdk.snowhide.platform.feedback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.core.feedback.FeedbackDuration
import com.nbljsbdk.snowhide.core.feedback.FeedbackPort
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/** Android Toast/通知适配，前台 Toast 与后台失败通知保持原语义。 */
class AndroidFeedbackAdapter(context: Context) : FeedbackPort {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun toast(message: String, duration: FeedbackDuration, enabled: Boolean) {
        SettingsRepository.init(appContext)
        if (!enabled || !SettingsRepository.showToast.value) return
        val toastDuration = if (duration == FeedbackDuration.LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        mainHandler.post { Toast.makeText(appContext, message, toastDuration).show() }
    }

    override fun notifyFailure(title: String, message: String) {
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

    private companion object {
        const val FAILURE_CHANNEL_ID = "operation_failure"
        const val FAILURE_CHANNEL_NAME = "操作失败"
        const val FAILURE_NOTIFICATION_ID = 3001
    }
}
