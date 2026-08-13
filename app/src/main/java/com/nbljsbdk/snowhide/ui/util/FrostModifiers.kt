package com.nbljsbdk.snowhide.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 冻结图标滤镜样式（用户拍板，美化设置可选，默认变蓝）
 */
enum class FreezeStyle {
    /** 保持原色：只降透明度，无色彩滤镜 */
    NONE,

    /** 变灰：去饱和 85%（老版霜化） */
    GRAY,

    /** 反色：色彩反转 */
    INVERT,

    /** 变蓝：轻微去饱和 + 冷蓝偏移（结冰效果，默认） */
    BLUE,
}

/**
 * 霜化视觉（设计文档 §3.1：冻结 = 结霜）
 *
 * [frosted]：已冻结内容按 [style] 蒙滤镜 + 半透明 + 300ms 渐变过渡。
 * 实现：drawWithCache 里给内容层套 ColorMatrix 滤镜
 * （graphicsLayer 不支持 colorFilter），alpha 用 graphicsLayer。
 */
fun Modifier.frosted(
    enabled: Boolean,
    style: FreezeStyle = FreezeStyle.BLUE,
): Modifier = composed {
    val progress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        label = "frost",
    )
    if (progress <= 0f) {
        this
    } else {
        val alphaLayer = graphicsLayer {
            alpha = 1f - 0.4f * progress // 透明度降至 60%
        }
        val matrix = matrixFor(style)
        if (matrix == null) {
            alphaLayer
        } else {
            alphaLayer.drawWithCache {
                val paint = Paint().apply {
                    colorFilter = ColorFilter.colorMatrix(matrix)
                }
                onDrawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
            }
        }
    }
}

/** 各样式对应的 ColorMatrix（NONE 返回 null = 无滤镜） */
private fun matrixFor(style: FreezeStyle): ColorMatrix? = when (style) {
    FreezeStyle.NONE -> null

    FreezeStyle.GRAY -> ColorMatrix(
        floatArrayOf(
            0.15f, 0.85f, 0.85f, 0f, 0f,
            0.85f, 0.15f, 0.85f, 0f, 0f,
            0.85f, 0.85f, 0.15f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    FreezeStyle.INVERT -> ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    FreezeStyle.BLUE -> ColorMatrix(
        floatArrayOf(
            0.70f, 0.15f, 0.15f, 0f, 10f,
            0.15f, 0.70f, 0.15f, 0f, 20f,
            0.10f, 0.20f, 0.70f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}
