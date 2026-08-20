package com.nbljsbdk.snowhide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 霜冻主题配色（设计文档 §3.1，用户拍板浅色版）
 *
 * 冻结 = 结霜意象：浅霜白底 + 冰蓝主题 + 深蓝灰文字 + 暖橙反向操作。
 */

// 背景与容器
val FrostBackground = Color(0xFFE9F1F9)   // 浅霜白底
val FrostCard = Color(0xFFD8E4F1)         // 卡片浅蓝灰

// 冰蓝主题
val IceBlue = Color(0xFF3D7EC9)           // 主题色（浅底对比足够）
val IceBlueDim = Color(0xFFC9DDF3)        // 浅蓝容器
val IceBlueOn = Color(0xFFFFFFFF)         // 冰蓝上的文字
val TianyiBlue = Color(0xFF66CCFF)        // 洛天依蓝：冻结雪花角标

// 冻结态
val FrostWhite = Color(0xFF24303E)        // 主文字（深蓝灰）
val FrostWhiteDim = Color(0xFF5E7085)     // 次要文字（灰蓝）

// 反向操作（解冻/危险）
val WarmOrange = Color(0xFFE56A3C)        // 暖橙（全部解冻/快速清理等）
val WarmOrangeOn = Color(0xFFFFFFFF)

// 通用
val FrostSurface = Color(0xFFF4F8FC)
val FrostOutline = Color(0xFFB9CBE0)
