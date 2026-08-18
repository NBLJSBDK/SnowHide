package com.nbljsbdk.snowhide.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * 描边文字（白字黑边，用户拍板）
 *
 * 深色/浅色背景（壁纸透明）下保证可读性。实现：双层 Text 叠加——
 * 底层 Stroke 描边 + 上层 Solid 填充。性能开销小（只多一次文本绘制
 * pass，无重组开销），大列表可放心使用。
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fillColor: Color = Color.White,
    outlineColor: Color = Color.Black.copy(alpha = 0.65f),
    outlineWidth: Float = 3f,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val outlineStyle = style.copy(
        color = outlineColor,
        drawStyle = Stroke(width = outlineWidth),
    )
    val fillStyle = style.copy(color = fillColor)
    // 两个 Text 同位置叠加：Box 子项左上对齐、内容尺寸一致 → 完全重合
    Box(modifier = modifier) {
        Text(
            text = text,
            style = outlineStyle,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
        )
        Text(
            text = text,
            style = fillStyle,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
        )
    }
}
