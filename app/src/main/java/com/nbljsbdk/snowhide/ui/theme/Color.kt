package com.nbljsbdk.snowhide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 霜冻主题配色（设计文档 §3.1）
 *
 * 冻结 = 结霜意象：深色底 + 冰蓝主题 + 霜白冻结态 + 暖橙反向操作。
 */

// 背景与容器
val FrostBackground = Color(0xFF0F1115)   // 深色底
val FrostCard = Color(0xFF1A1E24)         // 卡片

// 冰蓝主题
val IceBlue = Color(0xFF7EB6FF)           // 主题色
val IceBlueDim = Color(0xFF24344D)        // 深蓝灰容器
val IceBlueOn = Color(0xFF0D1B2E)         // 冰蓝上的文字

// 冻结态
val FrostWhite = Color(0xFFB8C4D9)        // 霜白（冻结图标/文字）
val FrostWhiteDim = Color(0xFF6B7687)     // 霜灰（冻结角标辅助）

// 反向操作（解冻/危险）
val WarmOrange = Color(0xFFFF8C66)        // 暖橙（全部解冻/快速清理等）
val WarmOrangeOn = Color(0xFF2B1206)

// 通用
val FrostSurface = Color(0xFF15181E)
val FrostOutline = Color(0xFF2A2F38)
