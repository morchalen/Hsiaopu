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
            description = "根据用户的指令输出一条完整的 Android 设备 shell 命令（命令由你根据自己的经验写出，系统会在设备 shell 中直接执行，不会替你想命令），系统会真实执行并把执行结果交给你。"
        )
    )

    /** ② 描述注入：从注册表动态生成工具说明，拼入 System Prompt。
     * 注意：这里只描述机制，严禁列举具体命令或指令示例——具体命令必须由 AI 凭自身经验写出。 */
    fun buildToolPrompt(): String = buildString {
        appendLine("你是一个运行在 Android 设备上的 AI 助手，你可以通过调用系统命令来操作或查询设备。")
        appendLine()
        skills.forEachIndexed { index, skill ->
            appendLine("技能${index + 1}【${skill.name}】：${skill.description}")
            appendLine("调用方式：${skill.marker}参数]")
            appendLine()
        }
        appendLine("规则：")
        appendLine("1. 当用户要求操作或查询设备时，你的第一轮回复必须只包含 [SHELL:命令]，不要写任何总结、确认、客套或其他文字；系统执行完并返回真实结果后，你才能在后续回复中总结汇报")
        appendLine("2. 命令必须由你根据用户需求自行写出（凭你的经验写正确的设备 shell 命令。注意：命令是直接要在设备 shell 里执行的，设备上没有 adb 这个命令，所以千万不要写 adb shell 前缀），不要虚构不存在的命令")
        appendLine("3. 系统执行后会返回真实结果给你，你必须如实汇报执行结果，严禁编造\"已成功\"\"已完成\"等结论")
        appendLine("4. 若系统提示命令执行失败，如实说明失败原因")
        appendLine("5. 危险操作（重启、关机、恢复出厂设置等）需要先向用户确认")
        appendLine("6. 用中文回复用户")
        appendLine("7. 严禁在回复中输出 \"✓\"\"✗\"\"⚠️\" 开头的文本，或\"执行成功，返回：\"这类系统结果格式——那是系统执行并核实后才会生成的内容，你的第一轮回复只允许出现 [SHELL:命令]，其余一律用中文表达")
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
