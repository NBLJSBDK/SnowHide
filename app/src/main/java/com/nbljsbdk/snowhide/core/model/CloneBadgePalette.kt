package com.nbljsbdk.snowhide.core.model

/** 分身徽标的稳定 ARGB 配色；宫格和桌面快捷方式共用同一套映射。 */
object CloneBadgePalette {
    private val colors = intArrayOf(
        0xFFE85D75.toInt(),
        0xFFF0A94B.toInt(),
        0xFF45C486.toInt(),
        0xFF4B9FE3.toInt(),
        0xFF8870E8.toInt(),
        0xFFE16CB0.toInt(),
    )

    fun colorFor(userId: Int): Int = colors[Math.floorMod(userId, colors.size)]
}
