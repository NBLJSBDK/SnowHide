package com.nbljsbdk.snowhide.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.nbljsbdk.snowhide.ui.util.AppIconLoader

/**
 * 懒加载应用图标（列表行/宫格共用组件）
 *
 * 首次组合时异步加载（图标包优先、系统回退、全局缓存），
 * 加载完成后自动显示；加载中显示灰色占位块。
 */
@Composable
fun LazyAppIcon(
    pkg: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(pkg) {
        icon = runCatching { AppIconLoader.loadIcon(pkg) }.getOrNull()
    }

    val bmp = icon
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size.value * 0.22f)),
        )
    } else {
        // 占位块（加载中/失败）
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size.value * 0.22f))
                .background(Color(0xFF2A2F38)),
        )
    }
}
