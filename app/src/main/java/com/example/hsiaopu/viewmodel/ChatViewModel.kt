package com.example.hsiaopu.viewmodel
//要看懂每一个参数是什么意思，什么类型，以及它的作用
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import com.example.hsiaopu.data.SettingsDataStore
import com.example.hsiaopu.data.ThemeSettings
import com.example.hsiaopu.data.local.ConversationEntity
import com.example.hsiaopu.data.local.MessageEntity
import com.example.hsiaopu.data.repository.ChatRepository
import com.example.hsiaopu.data.repository.ShellHistoryRepository
import com.example.hsiaopu.network.ChatClient
import com.example.hsiaopu.system.ShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.json.JSONObject
import javax.inject.Inject

//负责管理聊天会话、消息收发、AI 服务提供商调度以及工具指令执行的核心 ViewModel。
// @Inject 就是自动使用后面的constructor进行初始化
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,//同时初始化和传递形参
    val chatClient: ChatClient,
    private val settingsDataStore: SettingsDataStore,
    private val shellHistoryRepository: ShellHistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 数据类，用于存储聊天界面的状态
    data class ChatUiState(
        val conversations: List<ConversationEntity> = emptyList(),//对话的列表
        val currentConversationId: Long? = null,//当前选中的对话id
        val messages: List<ChatMessage> = emptyList(),//当前选中的对话的消息列表
        val isLoading: Boolean = false,//是否正在加载中
        val streamingContent: String = "",//流式内容
        val error: String? = null//错误信息
    )

    // 技能执行结果：类型定义见 ToolSkill.kt 的 SkillResult（原 SysResult 已迁移至 Skill 体系）
    // 暴露给外部依赖的组件
    val dataStore: SettingsDataStore get() = settingsDataStore
    //这里的get是固定的写法，使用空格间隔一下，这个是每次访问dataStore的时候，就使用get函数，得到一个settingsDataStore对象
    val shellRepo: ShellHistoryRepository get() = shellHistoryRepository

    /** 获取当前应用设置的同步快照 */
    fun getCurrentSettings(): AppSettings = _settings.value

    private val _uiState = MutableStateFlow(ChatUiState())//热流
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()//私有变公有

    /** 当前对话的消息监听协程，切换对话时取消，避免旧对话 Flow 覆盖 UI */
    private var messagesCollectJob: Job? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _themeSettings = MutableStateFlow(ThemeSettings())//热流，ThemeSettings更新就重载
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()//私有变公有

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    init {
        viewModelScope.launch {//启动一个协程，持续监听 DataStore 中的应用设置变化，每次变化都更新 ViewModel 中的 _settings 状态
            settingsDataStore.settingsFlow.collect { _settings.value = it }
        }

        viewModelScope.launch {//启动一个协程，持续监听 DataStore 中的主题设置变化，每次变化都更新 ViewModel 中的 _themeSettings 状态
            settingsDataStore.themeSettingsFlow.collect { _themeSettings.value = it }
        }

        viewModelScope.launch {//启动一个协程，持续监听数据库中所有对话的变化，每次变化都更新 ViewModel 中的 _uiState 状态
            repository.getAllConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
    }

    // ==========================================================================
    // 对话管理 (Conversation Management)
    // ==========================================================================

    fun createNewConversation() {
        viewModelScope.launch {
            val id = repository.createConversation()
            selectConversation(id)
        }
    }

    fun selectConversation(id: Long) {
        // 取消上一个对话的消息监听，避免旧对话新消息覆盖当前 UI
        messagesCollectJob?.cancel()
        _uiState.update { it.copy(currentConversationId = id, error = null) }
        messagesCollectJob = viewModelScope.launch {
            repository.getMessagesByConversation(id).collect { entities ->
                // 仅当当前选中的对话是 id 时才更新，防止旧 collect 残留竞态
                if (_uiState.value.currentConversationId == id) {
                    val messages = entities.map { ChatMessage(it.role, it.content, it.timestamp) }
                    _uiState.update { it.copy(messages = messages) }
                }
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) {
                val next = _uiState.value.conversations.firstOrNull { it.id != id }
                if (next != null) {
                    selectConversation(next.id)
                } else {
                    _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
                }
            }
        }
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch { repository.updateConversationTitle(id, title) }
    }



    // ==========================================================================
    // 设置与主题更新
    // ==========================================================================

    fun updateApiKey(key: String) {
        _settings.update { it.copy(apiKey = key) }
        viewModelScope.launch { settingsDataStore.updateApiKey(key) }
    }
    fun updateApiEndpoint(endpoint: String) {
        _settings.update { it.copy(apiEndpoint = endpoint) }
        viewModelScope.launch { settingsDataStore.updateApiEndpoint(endpoint) }
    }
    fun updateModelName(model: String) {
        _settings.update { it.copy(modelName = model) }
        viewModelScope.launch { settingsDataStore.updateModelName(model) }
    }
    fun updateSystemPrompt(prompt: String) {
        _settings.update { it.copy(systemPrompt = prompt) }
        viewModelScope.launch { settingsDataStore.updateSystemPrompt(prompt) }
    }
    fun updateTemperature(temp: Double) {
        _settings.update { it.copy(temperature = temp) }
        viewModelScope.launch { settingsDataStore.updateTemperature(temp) }
    }
    fun updateMaxTokens(tokens: Int) {
        _settings.update { it.copy(maxTokens = tokens) }
        viewModelScope.launch { settingsDataStore.updateMaxTokens(tokens) }
    }
    fun updateThemeMode(mode: String) {
        _themeSettings.update { it.copy(themeMode = mode) }
        viewModelScope.launch { settingsDataStore.updateThemeMode(mode) }
    }
    fun updateFontScale(scale: Int) {
        _themeSettings.update { it.copy(fontScale = scale) }
        viewModelScope.launch { settingsDataStore.updateFontScale(scale) }
    }
    fun refreshModels() {
        viewModelScope.launch {
            val currentSettings = _settings.value
            val fetchedModels = chatClient.fetchModels(currentSettings)
            _models.value = fetchedModels
        }
    }

    // 核心发送逻辑与工具调用
    fun sendMessage(content: String) {
        // 1️⃣ 获取当前设置快照（API Key、模型名等）
        val currentSettings = _settings.value
        // 2️⃣ 获取当前选中的对话 ID
        val convId = _uiState.value.currentConversationId

        // 3️⃣ 防重复：如果正在加载中（AI 正在回复），忽略本次点击
        if (_uiState.value.isLoading) return

        // 4️⃣ 校验：API Key 没填 → 显示错误，不发送
        if (currentSettings.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请在设置中填写 API Key") }
            return
        }

        // 5️⃣ 核心分支：有没有当前对话？
        if (convId == null) {
            // 🔹 没有 → 自动创建一个新对话
            viewModelScope.launch {
                // 创建对话，标题从用户输入内容截取（最多30字符）
                val id = repository.createConversation(getConversationTitle(content))
                // 更新 UI 状态：当前对话 ID 指向这个新创建的
                _uiState.update { it.copy(currentConversationId = id) }
                // 执行真正的发送逻辑
                doSendWithTools(id, content, currentSettings)
            }
            return
        }

        // 🔹 有 → 直接在当前对话下发送
        doSendWithTools(convId, content, currentSettings)
    }

    /**
     * 处理消息发送与工具调用的核心业务流：
     * 1. 存储用户输入。
     * 2. 构建注入了工具定义的 System Prompt。
     * 3. 请求 AI 并处理返回内容。
     * 4. 正则解析 AI 返回内容中的 [TOOL:...] 标记并执行底层 Shell 命令。
     * 5. 若有工具执行，发起第二轮 AI 总结请求。
     * 6. 更新并持久化最终完整状态。
     */
    private fun doSendWithTools(convId: Long, content: String, settings: AppSettings) {
        // 统一时间戳：内存与数据库使用同一个值，避免 UI 与 Flow 竞态导致重复显示
        val userTimestamp = System.currentTimeMillis()
        val userMsg = ChatMessage(role = "user", content = content, timestamp = userTimestamp)
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            isLoading = true,
            streamingContent = "",
            error = null
        )}

        viewModelScope.launch {
            repository.insertMessage(MessageEntity(
                conversationId = convId,
                role = "user",
                content = content,
                timestamp = userTimestamp
            ))
        }

        viewModelScope.launch {
            try {
                // 1. 构建注入了支持 Shizuku 工具调用指令的 System Prompt
                val systemPrompt = buildToolSystemPrompt(settings.systemPrompt)
                // 给 AI 的上下文剥离调试流程块，只保留正式回复，防止 AI 模仿【调试流程│开始】等标记
                val messages = buildList {
                    add(ChatMessage(role = "system", content = systemPrompt))
                    addAll(_uiState.value.messages.map { msg ->
                        msg.copy(content = stripDebugFlow(msg.content))
                    })
                }

                // [流式写法] 暂不用，保留参考
                // var fullContent = ""
                // chatClient.sendMessageStream(
                //     messages,
                //     settings
                // ).collect { chunk ->
                //     fullContent += chunk
                //     _uiState.update { it.copy(streamingContent = fullContent) }
                // }
                val fullContent = chatClient.sendMessageSafe(messages, settings)

                // 3. 解析并执行工具指令
                val (processedContent, toolResults) = executeToolsInContent(fullContent)

                val finalContent: String
                if (toolResults.isNotEmpty()) {
                    // ===== 场景B：有命令执行 → 必须核实系统返回值 =====
                    val (summary, toolResultText) = summarizeToolResults(messages, processedContent, toolResults, settings)
                    val reply = if (summary.isNotBlank()) summary else toolResultText
                    val flowBody = buildString {
                        appendLine("① 第一轮 AI 回复（含命令标记）：")
                        appendLine(fullContent.trim())
                        appendLine()
                        appendLine("② 系统命令执行结果（已核实）：")
                        appendLine(toolResultText.trim())
                        if (summary.isNotBlank()) {
                            appendLine()
                            appendLine("③ 第二轮 AI 总结汇报：")
                            appendLine(summary.trim())
                        }
                    }
                    finalContent = wrapDebugFlow(reply, flowBody)
                } else if (detectHallucinatedExecution(fullContent)) {
                    // ===== 场景A-2：AI 声称执行了命令但没有 [SHELL:] 标记（幻觉）→ 纠正 =====
                    val hallucinationMessages = messages + listOf(
                        ChatMessage(role = "assistant", content = fullContent),
                        ChatMessage(role = "user", content = "系统检测到你提到了命令执行，但没有输出 [SHELL:命令] 标记。本应用只能通过 [SHELL:xxx] 标记真实执行命令。若确实需要执行命令，请重新回复并包含正确的 [SHELL:xxx] 标记；若无需执行命令，请删除编造的执行描述，直接给出答案。")
                    )
                    var correctedContent = ""
                    try {
                        correctedContent = chatClient.sendMessageSafe(hallucinationMessages, settings)
                    } catch (_: Exception) { }

                    // 纠正后 AI 可能输出了 [SHELL:] 标记 → 必须再次解析执行，防止命令未执行
                    val (processedCorrected, correctedToolResults) = executeToolsInContent(correctedContent)

                    val reply: String
                    val flowBody: String
                    if (correctedToolResults.isNotEmpty()) {
                        // 纠正后输出了命令标记 → 执行并二次总结
                        val (correctedSummary, correctedToolResultText) =
                            summarizeToolResults(messages, processedCorrected, correctedToolResults, settings)
                        reply = if (correctedSummary.isNotBlank()) correctedSummary else correctedToolResultText
                        flowBody = buildString {
                            appendLine("① 第一轮 AI 回复（声称执行命令但无 [SHELL:] 标记，疑似幻觉）：")
                            appendLine(fullContent.trim())
                            appendLine()
                            appendLine("⚠️ 幻觉检测：AI 声称执行了命令但未输出 [SHELL:xxx] 标记，系统未执行任何命令")
                            appendLine()
                            appendLine("② 纠正后 AI 输出的命令：")
                            appendLine(processedCorrected.trim())
                            appendLine()
                            appendLine("③ 系统命令执行结果（已核实）：")
                            appendLine(correctedToolResultText.trim())
                            if (correctedSummary.isNotBlank()) {
                                appendLine()
                                appendLine("④ 最终总结汇报：")
                                appendLine(correctedSummary.trim())
                            }
                        }
                    } else {
                        reply = if (correctedContent.isNotBlank()) correctedContent else fullContent
                        flowBody = buildString {
                            appendLine("① 第一轮 AI 回复（声称执行命令但无 [SHELL:] 标记，疑似幻觉）：")
                            appendLine(fullContent.trim())
                            appendLine()
                            appendLine("⚠️ 幻觉检测：AI 声称执行了命令但未输出 [SHELL:xxx] 标记，系统未执行任何命令")
                            if (correctedContent.isNotBlank() && correctedContent != fullContent) {
                                appendLine()
                                appendLine("② 纠正请求后 AI 回复：")
                                appendLine(correctedContent.trim())
                            }
                        }
                    }
                    finalContent = wrapDebugFlow(reply, flowBody)
                } else {
                    // ===== 场景A-1：无命令 → 一次性直接回复，不发起第二次请求 =====
                    val flowBody = buildString {
                        appendLine("① 第一轮 AI 回复：")
                        appendLine(fullContent.trim())
                        appendLine()
                        appendLine("ℹ️ 未检测到 [SHELL:] 命令标记，系统未执行任何命令")
                    }
                    finalContent = wrapDebugFlow(processedContent, flowBody)
                }

                // 5. 持久化最终结果（统一时间戳，与内存消息一致）
                val assistantTimestamp = System.currentTimeMillis()
                repository.insertMessage(MessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = finalContent,
                    timestamp = assistantTimestamp
                ))
                repository.updateConversationTitle(convId, getConversationTitle(finalContent))

                val assistantMsg = ChatMessage(role = "assistant", content = finalContent, timestamp = assistantTimestamp)
                _uiState.update { state ->
                    // 防重复：若数据库 Flow 已将该消息写入 messages，则不再追加
                    val alreadyExists = state.messages.any { it.timestamp == assistantTimestamp }
                    if (alreadyExists) {
                        state.copy(isLoading = false, streamingContent = "")
                    } else {
                        state.copy(
                            messages = state.messages + assistantMsg,
                            isLoading = false,
                            streamingContent = ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    streamingContent = "",
                    error = e.message ?: "网络错误"
                ) }
            }
        }
    }
    //让ai返回shell工具指令
    /**
     * 构建 System Prompt：技能说明由 Skill 注册表动态生成（见 ToolSkill.kt）。
     * 给 AI 加新技能时无需修改这里的文案，只需在注册表加一行。
     */
    private fun buildToolSystemPrompt(userPrompt: String): String {
        val tools = ToolSkillRegistry.buildToolPrompt()
        return if (userPrompt.isNotBlank()) "$userPrompt\n\n$tools" else tools
    }

    /**
     * 检测 AI 是否"声称执行了命令"但没有输出 [SHELL:xxx] 标记（AI 幻觉）。
     * 有标记时交由正常执行流程处理；无标记但声称执行 → 判定为幻觉，需二次纠正。
     */
    private fun detectHallucinatedExecution(content: String): Boolean {
        val hasMarkers = Regex("""\[SHELL:[^\]]+\]""").containsMatchIn(content)
        if (hasMarkers) return false
        val claims = listOf(
            "已执行命令", "执行了命令", "命令已执行", "命令执行成功", "命令执行失败",
            "我执行了", "已经执行", "执行完成", "命令返回", "执行结果", "命令输出",
            "运行结果", "已运行", "执行成功", "执行失败"
        )
        return claims.any { content.contains(it) }
    }

    /** 调试流程块的开始/结束标记，UI 端据此渲染灰色小字 */
    companion object {
        const val FLOW_START = "【调试流程│开始】"
        const val FLOW_END = "【调试流程│结束】"

        /** 兼容全角│与半角|的流程块正则，非贪婪匹配，用于剥离流程块 */
        private val FLOW_BLOCK_ANY_REGEX = Regex("""【调试流程[│|]开始】([\s\S]*?)【调试流程[│|]结束】""")

        /** 把完整调试流程与最终回复拼成一条消息：流程块在上（灰色框），正式回复紧跟其后 */
        fun wrapDebugFlow(normalReply: String, flowBody: String): String {
            val reply = normalReply.trim()
            return buildString {
                appendLine(FLOW_START)
                append(flowBody.trim())
                appendLine()
                appendLine(FLOW_END)
                if (reply.isNotBlank()) {
                    appendLine()
                    appendLine()
                    append(reply)
                }
            }
        }

        /** 剥离调试流程块，供 TTS 朗读使用（只读流程块下方的正式回复内容）。兼容半角|与全角│标记 */
        fun stripDebugFlow(content: String): String {
            var text = FLOW_BLOCK_ANY_REGEX.replace(content) { "" }
            // 清理残留的 [SHELL:xxx] 命令标记，避免 TTS 朗读命令文本
            text = text.replace(Regex("""\[SHELL:[^\]]+\]"""), "")
            // 清理单独成行的分隔线，避免 TTS 朗读特殊字符
            return text
                .lines()
                .filter { it.trim() != "---" }
                .joinToString("\n")
                .trim()
        }

        /** 二次总结请求的固定指令：要求 AI 简洁实用地如实汇报真实执行结果 */
        const val SUMMARY_PROMPT = "以上是系统实际执行的真实结果。请用中文简洁实用地汇报：用一两句话直接说明命令是否执行成功及关键结果。不要客套话、不要重复、不要解释过程，直接给结论。执行失败就简短说明原因，无法核实就如实说明，不要编造任何输出。"
    }

    /**
     * 构建"已核实"的真实执行结果文本（区分 成功有输出 / 失败 / 无输出无法核实）。
     * 干净可读的格式：既用于调试流程块，也用于二次总结失败时作为正式回复（可被朗读）。
     */
    private fun buildToolResultText(toolResults: List<SkillResult>): String = buildString {
        toolResults.forEachIndexed { i, r ->
            if (i > 0) appendLine()
            when {
                r.success && r.output.isNotBlank() -> {
                    appendLine("✓ ${r.action}：执行成功，返回：")
                    appendLine(r.output.take(800))
                }
                !r.success -> {
                    appendLine("✗ ${r.action}：执行失败，${r.message}")
                }
                else -> {
                    appendLine("⚠️ ${r.action}：已执行但无任何返回输出，无法核实")
                }
            }
        }
    }

    /**
     * @return Pair(总结文本, 工具结果文本)；总结文本为空表示总结失败（调用方应降级展示工具结果）
     */
    private suspend fun summarizeToolResults(
        messages: List<ChatMessage>,
        processedContent: String,
        toolResults: List<SkillResult>,
        settings: AppSettings
    ): Pair<String, String> {
        val toolResultText = buildToolResultText(toolResults)
        val secondMessages = messages + listOf(
            ChatMessage(role = "assistant", content = processedContent),
            ChatMessage(role = "user", content = SUMMARY_PROMPT)
        )
        var summary = ""
        try {
            summary = chatClient.sendMessageSafe(secondMessages, settings)
        } catch (_: Exception) {
            // 次轮网络等异常时降级，展示真实执行结果而不是 AI 编造内容
        }
        return summary to toolResultText
    }

    /**
     * 解析 AI 回复中的所有技能调用标记并执行（Skill 体系通用分发）。
     * 执行器由本类提供（依赖 Shell/历史库等业务组件），标记格式与替换逻辑由注册表统一管理。
     */
    private suspend fun executeToolsInContent(content: String): Pair<String, List<SkillResult>> {
        return ToolSkillRegistry.executeAll(content) { skill, param ->
            when (skill.marker) {
                "[SHELL:" -> executeToolAction(param)
                "[SKILL:" -> executeStructuredSkill(param)
                else -> SkillResult(skill.name, false, "暂不支持的 Skill: ${skill.marker}", "")
            }
        }
    }

    /**
     * 结构化 Skill 执行器。
     * 格式：[SKILL:skill_name:{JSON参数}]
     * 这比让 AI 直接写 Shell 更接近手机厂商内部的 Skill 封装：名称稳定、参数可校验、执行命令可控。
     */
    private suspend fun executeStructuredSkill(rawParam: String): SkillResult {
        val separatorIndex = rawParam.indexOf(':')
        if (separatorIndex <= 0) {
            return SkillResult(rawParam, false, "Skill 格式错误，应为 skill_name:{JSON参数}", "")
        }

        val skillName = rawParam.substring(0, separatorIndex).trim()
        val jsonText = rawParam.substring(separatorIndex + 1).trim().ifBlank { "{}" }
        val params = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return SkillResult(skillName, false, "JSON 参数解析失败: ${e.message}", "")
        }

        val shellCommand = when (skillName) {
            "get_battery_info" -> "dumpsys battery"
            "get_memory_info" -> "cat /proc/meminfo | head -20"
            "open_settings" -> "am start -a android.settings.SETTINGS"
            "set_volume" -> buildSetVolumeCommand(params)
            "set_brightness" -> buildSetBrightnessCommand(params)
            else -> null
        } ?: return SkillResult(skillName, false, "未知或参数非法的 Skill: $skillName", "")

        val result = executeToolAction(shellCommand)
        return result.copy(action = "$skillName -> $shellCommand")
    }

    private fun buildSetVolumeCommand(params: JSONObject): String? {
        val streamName = params.optString("stream", "music")
        val streamCode = when (streamName) {
            "ring" -> 2
            "music" -> 3
            "alarm" -> 4
            "notification" -> 5
            else -> return null
        }
        val level = params.optInt("level", -1).takeIf { it in 0..15 } ?: return null
        return "media volume --stream $streamCode --set $level"
    }

    private fun buildSetBrightnessCommand(params: JSONObject): String? {
        val level = params.optInt("level", -1).takeIf { it in 1..255 } ?: return null
        return "settings put system screen_brightness $level"
    }
    
    // [暂不用] 解析 shell 工具指令参数（当前使用 [SHELL:原始命令] 格式，无需解析参数）
    // private fun parseParams(paramsStr: String): Map<String, String> {
    //     if (paramsStr.isBlank()) return emptyMap()
    //     return paramsStr.split(",").mapNotNull { part ->
    //         val eq = part.indexOf('=')
    //         if (eq > 0) {
    //             part.substring(0, eq).trim() to part.substring(eq + 1).trim()
    //         } else null
    //     }.toMap()
    // }

    // [暂不用] 工具标识到 Shell 指令的映射（当前 AI 直接输出原始 Shell 命令，无需映射）
    // private fun getShellCommandForAction(action: String, params: Map<String, String>): String? {
    //     return when (action) {
    //         "enable_wifi" -> "svc wifi enable"
    //         "disable_wifi" -> "svc wifi disable"
    //         ...
    //     }
    // }

    // 本地执行 shell 命令，同时将结果写入 ShellHistory 数据库供 Shell 页面显示
    private suspend fun executeToolAction(shellCommand: String): SkillResult {
        return try {
            val shellResult = kotlinx.coroutines.withTimeout(30000) {
                ShellExecutor.execute(shellCommand).first()
            }
            // 写入 ShellHistory 数据库，Shell 页面从 Room Flow 读取后自动显示
            shellHistoryRepository.insertHistory(shellResult)
            SkillResult(
                shellCommand,
                shellResult.isSuccess,
                shellResult.stdout.ifEmpty { shellResult.stderr },
                shellResult.stdout
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            SkillResult(shellCommand, false, "Shell 执行超时", "")
        } catch (e: Exception) {
            SkillResult(shellCommand, false, "Shell 执行失败: ${e.message}", "")
        }
    }

    // 清除错误信息
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // 从对话内容中提取标题
    private fun getConversationTitle(content: String): String {
        val cleaned = content.replace("\n", " ").trim()
        return if (cleaned.length > 10) cleaned.take(10) + "..." else cleaned
    }
}
