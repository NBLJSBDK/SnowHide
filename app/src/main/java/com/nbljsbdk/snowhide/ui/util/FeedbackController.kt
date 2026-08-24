package com.nbljsbdk.snowhide.ui.util

import android.content.Context
import android.widget.Toast
import com.nbljsbdk.snowhide.core.feedback.FeedbackDuration
import com.nbljsbdk.snowhide.core.feedback.FeedbackRegistry

/**
 * 统一处理短暂提示和后台失败反馈。
 *
 * Toast 是否显示由设置统一控制；后台入口没有 Snackbar 容器时使用通知提示失败。
 */
object FeedbackController {

    /** 显示受设置控制的 Toast；enabled=false 可用于场景级开关。 */
    fun toast(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_SHORT,
        enabled: Boolean = true,
    ) {
        // Context 保留在 UI 调用协议中，实际 Android 适配由 CompositionRoot 安装。
        val feedbackDuration = if (duration == Toast.LENGTH_LONG) {
            FeedbackDuration.LONG
        } else {
            FeedbackDuration.SHORT
        }
        FeedbackRegistry.toast(message, feedbackDuration, enabled)
    }

    /** 后台入口失败时使用通知，避免关闭 Toast 后完全没有结果。 */
    fun notifyFailure(context: Context, title: String, message: String) {
        FeedbackRegistry.notifyFailure(title, message)
    }
}
