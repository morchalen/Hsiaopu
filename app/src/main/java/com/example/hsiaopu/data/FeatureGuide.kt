package com.example.hsiaopu.data

/**
 * 懒加载功能引导 — 用户首次进入某个功能区域时弹出提示，看过即永久消失
 */
enum class FeatureGuideKey(val key: String) {
    /** 对话页 — 快速指令胶囊 */
    HOME_QUICK_COMMANDS("home_quick_commands"),
    /** 对话页 — 长按消息复制 */
    HOME_LONG_PRESS("home_long_press"),
    /** 对话页 — 导出对话 */
    HOME_EXPORT("home_export"),
    /** Shell 终端 — 预定义命令 */
    SHELL_PRESET_COMMANDS("shell_preset_commands"),
    /** Shell 终端 — 命令输出复制 */
    SHELL_COPY("shell_copy"),
    /** 工具页 — 设备信息卡片 */
    TOOLS_DEVICE_CARDS("tools_device_cards"),
    /** 设置页 — 多 Provider 切换 */
    SETTINGS_PROVIDER("settings_provider"),
    /** 设置页 — Token 用量面板 */
    SETTINGS_TOKEN_USAGE("settings_token_usage"),
}