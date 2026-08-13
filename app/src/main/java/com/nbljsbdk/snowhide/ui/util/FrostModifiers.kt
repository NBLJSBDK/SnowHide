package com.nbljsbdk.snowhide.ui.util

import androidx.compose.animation.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 霜化视觉组件（设计文档 §3.1：冻结 = 结霜）
 *
 * [frosted]：已冻结图标蒙霜——去饱和 85% + 半透明 + 300ms 渐变过渡（结霜/化霜）。
 * 图标圆角蒙版在宫格组件内用 clip(RoundedCornerShape(22%)) 实现。
 */
fun Modifier.frosted(enabled: Boolean, frostLevel: Float = 1f): Modifier = composed {
    val progress by animateFloatAsState(
        targetValue = if (enabled) frostLevel else 0f,
        label = "frost",
    )
    if (progress <= 0f) {
        this
    } else {
        graphicsLayer {
            // ColorMatrix 去饱和（饱和度 0.15 = 去饱和 85%）
            val saturation = 1f - 0.85f * progress
            val sr = (0.213f + 0.787f * saturation)
            val sg = (0.715f - 0.715f * saturation)
            val sb = (0.072f - 0.072f * saturation)
            val sr2 = (0.213f - 0.213f * saturation)
            val sg2 = (0.715f + 0.285f * saturation)
            val sb2 = (0.072f - 0.072f * saturation)
            val sr3 = (0.213f - 0.213f * saturation)
            val sg3 = (0.715f - 0.715f * saturation)
            val sb3 = (0.072f + 0.928f * saturation)
            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        sr, sg, sb, 0f, 0f,
                        sr2, sg2, sb2, 0f, 0f,
                        sr3, sg3, sb3, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
            alpha = 1f - 0.4f * progress // 透明度降至 60%
        }
    }
}
