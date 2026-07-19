package com.example.hsiaopu.ui.theme

import androidx.compose.ui.graphics.Color

// ╔══════════════════════════════════════════════════════════╗
// ║  色彩系统 — 以黑白灰为主导，低饱和彩色为辅助       ║
// ║  支持深色/浅色双模式，遵循 WCAG AA 可访问性标准         ║
// ╚══════════════════════════════════════════════════════════╝

// ============================================================
// 一、基础中性色系统 (Foundation Neutral System)
//    黑、白＋六级灰度，构成整个 App 的色彩骨架
// ============================================================

// 极色
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

// 六级灰度层次（从浅到深）
// systemGray1 最浅 ≈ iOS systemGray6，systemGray6 最深 ≈ iOS systemGray
val SystemGray1 = Color(0xFFF2F2F7)   // 最浅灰 — 浅色模式背景
val SystemGray2 = Color(0xFFE5E5EA)   // 浅灰 — 浅色模式分割线 / 卡片衬底
val SystemGray3 = Color(0xFFD1D1D6)   // 中浅灰 — 浅色模式禁用态
val SystemGray4 = Color(0xFFC7C7CC)   // 中灰 — 占位文字 / 辅助线
val SystemGray5 = Color(0xFFAEAEB2)   // 中深灰 — 次级文字
val SystemGray6 = Color(0xFF8E8E93)   // 深灰 — 浅色模式正文 / 深色模式辅助文字

// ============================================================
// 二、功能指示色系统 (Functional Indicator System)
//    低饱和度彩色，仅在关键状态指示时使用
// ============================================================

// 红色 — 错误、删除、危险操作
val FunctionalRed = Color(0xFFD0696F)
val FunctionalRedContainer = Color(0xFF3D1A1C)      // 深色模式红色容器

// 黄色 — 警告、提醒
val FunctionalYellow = Color(0xFFD4A850)
val FunctionalYellowContainer = Color(0xFF3D3018)   // 深色模式黄色容器

// 绿色 — 成功、就绪、正常运行
val FunctionalGreen = Color(0xFF5DA868)
val FunctionalGreenContainer = Color(0xFF1A3320)     // 深色模式绿色容器

// 蓝色 — 链接、选中态、主要操作（App 唯一主色调）
val FunctionalBlue = Color(0xFF3964FE)            // 浅色/深色模式统一主色调
val FunctionalBlueLight = Color(0xFF6B8AFF)       // 深色模式（更亮，保证可读性）
val FunctionalBlueContainer = Color(0xFF1A2240)   // 深色模式蓝色容器

// ============================================================
// 三、主题色彩映射 (Theme Color Mappings)
//    深色模式与浅色模式分别映射到 Material3 色彩方案
// ============================================================

// ── 深色模式 ──
// 纯黑基底，浅灰文字
val DarkBackground = Black                        // #000000 纯黑
val DarkSurface = Color(0xFF121212)               // 接近纯黑
val DarkSurfaceVariant = Color(0xFF1E1E1E)        // 深灰
val DarkOnBackground = Color(0xFFE0E0E0)          // 浅灰 — 正文
val DarkOnSurface = Color(0xFFE0E0E0)             // 浅灰 — 正文
val DarkOnSurfaceVariant = Color(0xFF9E9E9E)      // 中浅灰 — 辅助文字

// ── 浅色模式 ──
// 纯白基底，深灰文字
val LightBackground = White                       // #FFFFFF 纯白
val LightSurface = White                          // #FFFFFF 纯白
val LightSurfaceVariant = SystemGray1             // #F2F2F7 极浅灰
val LightOnBackground = Color(0xFF1C1C1E)         // 深灰 — 正文
val LightOnSurface = Color(0xFF1C1C1E)            // 深灰 — 正文
val LightOnSurfaceVariant = Color(0xFF6E6E73)     // 中灰 — 辅助文字

// ── 语义色变量（保持向后兼容） ──
// 在深色与浅色模式下使用相同的语义色值
val ErrorRed = FunctionalRed
val SuccessGreen = FunctionalGreen
val OnPrimary = White

// ── 聊天气泡 ──
// 用户气泡
val UserBubbleLight = SystemGray2        // 浅色模式：浅灰背景
val UserBubbleDark = Color(0xFF2A2A2A)   // 深色模式：深灰背景
// 助手气泡
val AssistantBubbleLight = SystemGray1   // 浅色模式：极浅灰背景（接近白）
val AssistantBubbleDark = Color(0xFF141414) // 深色模式：深黑背景

// ── 代码块 ──
val CodeBlockBg = Color(0xFF141414)                    // 代码块背景（深色模式）
val CodeBorder = Color(0xFF3A3A3A)                     // 代码块边框
val InlineCodeBg = Color(0xFF262626)                   // 行内代码背景（深色模式）
val CodeBlockBgLight = SystemGray1                     // 代码块背景（浅色模式）
val InlineCodeBgLight = SystemGray2                    // 行内代码背景（浅色模式）

// ── 焦点指示 ──
val FocusBorder = SystemGray4

// ── 终端配色 ──
val TerminalBgDark = Color(0xFF121212)
val TerminalBgLight = Color(0xFFFAFAFA)
val TerminalTextDark = Color(0xFFE0E0E0)
val TerminalTextLight = Color(0xFF333333)
val TerminalPromptDark = Color(0xFF4CAF50)
val TerminalPromptLight = Color(0xFF2E7D32)
val TerminalErrorDark = Color(0xFFF44336)
val TerminalErrorLight = Color(0xFFD32F2F)
val TerminalWarningDark = Color(0xFFFFC107)
val TerminalWarningLight = Color(0xFFE65100)
val TerminalInputBarBgDark = Color(0xFF1E1E1E)
val TerminalInputBarBgLight = Color(0xFFFFFFFF)
val TerminalRowBgEvenDark = Color(0xFF121212)
val TerminalRowBgOddDark = Color(0xFF1A1A1A)
val TerminalRowBgEvenLight = Color(0xFFFAFAFA)
val TerminalRowBgOddLight = Color(0xFFF0F2F5)
val TerminalInputFieldBgDark = Color(0xFF2D2D2D)
val TerminalInputFieldBgLight = Color(0xFFF0F0F0)