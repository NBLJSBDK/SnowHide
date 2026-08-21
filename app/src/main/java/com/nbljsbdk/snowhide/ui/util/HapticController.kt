package com.nbljsbdk.snowhide.ui.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/**
 * 统一震动入口。
 *
 * 只在动作确认或完成时调用，不在拖动过程和批量逐项执行时调用。
 * 静音、勿扰和没有震动器时静默跳过，避免打扰用户或抛出系统异常。
 */
object HapticController {

    fun vibrate(context: Context, type: HapticType) {
        val appContext = context.applicationContext
        SettingsRepository.init(appContext)
        if (!SettingsRepository.hapticEnabled.value) return

        val level = SettingsRepository.hapticLevel(type)
        if (level <= 0) return

        val audioManager = appContext.getSystemService(AudioManager::class.java)
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        if (notificationManager?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            return
        }

        val vibrator = appContext.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return

        val (duration, amplitude) = when (level.coerceIn(1, 4)) {
            1 -> 60L to 192
            2 -> 100L to 255
            3 -> 140L to 255
            else -> 180L to 255
        }
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }
}
