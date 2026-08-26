package com.nbljsbdk.snowhide.domain.settings

/** 应用显式动画的速度档位；数值顺序与设置滑竿从左到右一致。 */
enum class AnimationLevel(
    val storageValue: Int,
    val label: String,
    val durationMillis: Int,
) {
    /** 不等待，直接完成状态切换。 */
    OFF(0, "关", 0),

    /** 速度最快的动画档位。 */
    HIGH(1, "高", 150),

    /** 默认速度。 */
    MEDIUM(2, "中", 300),

    /** 速度最慢的动画档位。 */
    LOW(3, "低", 500),

    ;

    companion object {
        fun fromStorageValue(value: Int): AnimationLevel =
            entries.firstOrNull { it.storageValue == value } ?: MEDIUM

        /** 兼容 0.4.0 及更早版本的开关设置。 */
        fun fromLegacy(enabled: Boolean): AnimationLevel = if (enabled) MEDIUM else OFF
    }
}
