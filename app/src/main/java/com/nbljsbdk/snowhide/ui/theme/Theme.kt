package com.nbljsbdk.snowhide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 霜冻主题（设计文档 §3.1，浅色版——用户拍板：原深色底太阴暗）
 *
 * 固定浅霜白 + 冰蓝主题色；不做 Material You 动态取色——
 * 「霜冻」视觉语言需要自控配色，动态取色会破坏冻结隐喻的一致性。
 */
private val FrostColorScheme = lightColorScheme(
    primary = IceBlue,
    onPrimary = IceBlueOn,
    primaryContainer = IceBlueDim,
    onPrimaryContainer = IceBlue,
    secondary = FrostWhite,
    onSecondary = FrostBackground,
    tertiary = WarmOrange,
    onTertiary = WarmOrangeOn,
    error = WarmOrange,
    onError = WarmOrangeOn,
    background = FrostBackground,
    onBackground = FrostWhite,
    surface = FrostSurface,
    onSurface = FrostWhite,
    surfaceVariant = FrostCard,
    onSurfaceVariant = FrostWhiteDim,
    outline = FrostOutline,
)

@Composable
fun SnowHideTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FrostColorScheme,
        typography = Typography,
        content = content
    )
}
