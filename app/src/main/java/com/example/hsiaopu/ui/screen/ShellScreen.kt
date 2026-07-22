package com.example.hsiaopu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hsiaopu.system.ShellExecutor
import com.example.hsiaopu.system.ShellResult
import com.example.hsiaopu.system.ShizukuHelper
import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import com.example.hsiaopu.data.SettingsDataStore
import com.example.hsiaopu.data.ShellCommandBus
import com.example.hsiaopu.data.repository.ShellHistoryRepository
import com.example.hsiaopu.network.ChatClient
import com.example.hsiaopu.ui.theme.TerminalBgDark
import com.example.hsiaopu.ui.theme.TerminalBgLight
import com.example.hsiaopu.ui.theme.TerminalErrorDark
import com.example.hsiaopu.ui.theme.TerminalErrorLight
import com.example.hsiaopu.ui.theme.TerminalInputBarBgDark
import com.example.hsiaopu.ui.theme.TerminalInputBarBgLight
import com.example.hsiaopu.ui.theme.TerminalInputFieldBgDark
import com.example.hsiaopu.ui.theme.TerminalInputFieldBgLight
import com.example.hsiaopu.ui.theme.TerminalPromptDark
import com.example.hsiaopu.ui.theme.TerminalPromptLight
import com.example.hsiaopu.ui.theme.TerminalRowBgEvenDark
import com.example.hsiaopu.ui.theme.TerminalRowBgEvenLight
import com.example.hsiaopu.ui.theme.TerminalRowBgOddDark
import com.example.hsiaopu.ui.theme.TerminalRowBgOddLight
import com.example.hsiaopu.ui.theme.TerminalTextDark
import com.example.hsiaopu.ui.theme.TerminalTextLight
import com.example.hsiaopu.ui.theme.TerminalWarningDark
import com.example.hsiaopu.ui.theme.TerminalWarningLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    settingsDataStore: SettingsDataStore,// 设置数据存储
    shellHistoryRepository: ShellHistoryRepository,// 命令历史记录仓库
    shellCommandBus: ShellCommandBus,// 命令总线
    chatClient: ChatClient,// 聊天客户端
    appSettings: AppSettings// 应用设置
) {
    val context = LocalContext.current // 获取当前上下文，用来指定当前页面而不是其他页面，context：这个 App 当前所有状态信息
    val scope = rememberCoroutineScope() // 协程作用域
    val keyboardController = LocalSoftwareKeyboardController.current // 软键盘控制器
    val focusRequester = remember { FocusRequester() } // 输入框焦点请求

    val isDark = isSystemInDarkTheme()
    // 颜色全部来自主题 Color.kt，禁止硬编码
    val terminalBg = if (isDark) TerminalBgDark else TerminalBgLight
    val terminalText = if (isDark) TerminalTextDark else TerminalTextLight
    val promptColor = if (isDark) TerminalPromptDark else TerminalPromptLight
    val errorColor = if (isDark) TerminalErrorDark else TerminalErrorLight
    val warningColor = if (isDark) TerminalWarningDark else TerminalWarningLight
    val inputBarBg = if (isDark) TerminalInputBarBgDark else TerminalInputBarBgLight

    var command by remember { mutableStateOf("") } // 当前输入的命令
    var isRunning by remember { mutableStateOf(false) } // 命令是否正在执行
    var showSnippetsSheet by remember { mutableStateOf(false) } // 是否显示快捷命令面板

    var showSmartGenerateDialog by remember { mutableStateOf(false) } // 智能生成对话框
    var smartGenerateInput by remember { mutableStateOf("") } // 智能生成输入文本
    var smartGenerateError by remember { mutableStateOf("") } // 智能生成错误信息

    var isInterpreting by remember { mutableStateOf(false) } // 解读中

    val shizukuAvailable = remember { mutableStateOf(ShizukuHelper.isAvailable()) } // Shizuku 是否可用
    val shizukuPermission = remember { mutableStateOf(ShizukuHelper.hasPermission()) } // Shizuku 是否有权限

    val history = remember { mutableStateListOf<ShellResult>() } // 命令执行历史列表
    val listState = rememberLazyListState() // 列表滚动状态

    // 自动滚动到底部（新命令执行时带动画）（当history.size, isRunning 变化时触发）
    LaunchedEffect(history.size, isRunning) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size)
        }
    }

    // 加载保存的历史记录，加载完毕后立即跳到底部（无动画）（Unit：仅仅在启动时加载一次）
    LaunchedEffect(Unit) {
        val savedHistory = shellHistoryRepository.getAllHistorySync()// 从数据库中同步获取所有命令历史记录
        history.addAll(savedHistory)// 将数据库中的历史记录添加到列表中
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.size)//直接跳转，而不是动画滚动
        }
    }

    // 监听命令通道，执行来自 AI 的命令（Unit：仅仅在启动时加载一次）
    //原因是：Unit = 永远不变的值 = 一直开启监听
    LaunchedEffect(Unit) {//是启动一个协程，专门用来持续监听这个热流。
        //.collect是热流自带，用于收集数据
        shellCommandBus.commands.collect { cmd ->
            // ->前面写的是收集到的数据，然后我们命名为临时名字cmd，然后我们以cmd为名字进行操作，如果只有一个参数也可以简写为it来表示这个参数
            //去执行这个命令执行的结果是
            executeCommand(
                cmd,scope,history,shellHistoryRepository,shellCommandBus,{},{ isRunning = it }
            )
        }
    }

    // 主界面布局
    Scaffold(
        topBar = {
            // 顶部标题栏
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shell 终端", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                        // 状态标签
                        Surface(
                            color = if (shizukuAvailable.value && shizukuPermission.value) 
                                promptColor.copy(alpha = 0.15f) else errorColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (!shizukuAvailable.value) "未连接" else if (!shizukuPermission.value) "无权限" else "已连接",
                                color = if (shizukuAvailable.value && shizukuPermission.value) promptColor else errorColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {//右侧的按钮
                    // AI 智能解读按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                val lastResult = history.lastOrNull()
                                if (lastResult != null && lastResult.command != "[解读]") {
                                    isInterpreting = true
                                    try {
                                        val messages = listOf(
                                            ChatMessage("system", "你是一个 Shell 命令输出解读专家。请用简洁的中文解释以下命令执行结果的含义，不要超过100字。"),
                                            ChatMessage("user", "命令: ${lastResult.command}\n输出: ${lastResult.stdout}\n错误: ${lastResult.stderr}\n退出码: ${lastResult.exitCode}")
                                        )
                                        val interpretation = chatClient.sendMessage(messages, appSettings)
                                        val interpretationResult = ShellResult(
                                            command = "[解读]",
                                            stdout = interpretation,
                                            stderr = "",
                                            exitCode = 0
                                        )
                                        history.add(interpretationResult)
                                        shellHistoryRepository.insertHistory(interpretationResult)
                                    } catch (e: Exception) {
                                        val errorResult = ShellResult(
                                            command = "[解读]",
                                            stdout = "",
                                            stderr = "解读失败: ${e.message}",
                                            exitCode = -1
                                        )
                                        history.add(errorResult)
                                        shellHistoryRepository.insertHistory(errorResult)
                                    }
                                    isInterpreting = false
                                }
                            }
                        },
                        enabled = history.isNotEmpty() && !isInterpreting,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isInterpreting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Star, contentDescription = "智能解读", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 清除历史按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                shellHistoryRepository.clearAllHistory()
                                history.clear()
                            }
                        },
                        enabled = history.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "清除历史", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        },
        containerColor = terminalBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // ========== 终端输出区域 ==========
            // 命令执行结果列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusRequester.requestFocus() },
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 使用 itemsIndexed 获取当前是第几个
                //forEachIndexed 和 itemsIndexed 是同一个东西的不同叫法，
                //遍历一个列表，并且同时获取每个元素在列表中的位置（索引）和元素本身。
                //说白了就是一个遍历列表，同时获取每个元素的索引和元素本身。遍历history列表。
                itemsIndexed(history) { index, result ->
                    // 核心逻辑：判断奇偶，分配淡淡的交替背景色
                    val isEven = index % 2 == 0
                    val blockBgColor = if (isDark) {
                        if (isEven) TerminalRowBgEvenDark else TerminalRowBgOddDark
                    } else {
                        if (isEven) TerminalRowBgEvenLight else TerminalRowBgOddLight
                    }

                    TerminalOutputBlock(
                        result = result,
                        context = context,
                        textColor = terminalText,
                        promptColor = promptColor,
                        errorColor = errorColor,
                        warningColor = warningColor,
                        backgroundColor = blockBgColor
                    )
                }

                if (isRunning) {
                    item {
                        Text(
                            text = "命令执行中...",
                            color = warningColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ========== 底部输入与操作区 ==========
            Surface(
                color = inputBarBg,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(), // 适配全面屏手势条
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 快捷命令按钮（弹出命令模板面板）
                    IconButton(
                        onClick = { showSnippetsSheet = true },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "快捷命令", tint = MaterialTheme.colorScheme.primary)
                    }

                    // 智能生成按钮（AI 自然语言转 Shell 命令）
                    IconButton(
                        onClick = {
                            showSmartGenerateDialog = true
                            smartGenerateInput = ""
                            smartGenerateError = ""
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = "智能生成", tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 输入框容器
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isDark) TerminalInputFieldBgDark else TerminalInputFieldBgLight,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$ ",
                            color = promptColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        BasicTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                color = terminalText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp // 移动端稍微调大字体防误触
                            ),
                            cursorBrush = SolidColor(terminalText),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (command.isNotBlank() && !isRunning) {
                                        executeCommand(
                                            command,
                                            scope,
                                            history,
                                            shellHistoryRepository,
                                            shellCommandBus,
                                            { command = "" },
                                            { isRunning = it }
                                        )
                                        keyboardController?.hide()
                                    }
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (command.isEmpty()) {
                                    Text("请输入 Shell 命令...", color = terminalText.copy(alpha = 0.4f), fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 执行按钮
                    FilledIconButton(
                        onClick = {
                            if (command.isNotBlank() && !isRunning) {
                                executeCommand(
                                    command,
                                    scope,
                                    history,
                                    shellHistoryRepository,
                                    shellCommandBus,
                                    { command = "" },
                                    { isRunning = it }
                                )
                                keyboardController?.hide()
                            }
                        },
                        enabled = command.isNotBlank() && !isRunning && shizukuAvailable.value && shizukuPermission.value,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "执行")
                    }
                }
            }
        }

        // ========== 快捷命令底部抽屉 ==========
        if (showSnippetsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSnippetsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                SnippetsPanel(
                    onCommandExecute = { cmd ->
                        executeCommand(
                            cmd,
                            scope,
                            history,
                            shellHistoryRepository,
                            shellCommandBus,
                            {},
                            { isRunning = it }
                        )
                    },
                    onDismiss = { showSnippetsSheet = false }
                )
            }
        }

        // ========== 智能生成对话框 ==========
        if (showSmartGenerateDialog) {
            AlertDialog(
                onDismissRequest = { showSmartGenerateDialog = false },
                title = { Text("智能生成 Shell 命令") },
                text = {
                    Column {
                        Text("用自然语言描述你想执行的 Shell 操作，例如：查看 CPU 使用率")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = smartGenerateInput,
                            onValueChange = { smartGenerateInput = it; smartGenerateError = "" },
                            placeholder = { Text("请输入操作描述...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (smartGenerateError.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(smartGenerateError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (smartGenerateInput.isBlank()) {
                                    smartGenerateError = "请输入操作描述"
                                    return@launch
                                }
                                try {
                                    val messages = listOf(
                                        ChatMessage("system", "你是一个 Shell 命令生成器。根据用户的自然语言描述，返回一个可直接执行的 Shell 命令。只返回命令本身，不要任何解释或额外文本。如果无法生成命令，返回 'ERROR'。"),
                                        ChatMessage("user", smartGenerateInput)
                                    )
                                    val generatedCommand = chatClient.sendMessage(messages, appSettings).trim()
                                    if (generatedCommand.equals("ERROR", ignoreCase = true)) {
                                        smartGenerateError = "未识别到对应的 Shell 指令，请重新描述"
                                    } else {
                                        command = generatedCommand
                                        showSmartGenerateDialog = false
                                    }
                                } catch (e: Exception) {
                                    smartGenerateError = "生成失败: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Text("生成")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSmartGenerateDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/*封装的执行逻辑
 * @param cmd 要执行的命令
 * @param onClearInput 执行成功后清空输入框
 * @param onRunningChanged 执行状态回调
 */
private fun executeCommand(
    cmd: String,//从热流收集到的指令string类型的
    scope: kotlinx.coroutines.CoroutineScope,//协程作用域，用于启动新的协程
    history: androidx.compose.runtime.snapshots.SnapshotStateList<ShellResult>,//显示历史记录的列表
    shellHistoryRepository: ShellHistoryRepository,//持久化存储历史记录的仓库
    shellCommandBus: ShellCommandBus,//热流，用于发送命令和结果
    onClearInput: () -> Unit,//执行成功后清空输入框
    onRunningChanged: (Boolean) -> Unit//执行状态回调
) {
    val cmdToRun = cmd.trim()//去掉指令首尾的空格
    onClearInput()//执行成功后清空输入框
    onRunningChanged(true)//执行状态回调，设置为true表示正在执行
    // 启动一个协程，用于执行命令
    // 当命令执行完成后，将结果添加到显示列表、持久化存储、通知其他组件
    // 并将执行状态回调设置为false表示执行完成
    scope.launch {
        //object ShellExecutor是单例，全局只有一个实例，所以可以直接用类名调用它的方法，不需要创建实例。 ✅
        ShellExecutor.execute(cmdToRun).collect { result ->//把前面的返回值作为这里的形参result在后续使用
            history.add(result) // 追加到列表
            shellHistoryRepository.insertHistory(result) // 持久化存储
            shellCommandBus.sendResult(result) //发送热流，广播我们的结果出去，通知其他组件更新显示
        }
        onRunningChanged(false)//执行状态回调，设置为false表示执行完成
    }
}

//单条命令输出块（无边框，纯文本流体验）
@Composable
private fun TerminalOutputBlock(
    result: ShellResult,//命令执行结果
    context: Context,//上下文，用于获取系统服务
    textColor: Color,//文本颜色
    promptColor: Color,//提示颜色
    errorColor: Color,//错误颜色
    warningColor: Color,//警告颜色
    backgroundColor: Color//背景颜色
) {
    // 点击复制输出内容到剪贴板
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable {
                //准备复制的内容
                val textToCopy = result.stdout
                .ifEmpty { result.stderr }//如果标准输出为空，就准备：错误输出
                
                //获取剪贴板服务。as 是 Kotlin 里的类型转换关键字:告诉编译器“我知道这个东西实际是什么类型，你就把它当作这个类型来用吧”。
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                //获取到的系统服务的类型是any?这个类型；需要转换为我们知晓的这个剪贴板类型；
                //所以clipboard就是一个ClipboardManager类型的对象，我们可以调用它的setPrimaryClip方法来设置剪贴板内容的方法。
                val clipData = ClipData.newPlainText("shell_output", textToCopy)//创建一个ClipData对象，用于存储要复制的内容
                //setPrimaryClip把内容放到用户平时用的那个(主要)剪贴板里
                // Primary：用户明确操作的，优先级最高，随时可以被用户下一次复制覆盖。
                // Secondary：系统后台悄悄处理的，避免干扰用户当前正在使用的剪贴板内容。
                clipboard.setPrimaryClip(clipData)
                //弹窗提示
                Toast.makeText(context, "已复制输出内容", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // 显示发出去的命令
        Text(
            text = "$ ${result.command}",
            color = promptColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        // 成功执行之后的输出
        if (result.stdout.isNotEmpty()) {
            Text(
                text = result.stdout,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 错误输出
        if (result.stderr.isNotEmpty()) {
            Text(
                text = result.stderr,
                color = errorColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        // 非零退出码提示
        if (!result.isSuccess) {
            Text(
                text = "[进程异常退出，错误码: ${result.exitCode}]",
                color = warningColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        // 空白行分隔
        Spacer(modifier = Modifier.height(8.dp))
    }
}

//快捷命令面板(暂时不看)
@Composable
private fun SnippetsPanel(onCommandExecute: (String) -> Unit, onDismiss: () -> Unit) {
    val categories = ShellExecutor.predefinedCommands.map { it.category }.distinct()
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            "选择快捷命令", 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).takeIf { it >= 0 } ?: 0,
            edgePadding = 16.dp,
            divider = {}
        ) {
            categories.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = { Text(category, fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier.heightIn(max = 350.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            val filtered = ShellExecutor.predefinedCommands.filter { it.category == selectedCategory }
            items(filtered) { cmd ->
                ListItem(
                    headlineContent = { Text(cmd.label, fontWeight = FontWeight.Medium) },
                    supportingContent = { 
                        Text(
                            cmd.command, 
                            fontFamily = FontFamily.Monospace, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 2
                        ) 
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { 
                            onCommandExecute(cmd.command)
                            onDismiss()
                        }
                )
            }
        }
    }
}