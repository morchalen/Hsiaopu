package com.example.hsiaopu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ╔══════════════════════════════════════════════════════════╗
// ║  圆角体系 — iOS 连续曲线风格，全 App 统一引用        ║
// ║  不再散写数值；如需微调曲率，只改这里              ║
// ╚══════════════════════════════════════════════════════════╝

// 小圆角：内嵌小元素、标签
val CornerXS = RoundedCornerShape(8.dp)

// 中小圆角：小控件、抽屉项
val CornerSM = RoundedCornerShape(12.dp)

// 中圆角：设置分组卡片等
val CornerMD = RoundedCornerShape(16.dp)

// 大圆角：输入框、大容器
val CornerLG = RoundedCornerShape(20.dp)

// 胶囊：按钮、指示条（全圆）
val CornerFull = RoundedCornerShape(50)

// 气泡尾巴的曲率（模拟 iMessage 小尾巴）
private val CornerTail = 6.dp

/**
 * iMessage 风格消息气泡：三个圆角 + 一个 6dp 小尾巴。
 * 用户消息尾巴在右下角，AI 消息尾巴在左下角。
 */
fun bubbleShape(isUser: Boolean) = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = if (isUser) 18.dp else CornerTail,
    bottomEnd = if (isUser) CornerTail else 18.dp
)
