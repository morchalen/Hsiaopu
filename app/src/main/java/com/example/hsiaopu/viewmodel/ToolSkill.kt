package com.example.hsiaopu.viewmodel

/**
 * ==================== Skill 体系 ====================
 * 把 AI 的每个能力封装成一张"技能卡"（ToolSkill）。
 * 一个技能 = 三要素：
 *   ① 注册表：这个项目有哪些技能（skills 列表）
 *   ② 描述注入：把技能说明动态拼进 System Prompt，告诉 AI 会什么、怎么调用
 *   ③ 执行器：解析 AI 输出的调用标记 → 真正执行 → 结果替换回回复
 *
 * 想给 AI 加新本事，只需：
 *   1. 在 skills 列表加一行技能卡
 *   2. 在 ChatViewModel 里提供对应的执行器函数
 * 无需改解析逻辑和 prompt 文案。
 * ====================================================
 */

/** 一次技能调用的执行结果（原 ChatViewModel.SysResult 迁出） */
data class SkillResult(
    val action: String,    // 被调用的动作，如 "svc bluetooth disable"
    val success: Boolean,  // 是否执行成功
    val message: String,   // 失败原因 / 提示信息
    val output: String     // 真实输出
)

/** 技能卡：AI 的一个本事 */
data class ToolSkill(
    val name: String,                                // 技能名（给人看的）
    val marker: String,                              // AI 调用标记前缀，如 "[SHELL:"
    val description: String                          // 技能说明（注入 System Prompt 给 AI 看）
)

/** 技能注册表：项目所有技能的清单 */
object ToolSkillRegistry {

    /** ① 注册表：目前只有"设备命令"一个技能。加新技能在这里加一行即可 */
    val skills: List<ToolSkill> = listOf(
        ToolSkill(
            name = "设备命令",
            marker = "[SHELL:",
            description = "执行 Shell 命令控制设备，如开关 WiFi/蓝牙、查电量、调音量、重启等。参数为完整 Shell 命令。"
        ),
        ToolSkill(
            name = "结构化系统 Skill",
            marker = "[SKILL:",
            description = "调用已经封装好的 Android 系统能力，格式为 [SKILL:skill_name:{JSON参数}]。支持 get_battery_info、get_memory_info、set_volume、set_brightness、open_settings。"
        )
        // 示例：加"打开应用"技能只需加一行
        // ToolSkill(
        //     name = "打开应用",
        //     marker = "[APP:",
        //     description = "启动指定的应用，参数为应用名称，如 微信/设置/相机"
        // )
    )

    /** ② 描述注入：从注册表动态生成工具说明，拼入 System Prompt */
    fun buildToolPrompt(): String = buildString {
        appendLine("你是一个运行在 Android 设备上的 AI 助手，你可以通过以下技能控制设备：")
        appendLine()
        skills.forEachIndexed { index, skill ->
            appendLine("技能${index + 1}【${skill.name}】：${skill.description}")
            appendLine("调用方式：${skill.marker}参数]")
            appendLine()
        }
        appendLine("规则：")
        appendLine("1. 当用户要求操作设备时，先输出技能调用标记，系统会真实执行，然后根据执行结果回复用户")
        appendLine("2. 查询类操作直接输出调用标记获取结果即可")
        appendLine("3. 危险操作（重启、关机等）需要先向用户确认")
        appendLine("4. 如果你不确定具体的调用方式，告诉用户暂不支持")
        appendLine("5. 严禁编造执行结果：你无法自行执行，只有输出调用标记后系统才会真正执行；未输出标记时绝对不要声称\"已执行\"，也不要编造命令输出或执行结果")
        appendLine("6. 若命令执行失败或无返回输出，如实报告错误，不要假装成功")
        appendLine("7. 用中文回复用户")
        appendLine("8. 优先使用结构化系统 Skill；只有没有合适 Skill 时，才使用 Shell 命令")
        appendLine()
        appendLine("结构化 Skill 示例：")
        appendLine("- 查询电池：[SKILL:get_battery_info:{}]")
        appendLine("- 查询内存：[SKILL:get_memory_info:{}]")
        appendLine("- 设置媒体音量：[SKILL:set_volume:{\"stream\":\"music\",\"level\":5}]")
        appendLine("- 设置亮度：[SKILL:set_brightness:{\"level\":120}]")
        appendLine("- 打开系统设置：[SKILL:open_settings:{}]")
    }

    /**
     * ③ 执行器分发：解析 AI 回复中的所有技能调用标记并执行。
     * @param content  AI 的回复原文
     * @param executor 技能执行器（由 ChatViewModel 提供，因为执行器依赖 Shell 等业务组件）
     * @return Pair(标记替换为真实结果后的文本, 所有执行结果列表)
     */
    suspend fun executeAll(
        content: String,
        executor: suspend (skill: ToolSkill, param: String) -> SkillResult
    ): Pair<String, List<SkillResult>> {
        val results = mutableListOf<SkillResult>()
        var processed = content
        skills.forEach { skill ->
            val regex = Regex(Regex.escape(skill.marker) + "([^]]+)]")
            regex.findAll(processed).forEach { match ->
                val param = match.groupValues[1].trim()
                val result = executor(skill, param)
                results.add(result)
                // 把调用标记替换为"已核实"的真实结果展示文本
                processed = processed.replace(match.value, formatResult(result))
            }
        }
        return processed to results
    }

    /** 执行结果的展示格式（核实状态：成功有输出 / 失败 / 成功但无输出） */
    fun formatResult(result: SkillResult): String = buildString {
        appendLine()
        appendLine("---")
        when {
            result.success && result.output.isNotBlank() -> {
                appendLine("**✓ ${result.action}**（执行成功，返回：）")
                appendLine(result.output.take(800))
            }
            !result.success -> {
                appendLine("**✗ ${result.action}**（执行失败：${result.message}）")
            }
            else -> {
                appendLine("**⚠️ ${result.action}**（已执行但无任何返回输出，无法核实）")
            }
        }
        appendLine("---")
    }
}
