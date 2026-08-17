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
 * 冻结图标滤镜样式（用户拍板终版）
 */
enum class FreezeStyle {
    /** 真正原色：完全无任何遮罩（冻结靠雪花角标区分） */
    NONE,

    /** 变灰：去饱和 85% */
    GRAY,

    /** 反色：色彩反转 */
    INVERT,

    /** 淡化：纯 60% 透明遮罩（无颜色矩阵，原「变蓝」效果） */
    BLUE,
}

/**
 * 霜化视觉（设计文档 §3.1：冻结 = 结霜）
 *
 * [frosted]：已冻结内容按 [style] 蒙滤镜 + 半透明 + 300ms 渐变过渡。
 * NONE = 真正原色（连透明度都不动，完全原样）。
 * 性能：解冻或原色时**直接返回不进入 composed**——
 * 避免大列表滑动时每个 item 创建 Animatable（真机 38% jank 实锤）。
 * 实现：drawWithCache 里给内容层套 ColorMatrix 滤镜
 * （graphicsLayer 不支持 colorFilter），alpha 用 graphicsLayer。
 */
fun Modifier.frosted(
    enabled: Boolean,
    style: FreezeStyle = FreezeStyle.BLUE,
): Modifier {
    // 解冻或原色：完全无视觉变化，零开销直接返回
    if (!enabled || style == FreezeStyle.NONE) return this
    return composed {
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
}

/** 各样式对应的 ColorMatrix（null = 无颜色矩阵，仅遮罩） */
private fun matrixFor(style: FreezeStyle): ColorMatrix? = when (style) {
    FreezeStyle.NONE, FreezeStyle.BLUE -> null

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
}
