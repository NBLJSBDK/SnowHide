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
 * 霜化视觉（设计文档 §3.1：冻结 = 结霜）
 *
 * [frosted]：已冻结内容蒙霜——去饱和 85% + 半透明 + 300ms 渐变过渡（结霜/化霜）。
 * 实现：drawWithCache 里给内容层套 ColorMatrix 饱和度滤镜（Compose 官方推荐方式，
 * graphicsLayer 不支持 colorFilter），alpha 用 graphicsLayer。
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
            alpha = 1f - 0.4f * progress // 透明度降至 60%
        }.drawWithCache {
            val paint = Paint().apply {
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            1f - 0.85f * progress, 0.85f * progress, 0.85f * progress, 0f, 0f,
                            0.85f * progress, 1f - 0.85f * progress, 0.85f * progress, 0f, 0f,
                            0.85f * progress, 0.85f * progress, 1f - 0.85f * progress, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        )
                    )
                )
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
