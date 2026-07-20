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
import com.example.hsiaopu.data.UsageInfo
import com.example.hsiaopu.data.local.ConversationEntity
import com.example.hsiaopu.data.local.MessageEntity
import com.example.hsiaopu.data.ShellCommandBus
import com.example.hsiaopu.data.repository.ChatRepository
import com.example.hsiaopu.data.repository.ShellHistoryRepository
import com.example.hsiaopu.network.AiProviderRegistry
import com.example.hsiaopu.network.UsageStats
import com.example.hsiaopu.system.SysResult
import com.example.hsiaopu.system.SystemControlExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

//数据类，用于存储聊天界面的状态
data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),//对话的列表
    val currentConversationId: Long? = null,//当前选中的对话id
    val messages: List<ChatMessage> = emptyList(),//当前选中的对话的消息列表
    val isLoading: Boolean = false,//是否正在加载中
    val streamingContent: String = "",//流式内容
    val error: String? = null//错误信息
)

//负责管理聊天会话、消息收发、AI 服务提供商调度以及工具指令执行的核心 ViewModel。
// @Inject 就是自动使用后面的constructor进行初始化
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,//同时初始化和传递形参
    private val providerRegistry: AiProviderRegistry,
    private val settingsDataStore: SettingsDataStore,
    private val shellHistoryRepository: ShellHistoryRepository,
    private val shellCommandBus: ShellCommandBus,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 暴露给外部依赖的组件
    val dataStore: SettingsDataStore get() = settingsDataStore
    //这里的get是固定的写法，使用空格间隔一下，这个是每次访问dataStore的时候，就使用get函数，得到一个settingsDataStore对象
    val shellRepo: ShellHistoryRepository get() = shellHistoryRepository
    val cmdBus: ShellCommandBus get() = shellCommandBus

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
    // 导出功能
    // ==========================================================================

    fun exportConversationAsMarkdown(id: Long): String {
        val messages = _uiState.value.messages
        val conv = _uiState.value.conversations.find { it.id == id }
        return buildString {
            appendLine("# ${conv?.title ?: "Conversation"}")
            appendLine()
            messages.forEach { msg ->
                val role = if (msg.role == "user") "You" else "AI"
                appendLine("**$role**:")
                appendLine(msg.content)
                appendLine()
            }
        }
    }

    fun exportConversationAsJson(id: Long): String {
        val conv = _uiState.value.conversations.find { it.id == id }
        val messages = _uiState.value.messages.map { msg ->
            """{"role":"${msg.role}","content":${escapeJson(msg.content)},"timestamp":${msg.timestamp}}"""
        }
        return """{"title":"${conv?.title ?: ""}","messages":[${messages.joinToString(",")}]}"""
    }

    private fun escapeJson(s: String): String {
        return "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
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
            val fetchedModels = providerRegistry.fetchModels(currentSettings)
            _models.value = fetchedModels
        }
    }

    // ==========================================================================
    // 核心发送逻辑与工具调用
    // ==========================================================================

    fun sendMessage(content: String) {
        val currentSettings = _settings.value
        val convId = _uiState.value.currentConversationId

        if (_uiState.value.isLoading) return

        if (currentSettings.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请在设置中填写 API Key") }
            return
        }

        // if (!_uiState.value.isOnline) {
        //     _uiState.update { it.copy(error = "网络不可用，消息将在联网后发送") }
        // }

        // 若当前无激活对话，则自动创建并发送
        if (convId == null) {
            viewModelScope.launch {
                val id = repository.createConversation(getConversationTitle(content))
                _uiState.update { it.copy(currentConversationId = id) }
                doSendWithTools(id, content, currentSettings)
            }
            return
        }

        doSendWithTools(convId, content, currentSettings)
    }

    /**
     * 处理消息发送与工具调用的核心业务流：
     * 1. 存储用户输入。
     * 2. 构建注入了工具定义的 System Prompt。
     * 3. 请求 AI 并流式处理返回内容。
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
        ) }

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

                var fullContent = ""
                providerRegistry.sendMessageStream(
                    messages,
                    settings
                ).collect { chunk ->
                    fullContent += chunk
                    _uiState.update { it.copy(streamingContent = fullContent) }
                }

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
                        providerRegistry.sendMessageStream(
                            secondMessages,
                            settings
                        ).collect { chunk ->
                            secondContent += chunk
                            _uiState.update {
                                it.copy(streamingContent = "$processedContent\n\n$toolResultText\n\n$secondContent")
                            }
                        }
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

    private fun buildToolSystemPrompt(userPrompt: String): String {
        val tools = """
        你是一个运行在 Android 设备上的 AI 助手。你可以通过 Shizuku 控制设备。

        你可以输出以下格式的标记来执行系统命令（每行一个，放在回复末尾）：
        [TOOL:action_name:param1=value1,param2=value2]

        可用工具列表：

        === 开关控制 ===
        enable_wifi, disable_wifi, enable_bluetooth, disable_bluetooth, 
        enable_hotspot:ssid=xxx,password=xxx, disable_hotspot,
        enable_mobile_data, disable_mobile_data,
        enable_airplane_mode, disable_airplane_mode,
        enable_nfc, disable_nfc

        === 调节 ===
        set_brightness:level=128 (1-255)
        set_volume:stream=music,level=8 (stream: music/ring/alarm, level: 0-15)

        === 查询 ===
        get_cpu_info, get_memory_info, get_uptime, get_kernel_version,
        get_battery_info, get_cpu_temp, get_ip_address, get_wifi_networks,
        get_disk_usage, get_top_processes, get_network_interfaces,
        get_sensor_list, get_display_info, get_installed_packages,
        get_running_services, get_routing_table, get_dns_info, get_mount_info,
        ping_test:host=8.8.8.8,count=4

        get_prop:key=ro.build.version.release
        get_volume:stream=music
        get_brightness

        === 应用管理 ===
        force_stop:package=com.example.app
        clear_data:package=com.example.app
        uninstall:package=com.example.app

        === 文件操作 ===
        list_files:path=/sdcard
        read_file:path=/sdcard/test.txt
        delete_file:path=/sdcard/test.txt

        === 截屏 ===
        screenshot:path=/sdcard/Pictures/screenshot.png

        === 系统 ===
        reboot, reboot_recovery, reboot_bootloader, shutdown

        规则：
        1. 当用户要求操作设备时，先输出 [TOOL:xxx] 执行命令，然后根据结果回复用户
        2. 查询类命令直接输出结果即可
        3. 危险操作（重启、关机）需要先向用户确认
        4. 如果没有匹配的工具，告诉用户暂不支持
        5. 用中文回复用户
        """.trimIndent()

        return if (userPrompt.isNotBlank()) "$userPrompt\n\n$tools" else tools
    }

    private suspend fun executeToolsInContent(content: String): Pair<String, List<SysResult>> {
        val toolRegex = Regex("""\[TOOL:([a-z_]+)(?::([^\]]*))?\]""")
        val matches = toolRegex.findAll(content)
        
        if (!matches.any()) return Pair(content, emptyList())

        val results = mutableListOf<SysResult>()
        var processed = content

        for (match in matches) {
            val action = match.groupValues[1]
            val paramsStr = match.groupValues.getOrNull(2) ?: ""
            val params = parseParams(paramsStr)

            val result = executeToolAction(action, params)
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

    private fun parseParams(paramsStr: String): Map<String, String> {
        if (paramsStr.isBlank()) return emptyMap()
        return paramsStr.split(",").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            } else null
        }.toMap()
    }

    /** 工具标识到底层 Shell 指令的映射。 */
    private fun getShellCommandForAction(action: String, params: Map<String, String>): String? {
        return when (action) {
            "enable_wifi" -> "svc wifi enable"
            "disable_wifi" -> "svc wifi disable"
            "enable_bluetooth" -> "svc bluetooth enable"
            "disable_bluetooth" -> "svc bluetooth disable"
            "enable_hotspot" -> {
                val ssid = params["ssid"] ?: "Hsiaopu"
                val password = params["password"] ?: "12345678"
                "cmd wifi start-softap \"$ssid\" wpa2-psk \"$password\""
            }
            "disable_hotspot" -> "cmd wifi stop-softap"
            "enable_mobile_data" -> "svc data enable"
            "disable_mobile_data" -> "svc data disable"
            "enable_airplane_mode" -> "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE"
            "disable_airplane_mode" -> "settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE"
            "enable_nfc" -> "svc nfc enable"
            "disable_nfc" -> "svc nfc disable"

            "set_brightness" -> "settings put system screen_brightness ${params["level"] ?: "128"}"
            "get_brightness" -> "settings get system screen_brightness"
            "set_volume" -> {
                val stream = params["stream"] ?: "music"
                val streamCode = when (stream) { "ring" -> 2; "alarm" -> 4; else -> 3 }
                "media volume --stream $streamCode --set ${params["level"] ?: "8"}"
            }
            "get_volume" -> {
                val stream = params["stream"] ?: "music"
                val streamCode = when (stream) { "ring" -> 2; "alarm" -> 4; else -> 3 }
                "media volume --stream $streamCode --get"
            }

            "get_cpu_info" -> "cat /proc/cpuinfo | head -40"
            "get_memory_info" -> "cat /proc/meminfo | head -20"
            "get_uptime" -> "cat /proc/uptime"
            "get_kernel_version" -> "uname -a"
            "get_battery_info" -> "dumpsys battery"
            "get_cpu_temp" -> "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null || echo 'N/A'"
            "get_ip_address" -> "ip addr show wlan0 2>/dev/null | grep 'inet ' || ip addr show eth0 2>/dev/null | grep 'inet '"
            "get_wifi_networks" -> "dumpsys wifi | grep 'SSID:' | head -10"
            "get_disk_usage" -> "df -h"
            "get_top_processes" -> "ps -A -o PID,USER,NAME | tail -20"
            "get_network_interfaces" -> "ip addr show"
            "get_sensor_list" -> "dumpsys sensorservice | grep -A 1 'Sensor List'"
            "get_display_info" -> "dumpsys window displays | head -30"
            "get_installed_packages" -> "pm list packages | tail -30"
            "get_running_services" -> "dumpsys activity services | grep 'ServiceRecord' | tail -20"
            "get_routing_table" -> "ip route show"
            "get_dns_info" -> "getprop net.dns1 && getprop net.dns2"
            "get_mount_info" -> "mount | grep -v '^rootfs'"
            "get_prop" -> "getprop ${params["key"] ?: ""}"
            "ping_test" -> "ping -c ${params["count"] ?: "4"} -W 2 ${params["host"] ?: "8.8.8.8"}"

            "force_stop" -> "am force-stop ${params["package"] ?: ""}"
            "clear_data" -> "pm clear ${params["package"] ?: ""}"
            "uninstall" -> "pm uninstall ${params["package"] ?: ""}"

            "list_files" -> "ls -lah ${params["path"] ?: "/sdcard"}"
            "read_file" -> "cat ${params["path"] ?: ""} | head -50"
            "delete_file" -> "rm -f ${params["path"] ?: ""}"

            "screenshot" -> "screencap -p ${params["path"] ?: "/sdcard/Pictures/screenshot_hsiaopu.png"}"

            "reboot" -> "reboot"
            "reboot_recovery" -> "reboot recovery"
            "reboot_bootloader" -> "reboot bootloader"
            "shutdown" -> "reboot -p"

            else -> null
        }
    }

    /**
     * 将封装好的命令通过总线分发，并挂起等待 Shell 环境执行完毕。
     * 最大等待时长 30 秒。
     */
    private suspend fun executeToolAction(action: String, params: Map<String, String>): SysResult {
        val shellCommand = getShellCommandForAction(action, params)
        if (shellCommand == null) {
            return SysResult(action, false, "不支持的操作: $action", "", false)
        }

        shellCommandBus.sendCommand(shellCommand)

        return try {
            val shellResult = kotlinx.coroutines.withTimeout(30000) {
                shellCommandBus.results.first { it.command == shellCommand }
            }
            SysResult(
                action,
                shellResult.isSuccess,
                shellResult.stdout.ifEmpty { shellResult.stderr },
                shellResult.stdout,
                false
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            SysResult(action, false, "Shell 执行超时，请确保 Shell 页面已打开", "", false)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getConversationTitle(content: String): String {
        val cleaned = content.replace("\n", " ").trim()
        return if (cleaned.length > 30) cleaned.take(30) + "..." else cleaned
    }
}