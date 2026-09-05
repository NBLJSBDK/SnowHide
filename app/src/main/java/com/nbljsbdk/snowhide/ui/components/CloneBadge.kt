package com.nbljsbdk.snowhide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val cloneBadgeColors = listOf(
    Color(0xFFE85D75),
    Color(0xFFF0A94B),
    Color(0xFF45C486),
    Color(0xFF4B9FE3),
    Color(0xFF8870E8),
    Color(0xFFE16CB0),
)

/**
 * 分身图标角标：保持原图标完整，只在右上角叠加轻量的复制标记。
 *
 * 用户空间 ID 只放在无障碍描述里，不把 user 999 之类的内部实现细节
 * 直接画在宫格图标上；文件夹小预览使用纯色标记，避免缩小后变脏。
 */
@Composable
fun CloneBadge(
    userId: Int,
    iconSize: Dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val badgeSize = (iconSize * 0.30f).coerceAtLeast(if (compact) 8.dp else 12.dp)
    val innerPadding = if (compact) 1.dp else 1.5.dp
    // 用用户空间稳定取色，避免 Compose 重组或应用重启后颜色跳变。
    val badgeColor = cloneBadgeColors[userId % cloneBadgeColors.size]

    Box(
        modifier = modifier
            .size(badgeSize)
            .shadow(1.dp, CircleShape)
            .background(Color.White.copy(alpha = 0.96f), CircleShape)
            .padding(innerPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (compact) 2.dp else 3.dp))
                .background(badgeColor),
        ) {
            if (!compact) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "分身用户 $userId",
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                )
            }
        }
    }
}
