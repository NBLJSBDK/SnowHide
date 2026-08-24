package com.nbljsbdk.snowhide.platform.feedback

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.nbljsbdk.snowhide.core.feedback.HapticPort
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/** Android 震动适配，统一应用设置、静音和勿扰过滤。 */
class AndroidHapticAdapter(context: Context) : HapticPort {

    private val appContext = context.applicationContext

    override fun vibrate(type: HapticType) {
        SettingsRepository.init(appContext)
        if (!SettingsRepository.hapticEnabled.value) return
        val level = SettingsRepository.hapticLevel(type)
        if (level <= 0) return

        val audioManager = appContext.getSystemService(AudioManager::class.java)
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        if (notificationManager?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) return

        val vibrator = appContext.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        val (duration, amplitude) = when (level.coerceIn(1, 4)) {
            1 -> 60L to 192
            2 -> 100L to 255
            3 -> 140L to 255
            else -> 180L to 255
        }
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude)) }
    }
}
