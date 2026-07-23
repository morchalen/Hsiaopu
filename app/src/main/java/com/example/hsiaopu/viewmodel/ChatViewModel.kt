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

    // Shell 命令执行结果
    data class SysResult(
        val action: String,
        val success: Boolean,
        val message: String,
        val output: String
    )
    // 暴露给外部依赖的组件
    val dataStore: SettingsDataStore get() = settingsDataStore
    //这里的get是固定的写法，使用空格间隔一下，这个是每次访问dataStore的时候，就使用get函数，得到一个settingsDataStore对象
    val shellRepo: ShellHistoryRepository get() = shellHistoryRepository

    /** 获取当前应用设置的同步快照 */
    fun getCurrentSettings(): AppSettings = _settings.value

    private val _uiState = MutableStateFlow(ChatUiState())//热流
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()//私有变公有

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
        viewModelScope.launch {
            _uiState.update { it.copy(currentConversationId = id, error = null) }
            repository.getMessagesByConversation(id).collect { entities ->
                val messages = entities.map { ChatMessage(it.role, it.content, it.timestamp) }
                _uiState.update { it.copy(messages = messages) }
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
        val userMsg = ChatMessage(role = "user", content = content)
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
                content = content
            ))
        }

        viewModelScope.launch {
            try {
                // 1. 构建注入了支持 Shizuku 工具调用指令的 System Prompt
                val systemPrompt = buildToolSystemPrompt(settings.systemPrompt)
                val messages = buildList {
                    add(ChatMessage(role = "system", content = systemPrompt))
                    addAll(_uiState.value.messages)
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
                val fullContent = chatClient.sendMessage(messages, settings)

                // 3. 解析并执行工具指令
                val (processedContent, toolResults) = executeToolsInContent(fullContent)

                val finalContent: String
                if (toolResults.isNotEmpty()) {
                    // 4. 构建工具执行结果上下文，发起第二轮总结请求
                    val toolResultText = buildString {
                        appendLine()
                        appendLine("---")
                        appendLine("系统执行结果：")
                        toolResults.forEach { r ->
                            val status = if (r.success) "✓" else "✗"
                            appendLine("$status ${r.action}: ${r.message}")
                        }
                        appendLine("---")
                    }

                    val secondMessages = messages + listOf(
                        ChatMessage(role = "assistant", content = processedContent),
                        ChatMessage(role = "user", content = "请根据以上执行结果，用中文向我汇报。")
                    )

                    var secondContent = ""
                    try {
                        // [流式写法] 暂不用，保留参考
                        // chatClient.sendMessageStream(
                        //     secondMessages,
                        //     settings
                        // ).collect { chunk ->
                        //     secondContent += chunk
                        //     _uiState.update {
                        //         it.copy(streamingContent = "$processedContent\n\n$toolResultText\n\n$secondContent")
                        //     }
                        // }
                        secondContent = chatClient.sendMessage(secondMessages, settings)
                    } catch (_: Exception) {
                        // 次轮网络等异常时降级，保留第一轮结果及原始工具执行态
                    }

                    finalContent = if (secondContent.isNotBlank()) {
                        "$processedContent\n\n$toolResultText\n\n$secondContent"
                    } else {
                        "$processedContent\n\n$toolResultText"
                    }
                } else {
                    finalContent = processedContent
                }

                // 5. 持久化最终结果
                repository.insertMessage(MessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = finalContent
                ))
                repository.updateConversationTitle(convId, getConversationTitle(finalContent))

                val assistantMsg = ChatMessage(role = "assistant", content = finalContent)
                _uiState.update { it.copy(
                    messages = it.messages + assistantMsg,
                    isLoading = false,
                    streamingContent = ""
                ) }
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
    private fun buildToolSystemPrompt(userPrompt: String): String {
        val tools = """
        你是一个运行在 Android 设备上的 AI 助手。你可以通过 Shizuku 执行 Shell 命令来控制设备。

        当你需要执行 Shell 命令时，在回复中包含以下格式的标记（每行一个，放在回复末尾）：
        [SHELL:具体的 Shell 命令]

        例如：用户说"打开 WiFi"，你输出 [SHELL:svc wifi enable]
        例如：用户说"查看电量"，你输出 [SHELL:dumpsys battery]
        例如：用户说"重启手机"，你输出 [SHELL:reboot]

        规则：
        1. 当用户要求操作设备时，先输出 [SHELL:xxx] 执行命令，然后根据执行结果回复用户
        2. 查询类命令直接输出 [SHELL:xxx] 并获取结果即可
        3. 危险操作（重启、关机等）需要先向用户确认
        4. 如果你不确定具体的 Shell 命令，告诉用户暂不支持
        5. 用中文回复用户
        """.trimIndent()

        return if (userPrompt.isNotBlank()) "$userPrompt\n\n$tools" else tools
    }

    // 匹配ai回复的格式
    private suspend fun executeToolsInContent(content: String): Pair<String, List<SysResult>> {
        val toolRegex = Regex("""\[SHELL:([^\]]+)\]""")
        val matches = toolRegex.findAll(content)
        
        if (!matches.any()) return Pair(content, emptyList())

        val results = mutableListOf<SysResult>()
        var processed = content

        for (match in matches) {
            val shellCommand = match.groupValues[1].trim()
            val result = executeToolAction(shellCommand)
            results.add(result)

            val replacement = buildString {
                val status = if (result.success) "✓" else "✗"
                appendLine()
                appendLine("---")
                appendLine("**$status ${result.action}**")
                appendLine("${result.message}")
                if (result.output.isNotBlank() && result.output.length < 500) {
                    appendLine("```")
                    appendLine(result.output.take(800))
                    appendLine("```")
                }
                appendLine("---")
            }
            processed = processed.replace(match.value, replacement)
        }

        return Pair(processed, results)
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
    private suspend fun executeToolAction(shellCommand: String): SysResult {
        return try {
            val shellResult = kotlinx.coroutines.withTimeout(30000) {
                ShellExecutor.execute(shellCommand).first()
            }
            // 写入 ShellHistory 数据库，Shell 页面从 Room Flow 读取后自动显示
            shellHistoryRepository.insertHistory(shellResult)
            SysResult(
                shellCommand,
                shellResult.isSuccess,
                shellResult.stdout.ifEmpty { shellResult.stderr },
                shellResult.stdout
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            SysResult(shellCommand, false, "Shell 执行超时", "")
        } catch (e: Exception) {
            SysResult(shellCommand, false, "Shell 执行失败: ${e.message}", "")
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