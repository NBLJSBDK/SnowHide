package com.nbljsbdk.snowhide.ui.util

import android.content.Context
import com.nbljsbdk.snowhide.core.feedback.FeedbackRegistry
import com.nbljsbdk.snowhide.core.feedback.HapticType

/**
 * 统一震动入口。
 *
 * 只在动作确认或完成时调用，不在拖动过程和批量逐项执行时调用。
 * 静音、勿扰和没有震动器时静默跳过，避免打扰用户或抛出系统异常。
 */
object HapticController {

    fun vibrate(context: Context, type: HapticType) {
        // Context 保留在 UI 调用协议中，实际硬件适配由 CompositionRoot 安装。
        FeedbackRegistry.vibrate(type)
    }
}
