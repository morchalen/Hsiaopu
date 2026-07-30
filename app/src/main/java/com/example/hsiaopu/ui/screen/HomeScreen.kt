package com.example.hsiaopu.ui.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hsiaopu.R
import com.example.hsiaopu.data.ChatMessage
import com.example.hsiaopu.data.local.ConversationEntity
import com.example.hsiaopu.ui.theme.*
import com.example.hsiaopu.util.VoskSpeechHelper
import com.example.hsiaopu.util.rememberTtsManager
import com.example.hsiaopu.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: ChatViewModel, isTablet: Boolean = false) {
    val TAG = "Hsiaopu-Voice"
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // TTS 自动播放
    val ttsManager = rememberTtsManager()
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var autoPlay by remember { mutableStateOf(true) }
    val lastAutoPlayedMessageId = remember { mutableStateOf<String?>(null) }

    // Vosk 语音识别
    val voskHelper = remember { VoskSpeechHelper(context) }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var isSlidingToCancel by remember { mutableStateOf(false) }
    var pendingVoiceSend by remember { mutableStateOf(false) }

    // 监听 TTS 朗读状态
    DisposableEffect(Unit) {
        ttsManager.onSpeakingStateChanged = { isSpeaking ->
            if (!isSpeaking) {
                speakingMessageId = null
            }
        }
        onDispose {
            ttsManager.onSpeakingStateChanged = null
        }
    }

    // 监听 Vosk 结果和状态
    DisposableEffect(Unit) {
        voskHelper.onResult = { text ->
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk 识别结果: '$text'")
                inputText = text
                if (pendingVoiceSend) {
                    pendingVoiceSend = false
                    Log.d(TAG, "语音识别结果就绪，自动发送")
                    viewModel.sendMessage(text)
                    inputText = ""
                }
            }
        }
        voskHelper.onPartialResult = { text ->
            if (text.isNotBlank()) {
                inputText = text
            }
        }
        voskHelper.onStateChanged = { state ->
            Log.d(TAG, "Vosk 状态变化: $state")
        }
        onDispose {
            voskHelper.onResult = null
            voskHelper.onPartialResult = null
            voskHelper.onStateChanged = null
            voskHelper.release()
        }
    }

    // 初始化 Vosk 模型
    LaunchedEffect(Unit) {
        voskHelper.initialize(scope)
    }

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voskHelper.initialize(scope)
            if (voskHelper.isModelReady) {
                voskHelper.startRecording()
                isVoiceRecording = true
            }
        }
    }

    // 协程的启动方式讲解:
    // LaunchedEffect	组件进入时自动启动，离开时自动取消
    // rememberCoroutineScope	在事件回调中手动启动
    // viewModelScope.launch	ViewModel 中启动，ViewModel 销毁时取消
    // lifecycleScope.launch	Activity/Fragment 中启动，生命周期结束时取消
    // CoroutineScope(...).launch	自定义作用域，需要手动取消

    // ai回复的时候，消息变化，自动滚动到底部
    // //只有当括号里的值发生变化时，才会再次运行 {} 里的代码。
    LaunchedEffect(
    uiState.messages.size,   // ① 消息列表的数量
    uiState.streamingContent // ② AI 正在流式输出的内容
    ) {
        
        if (uiState.messages.isNotEmpty() || uiState.streamingContent.isNotEmpty()) {
            // 消息列表有内容或 AI 正在流式输出内容时，滚动到底部；// 调用LazyListState 它自带的方法 scrollToItem 滚动到指定项
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            //totalItemsCount - 1 = 最后一条消息的索引（下标），不是“倒数第二条”。
        }
    }
    // 自动播放逻辑：检测新用户消息 -> 停止当前 TTS
    LaunchedEffect(uiState.messages.lastOrNull { it.role == "user" }?.timestamp) {
        if (autoPlay) {
            ttsManager.stop()
            lastAutoPlayedMessageId.value = null
        }
    }

    // 自动播放逻辑：检测新 AI 回复 -> 加入 TTS 队列
    LaunchedEffect(uiState.messages.size, autoPlay) {
        if (!autoPlay || uiState.messages.isEmpty()) return@LaunchedEffect
        val lastUserIdx = uiState.messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return@LaunchedEffect
        val unspoken = uiState.messages.drop(lastUserIdx + 1)
            .filter { it.role != "user" && it.timestamp != lastAutoPlayedMessageId.value?.toLongOrNull() }
        if (unspoken.isEmpty()) return@LaunchedEffect
        unspoken.forEachIndexed { i, msg ->
            if (i == 0) {
                ttsManager.speak(msg.content)
            } else {
                ttsManager.speakQueued(msg.content)
            }
            lastAutoPlayedMessageId.value = msg.timestamp.toString()
        }
    }

    // 输入框有内容时点击返回键清空输入框
    //拦截器：在返回键点击时，判断输入框是否有内容，有内容则清空输入框，否则不执行默认返回操作
    if (inputText.isNotBlank()) {
        BackHandler { inputText = "" }// 输入框有内容时点击返回键清空输入框
    }

    // 删除确认弹窗
    //deleteConfirmId 有值（不为 null），则显示删除确认弹窗
    deleteConfirmId?.let { id ->
        AlertDialog(//带确认/取消按钮的标准弹窗
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("确认删除吗？") },
            text = { Text("删除后将无法恢复，是否继续?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(id)
                    deleteConfirmId = null
                }) { Text("确认删除", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("取消") }
            }
        )
    }

    // ========== 抽屉内容（手机和平板共用） ==========
    val drawerContent = @Composable {
        ConversationDrawerContent(
            
            conversations = uiState.conversations,
            currentId = uiState.currentConversationId,
            //回调（Callback）	把一段代码作为参数传给别人，让别人在合适的时候调用
            //这里必须写函数以及实际参数，因为回调是使用代码方法，需要我们给回调函数赋予实际参数，在回调的具体实现代码里面使用的是形参来撰写代码的
            onSelect = {
                viewModel.selectConversation(it)
                scope.launch { drawerState.close() }
            },
            //当触发删除按钮时，将删除确认弹窗的对话框ID设置为当前项的ID
            onDelete = { deleteConfirmId = it },
            //当触发重命名按钮时，调用ViewModel的renameConversation方法，传入当前项的ID和新的标题
            onRename = { id, title -> viewModel.renameConversation(id, title) },
            //当触发新建聊天按钮时，调用ViewModel的createNewConversation方法，创建新的聊天会话
            //如果是平板模式，关闭抽屉；否则关闭抽屉状态
            onNewChat = {
                viewModel.createNewConversation()
                scope.launch { drawerState.close() }
            }
        )
    }

    // ========== 聊天内容主体（手机和平板共用） ==========
    //将 @Composable 函数赋值给变量，组件嵌套调用
    val chatContent = @Composable {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {

            // 使用 TopAppBar 作为页面锚点（规范六：每个主页面必须有 TopAppBar）
            TopAppBar(
                title = {
                    Text(
                        "Hsiaopu",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (!isTablet) {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                          }) {
                            Icon(Icons.Default.Menu, contentDescription = "对话记录菜单", modifier = Modifier.size(22.dp))
                        }
                    }
                },
                actions = {
                    // 自动朗读按钮
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .combinedClickable(
                                onClick = {
                                    autoPlay = !autoPlay
                                    if (!autoPlay) {
                                        ttsManager.stop()
                                        lastAutoPlayedMessageId.value = null
                                    }
                                },
                                onLongClick = {
                                    Toast.makeText(
                                        context,
                                        "更换语音引擎：系统设置 → 辅助功能 → 文字转语音 → 首选引擎",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (autoPlay) "关闭自动朗读" else "开启自动朗读",
                            tint = if (autoPlay)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.createNewConversation() },
                        enabled = uiState.messages.isNotEmpty() || uiState.streamingContent.isNotEmpty()
                    ) {
                        if (uiState.messages.isNotEmpty() || uiState.streamingContent.isNotEmpty()) {
                            Icon(Icons.Default.Add, contentDescription = "新建对话框", modifier = Modifier.size(22.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // 内容区域
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.messages.isEmpty() && uiState.streamingContent.isEmpty()) {
                    EmptyChatPlaceholder(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),           // 占满可用空间
                        state = listState,                          // 控制滚动位置
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),  // 左右留白
                        verticalArrangement = Arrangement.spacedBy(8.dp)  // 每条消息间距 8dp
                    ) {
                        // 1️⃣ 渲染所有历史消息
                        items(
                            items = uiState.messages,
                            key = { "msg_${it.timestamp}" }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                isSpeaking = speakingMessageId == message.timestamp.toString(),
                                onSpeakToggle = {
                                    val msgId = message.timestamp.toString()
                                    if (speakingMessageId == msgId) {
                                        ttsManager.stop()
                                        speakingMessageId = null
                                    } else {
                                        ttsManager.stop()
                                        ttsManager.speak(message.content)
                                        speakingMessageId = msgId
                                    }
                                },
                                onCopy = { }
                            )
                        }

                        // 2️⃣ 如果 AI 正在流式回复，显示流式消息
                        if (uiState.streamingContent.isNotEmpty()) {
                            item(key = "streaming") {
                                MessageBubble(//把流式消息显示为气泡
                                    message = ChatMessage(role = "assistant", content = uiState.streamingContent),
                                    isStreaming = true  // 显示打字光标
                                )
                            }
                        }

                        // 3️⃣ 如果正在加载且没有流式内容，显示加载动画
                        if (uiState.isLoading && uiState.streamingContent.isEmpty()) {
                            item(key = "loading") {
                                LoadingDots()  // 三个跳动的点
                            }
                        }
                    }
                }

                // 滚动到底部的 FAB 按钮（临时关闭）
                // androidx.compose.animation.AnimatedVisibility(
                //     visible = showScrollToBottom,
                //     enter = scaleIn(tween(300)) + fadeIn(tween(300)),
                //     exit = scaleOut(tween(200)) + fadeOut(tween(200)),
                //     modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                // ) {
                //     SmallFloatingActionButton(
                //         onClick = { scope.launch { listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1) } },
                //         containerColor = MaterialTheme.colorScheme.primary,
                //         contentColor = MaterialTheme.colorScheme.onPrimary
                //     ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "滚动到底部") }
                // }
            }

            // 错误提示区域
            // if (uiState.error != null) {
            //     Snackbar(
            //         modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            //         action = { TextButton(onClick = { viewModel.clearError() }) { Text("关闭") } }
            //     ) { Text(uiState.error!!) }
            // }

            // 输入区域
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                isLoading = uiState.isLoading,
                isVoiceRecording = isVoiceRecording,
                isSlidingToCancel = isSlidingToCancel,
                onSlidingToCancelChange = { isSlidingToCancel = it },
                onVoiceStart = {
                    ttsManager.stop()
                    speakingMessageId = null
                    if (voskHelper.isModelReady) {
                        if (voskHelper.hasPermission()) {
                            voskHelper.startRecording()
                            isVoiceRecording = true
                            isSlidingToCancel = false
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onVoiceEnd = { isCancel ->
                    if (isVoiceRecording) {
                        if (!isCancel) {
                            pendingVoiceSend = true
                        }
                        voskHelper.stopRecording()
                        isVoiceRecording = false
                        isSlidingToCancel = false
                    }
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                }
            )
        }

        // 微信风格录音弹窗
        if (isVoiceRecording) {
            RecordingPopup(
                isSlidingToCancel = isSlidingToCancel
            )
        }
    }

    // ========== 平板模式：Row 布局，左侧抽屉固定显示 ==========
    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            //第一列：抽屉
            Surface(
                modifier = Modifier.fillMaxHeight().width(300.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                drawerContent()
            }
            //第二列：聊天内容
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            //第三列：聊天区域
            chatContent()
        }
    } else {
        // ========== 手机模式：ModalNavigationDrawer 覆盖式抽屉 ==========
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
            gesturesEnabled = true,
            scrimColor = Black.copy(alpha = 0.5f)
        ) {
            chatContent()
        }
    }
}


// ==================== 空状态占位符 ====================

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Forum,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        )
        Spacer(modifier = Modifier.height(12.dp))   
        Text(
            "开始对话",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    }
}

// ==================== 消息气泡 ====================

/**
 * 聊天消息气泡组件
 * 
 * 根据消息角色（用户/AI）展示不同样式的聊天气泡：
 * - 用户消息：右对齐，带主题色背景，最大宽度 300dp
 * - AI 消息：左对齐，透明背景，撑满宽度
 * - 流式输出时：AI 消息下方显示闪烁光标
 * - 长按气泡：弹出复制菜单
 * 
 * @param message 聊天消息数据（角色、内容、时间戳）
 * @param isStreaming 是否为流式输出中（AI 正在回复）
 * @param onCopy 复制内容到剪贴板的回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    isSpeaking: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    onCopy: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isDark = isSystemInDarkTheme()
    val userBubbleColor = if (isDark) UserBubbleDark else UserBubbleLight
    val assistantBubbleColor = Color.Transparent

    // ========== 外层容器：控制整条消息的左右对齐 ==========
    Column(
        modifier = Modifier.fillMaxWidth(),//尽量撑满宽度
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // ========== 1顶部信息行：发送者标签 + 时间戳 ==========
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(//padding是内边距，下面用来描述占据的面积位置
                start = if (isUser) 0.dp else 4.dp,// 用户消息：左侧不留白，AI 消息：左侧留4dp
                end = if (isUser) 4.dp else 0.dp,// 用户消息：右侧留4dp，AI 消息：右侧不留白
                bottom = 4.dp// 底部留白4dp（间距阶梯 xs）
            )
        ) {
            //1： AI 消息：显示 "AI" 标签
            if (!isUser) {
                Text(
                    text = if (isStreaming) "AI…" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            //2： 时间戳（HH:mm 格式）
            Text(
                text = dateFormat.format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )

            //3： 用户消息：显示 "You" 标签
            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // ========== 2气泡主体 + 长按复制菜单 ==========
        Box {
            // 气泡容器
            Surface(
                color = if (isUser) userBubbleColor else assistantBubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,   // 用户：右下小圆角，AI：左下小圆角
                    bottomEnd = if (isUser) 4.dp else 16.dp       // 模拟对话气泡的尾巴效果
                ),
                modifier = Modifier
                    .let { modifier ->
                        if (isUser) {
                            modifier.widthIn(max = 300.dp)        // 用户消息限制最大宽度
                        } else {
                            modifier.fillMaxWidth()               // AI 消息撑满宽度
                        }
                    }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true; onCopy?.invoke() }  // 长按触发复制
                    )
            ) {
                if (isUser) {
                    MarkdownText(
                        content = message.content,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    Column {
                        MarkdownText(
                            content = message.content,
                            modifier = Modifier.padding(12.dp)
                        )
                        // AI 消息朗读按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onSpeakToggle,
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isSpeaking)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else
                                        Color.Transparent,
                                    contentColor = if (isSpeaking)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isSpeaking) "停止朗读" else "朗读",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 长按弹出的复制菜单
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = { onCopy?.invoke(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
            }
        }

        // ========== 3流式输出光标：AI 回复时显示闪烁的竖线 ==========
        if (isStreaming) {
            Spacer(modifier = Modifier.height(4.dp))

            // 闪烁动画：透明度在 1.0 ~ 0.2 之间循环
            val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cursor_alpha"
            )

            // 光标竖线：4dp 宽 × 16dp 高，圆角矩形
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

// ==================== 加载指示器 ====================

@Composable
fun LoadingDots() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(300L); step = (step + 1) % 3 } }
    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val scale by animateFloatAsState(
                targetValue = if (step == i) 1.3f else 1f,
                animationSpec = tween(300),
                label = "dot_scale"
            )
            Box(modifier = Modifier
                .size(8.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = when { step == i -> 0.9f; (step + 1) % 3 == i -> 0.5f; else -> 0.25f })))
        }
    }
}

// ==================== 输入框 ====================

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    isVoiceRecording: Boolean = false,
    isSlidingToCancel: Boolean = false,
    onSlidingToCancelChange: (Boolean) -> Unit = {},
    onVoiceStart: () -> Unit = {},
    onVoiceEnd: (isCancel: Boolean) -> Unit = {},
    onSend: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    var isVoicePressed by remember { mutableStateOf(false) }

    val sendScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(200),
        label = "send_scale"
    )

    val micTint by animateColorAsState(
        targetValue = when {
            isVoiceRecording -> MaterialTheme.colorScheme.error
            isVoicePressed -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "micTint"
    )

    val micBg by animateColorAsState(
        targetValue = when {
            isVoicePressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        label = "micBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "发送指令",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 语音按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(micBg)
                    .pointerInput(Unit) {
                        val density = this.density
                        val cancelThresholdPx = with(density) { 80.dp.toPx() }
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isVoicePressed = true
                                onVoiceStart()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val slideUpPx = -change.position.y
                                if (slideUpPx > cancelThresholdPx) {
                                    onSlidingToCancelChange(true)
                                } else {
                                    onSlidingToCancelChange(false)
                                }
                            },
                            onDragEnd = {
                                isVoicePressed = false
                                onVoiceEnd(false)
                            },
                            onDragCancel = {
                                isVoicePressed = false
                                onVoiceEnd(true)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "语音输入",
                    tint = micTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 发送按钮
            val sendEnabled = inputText.isNotBlank() && !isLoading
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        color = if (sendEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = sendEnabled,
                        onClick = onSend
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (sendEnabled)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
        }
    }
}

// ==================== 录音弹窗 ====================

@Composable
private fun RecordingPopup(
    isSlidingToCancel: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_anim")
    val waveAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "wave"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSlidingToCancel) 120.dp else 128.dp)
                    .background(
                        color = if (isSlidingToCancel)
                            Color(0xFFFF4444)
                        else
                            Color(0xFF3A3A3A),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSlidingToCancel) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "↑",
                            fontSize = 28.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "取消发送",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        for (i in 0..3) {
                            val alpha = ((waveAnim * 4 - i.toFloat()).coerceIn(0f, 1f)) * 0.5f
                            if (alpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .size((48 + i * 20).dp)
                                        .scale(1f + waveAnim * 0.15f)
                                        .background(
                                            color = Color.White.copy(alpha = alpha * 0.3f),
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSlidingToCancel) "松开手指，取消发送" else "手指上滑，取消发送",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        color = if (isSlidingToCancel) Color(0xFFFF4444) else Color(0xFF3A3A3A),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
// ==================== Markdown ====================

/**
 * 渲染Markdown文本内容的组件
 * 
 * 该组件将Markdown格式的文本解析为可组合的UI元素（文本、代码块、内联代码、标题），
 * 并根据当前主题（深色/浅色）应用不同的样式。
 * 
 * @param content Markdown格式的文本内容
 * @param modifier 可选的修饰符，用于自定义组件布局和样式
 */
@Composable
fun MarkdownText(content: String, modifier: Modifier = Modifier) {
    val segments = remember(content) { parseMarkdown(content) }
    val isDark = isSystemInDarkTheme()
    val codeBlockBg = if (isDark) CodeBlockBg else CodeBlockBgLight
    val inlineCodeBg = if (isDark) InlineCodeBg else InlineCodeBgLight
    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> Text(text = segment.text, style = MaterialTheme.typography.bodyLarge,
                    color = if (segment.isBold) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal)
                is MarkdownSegment.Code -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = codeBlockBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(0.5.dp, CodeBorder, RoundedCornerShape(8.dp))) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Text(text = segment.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MarkdownSegment.InlineCode -> Surface(color = inlineCodeBg, shape = RoundedCornerShape(4.dp)) {
                    Text(text = segment.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                is MarkdownSegment.Header -> Text(text = segment.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                is MarkdownSegment.ListItem -> Row {
                    Text("  ${segment.bullet} ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(segment.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

sealed class MarkdownSegment {
    data class Text(val text: String, val isBold: Boolean = false) : MarkdownSegment()
    data class Code(val code: String) : MarkdownSegment()
    data class InlineCode(val code: String) : MarkdownSegment()
    data class Header(val text: String) : MarkdownSegment()
    data class ListItem(val bullet: String, val text: String) : MarkdownSegment()
}

private fun parseMarkdown(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = content.split("\n")
    var inCodeBlock = false
    val codeBuffer = StringBuilder()
    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (inCodeBlock) { if (codeBuffer.isNotEmpty()) { segments.add(MarkdownSegment.Code(codeBuffer.toString().trimEnd())); codeBuffer.clear() }; inCodeBlock = false }
            else inCodeBlock = true
            continue
        }
        if (inCodeBlock) { if (codeBuffer.isNotEmpty()) codeBuffer.append("\n"); codeBuffer.append(line); continue }
        if (line.trimStart().startsWith("### ")) { segments.add(MarkdownSegment.Header(line.trimStart().removePrefix("### ").trim())); continue }
        if (line.trimStart().startsWith("## ")) { segments.add(MarkdownSegment.Header(line.trimStart().removePrefix("## ").trim())); continue }
        if (line.trimStart().startsWith("# ")) { segments.add(MarkdownSegment.Header(line.trimStart().removePrefix("# ").trim())); continue }
        if (line.trimStart().matches(Regex("^[-*+]\\s"))) { segments.add(MarkdownSegment.ListItem("\u2022", line.trimStart().replaceFirst(Regex("^[-*+]\\s"), ""))); continue }
        if (line.trimStart().matches(Regex("^\\d+\\.\\s"))) { segments.add(MarkdownSegment.ListItem("${line.trimStart().substringBefore(".")}.", line.trimStart().substringAfter(". ").trim())); continue }
        segments.addAll(processInlineMarkdown(line))
    }
    if (inCodeBlock && codeBuffer.isNotEmpty()) segments.add(MarkdownSegment.Code(codeBuffer.toString().trimEnd()))
    return segments.ifEmpty { listOf(MarkdownSegment.Text(content)) }
}

private fun processInlineMarkdown(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val regex = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")
    var lastIndex = 0
    regex.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) segments.add(MarkdownSegment.Text(text.substring(lastIndex, match.range.first)))
        when { match.groups[1] != null -> segments.add(MarkdownSegment.Text(match.groups[1]!!.value, isBold = true)); match.groups[2] != null -> segments.add(MarkdownSegment.InlineCode(match.groups[2]!!.value)) }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) segments.add(MarkdownSegment.Text(text.substring(lastIndex)))
    return segments.ifEmpty { listOf(MarkdownSegment.Text(text)) }
}



