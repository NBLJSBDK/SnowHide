package com.nbljsbdk.snowhide.domain.settings

/** 应用统一使用的快速连续动画；旧版本档位只用于兼容已保存的设置。 */
enum class AnimationLevel(
    val storageValue: Int,
    val label: String,
    val durationMillis: Int,
) {
    /** 速度最快且保留连续性的动画。 */
    FAST(1, "最快", 150),

    ;

    companion object {
        /** 旧版 0/1/2/3 档位统一迁移到固定快速动画。 */
        fun fromStorageValue(value: Int): AnimationLevel = FAST

        /** 兼容 0.4.0 及更早版本的开关设置。 */
        fun fromLegacy(enabled: Boolean): AnimationLevel = FAST
    }
}
