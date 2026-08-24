package com.nbljsbdk.snowhide.core.feedback

/** 业务层可理解的 Toast 时长，避免把 Android Toast 常量泄漏到 core。 */
enum class FeedbackDuration {
    SHORT,
    LONG,
}

interface FeedbackPort {
    fun toast(message: String, duration: FeedbackDuration = FeedbackDuration.SHORT, enabled: Boolean = true)

    fun notifyFailure(title: String, message: String)
}

interface HapticPort {
    fun vibrate(type: HapticType)
}

/** 进程级反馈适配器，由 CompositionRoot 安装 Android 实现。 */
object FeedbackRegistry {

    @Volatile
    private var feedbackPort: FeedbackPort = NoOpFeedbackPort

    @Volatile
    private var hapticPort: HapticPort = NoOpHapticPort

    fun install(feedback: FeedbackPort, haptic: HapticPort) {
        feedbackPort = feedback
        hapticPort = haptic
    }

    fun toast(
        message: String,
        duration: FeedbackDuration = FeedbackDuration.SHORT,
        enabled: Boolean = true,
    ) = feedbackPort.toast(message, duration, enabled)

    fun notifyFailure(title: String, message: String) = feedbackPort.notifyFailure(title, message)

    fun vibrate(type: HapticType) = hapticPort.vibrate(type)

    private object NoOpFeedbackPort : FeedbackPort {
        override fun toast(message: String, duration: FeedbackDuration, enabled: Boolean) = Unit
        override fun notifyFailure(title: String, message: String) = Unit
    }

    private object NoOpHapticPort : HapticPort {
        override fun vibrate(type: HapticType) = Unit
    }
}
