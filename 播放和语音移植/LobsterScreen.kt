package com.example.hsiaowear.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hsiaowear.R
import android.util.Log
import android.widget.Toast
import com.example.hsiaowear.ui.components.EmptyState
import com.example.hsiaowear.util.rememberTtsManager
import com.example.hsiaowear.util.VoskSpeechHelper
import com.example.hsiaowear.viewmodel.ChatMessage
import com.example.hsiaowear.viewmodel.LobsterViewModel

@Composable
fun LobsterScreen(viewModel: LobsterViewModel, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val TAG = "HsiaoWear-Voice"
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val ttsManager = rememberTtsManager()
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }

    // 自动播放开关
    var autoPlay by remember { mutableStateOf(true) }
    // 记录最后播放过的 AI 消息 ID，避免重复播放
    val lastAutoPlayedMessageId = remember { mutableStateOf<Long?>(null) }

    // Vosk 语音识别
    val voskHelper = remember { VoskSpeechHelper(context) }
    val scope = rememberCoroutineScope()
    var isVoiceRecording by remember { mutableStateOf(false) }
    var isSlidingToCancel by remember { mutableStateOf(false) }
    var pendingVoiceSend by remember { mutableStateOf(false) }

    // 监听 Vosk 状态变化
    DisposableEffect(Unit) {
        voskHelper.onStateChanged = { state ->
            Log.d(TAG, "Vosk 状态变化: $state")
        }
        onDispose {
            voskHelper.onStateChanged = null
        }
    }

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "录音权限结果: granted=$granted")
        if (granted) {
            Log.d(TAG, "权限已授予，初始化并开始录音")
            voskHelper.initialize(scope)
            if (voskHelper.isModelReady) {
                voskHelper.startRecording()
                isVoiceRecording = true
                Log.d(TAG, "录音已开始")
            } else {
                Log.d(TAG, "模型未就绪，等待自动初始化完成后开始")
            }
        } else {
            Log.w(TAG, "用户拒绝了录音权限，无法使用语音输入")
        }
    }

    // 初始化 Vosk 模型（后台下载）
    LaunchedEffect(Unit) {
        Log.d(TAG, "开始初始化 Vosk 模型")
        voskHelper.initialize(scope)
    }

    // 监听 Vosk 结果和状态
    DisposableEffect(Unit) {
        voskHelper.onResult = { text ->
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk 识别结果: '$text'")
                inputText = text
                // 如果刚结束录音，自动发送
                if (pendingVoiceSend) {
                    pendingVoiceSend = false
                    Log.d(TAG, "语音识别结果就绪，自动发送")
                    viewModel.sendMessage(text)
                    inputText = ""
                }
            } else {
                Log.d(TAG, "Vosk 识别结果为空，忽略")
            }
        }
        voskHelper.onPartialResult = { text ->
            if (text.isNotBlank()) {
                Log.d(TAG, "Vosk 部分识别: '$text'")
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
            Log.d(TAG, "Vosk 资源已释放")
        }
    }

    // 监听朗读状态，朗读结束时清除 speakingMessageId
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 自动播放逻辑：检测新用户消息 -> 停止当前 TTS
    LaunchedEffect(messages.lastOrNull { it.isUser }?.id) {
        if (autoPlay) {
            Log.d("HsiaoWear-AutoPlay", "检测到新用户消息，停止自动播放")
            ttsManager.stop()
            lastAutoPlayedMessageId.value = null
        }
    }

    // 自动播放逻辑：检测新 AI 回复 -> 加入 TTS 队列
    LaunchedEffect(messages.size, autoPlay) {
        if (!autoPlay || messages.isEmpty()) return@LaunchedEffect
        val lastUserIdx = messages.indexOfLast { it.isUser }
        if (lastUserIdx < 0) return@LaunchedEffect
        // 获取最后一条用户消息之后、未播放过的 AI 回复
        val unspoken = messages.drop(lastUserIdx + 1)
            .filter { !it.isUser && it.id != lastAutoPlayedMessageId.value }
        if (unspoken.isEmpty()) return@LaunchedEffect
        Log.d("HsiaoWear-AutoPlay", "自动播放 ${unspoken.size} 条 AI 回复")
        // 第一条用 QUEUE_FLUSH（打断当前），后续用 QUEUE_ADD（排队）
        unspoken.forEachIndexed { i, msg ->
            if (i == 0) {
                ttsManager.speak(msg.text)
            } else {
                ttsManager.speakQueued(msg.text)
            }
            lastAutoPlayedMessageId.value = msg.id
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 自动朗读按钮（极简，仅占一行，长按查看 TTS 设置路径）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp, top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

        if (messages.isEmpty()) {
            LobsterEmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        isSpeaking = speakingMessageId == message.id,
                        onSpeakToggle = {
                            if (speakingMessageId == message.id) {
                                ttsManager.stop()
                                speakingMessageId = null
                            } else {
                                ttsManager.stop()
                                ttsManager.speak(message.text)
                                speakingMessageId = message.id
                            }
                        }
                    )
                }
                if (isTyping) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 20.dp
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }
        }

        InputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            },
            isLoading = isTyping,
            isVoiceRecording = isVoiceRecording,
            isSlidingToCancel = isSlidingToCancel,
            onSlidingToCancelChange = { isSlidingToCancel = it },
            onVoiceStart = {
                // 开始录音前停止 TTS 朗读
                ttsManager.stop()
                speakingMessageId = null
                Log.d(TAG, "语音按钮按下, isModelReady=${voskHelper.isModelReady}, hasPermission=${voskHelper.hasPermission()}")
                if (voskHelper.isModelReady) {
                    if (voskHelper.hasPermission()) {
                        voskHelper.startRecording()
                        isVoiceRecording = true
                        isSlidingToCancel = false
                        Log.d(TAG, "✓ 录音已开始")
                    } else {
                        Log.d(TAG, "请求录音权限")
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    Log.w(TAG, "Vosk 模型未就绪，无法录音")
                }
            },
            onVoiceEnd = { isCancel ->
                if (isVoiceRecording) {
                    // 先标记等待发送，再停止录音（确保 onResult 能正确触发发送）
                    if (!isCancel) {
                        pendingVoiceSend = true
                    }
                    voskHelper.stopRecording()
                    isVoiceRecording = false
                    isSlidingToCancel = false
                    Log.d(TAG, "✓ 录音已停止${if (isCancel) "（已取消）" else "（等待识别结果发送）"}")
                }
            }
        )
    }

        // 微信风格录音弹窗（覆盖在页面之上）
        if (isVoiceRecording) {
            RecordingPopup(
                isSlidingToCancel = isSlidingToCancel
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean = false,
    onSpeakToggle: () -> Unit = {}
) {
    val isUser = message.isUser
    val timeText = remember(message.timestamp) {
        formatTime(message.timestamp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // AI 消息左侧的简约指示器
        if (!isUser) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.Top)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 气泡主体
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .shadow(
                        elevation = if (isUser) 0.dp else 2.dp,
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 20.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        ),
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .background(
                        color = if (isUser)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 20.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateContentSize()
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Column {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

            // 时间戳
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

/** 格式化时间戳为 HH:mm 格式 */
private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
private fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Dot(delay = 0)
        Dot(delay = 150)
        Dot(delay = 300)
    }
}

@Composable
private fun Dot(delay: Int) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(delay.toLong())
            visible = !visible
            kotlinx.coroutines.delay(400)
            visible = !visible
        }
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (visible) 0.8f else 0.2f),
                shape = RoundedCornerShape(50)
            )
    )
}

@Composable
private fun InputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isVoiceRecording: Boolean = false,
    isSlidingToCancel: Boolean = false,
    onSlidingToCancelChange: (Boolean) -> Unit = {},
    onVoiceStart: () -> Unit = {},
    onVoiceEnd: (isCancel: Boolean) -> Unit = {}
) {
    var isVoicePressed by remember { mutableStateOf(false) }

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 统一圆角输入容器（仅包含输入框）
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(start = 12.dp, end = 4.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        stringResource(R.string.lobster_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                singleLine = false
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Vosk 语音按钮（独立放置，避免触摸事件冲突）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(micBg)
                .pointerInput(Unit) {
                    val density = this.density
                    val cancelThresholdPx = with(density) { 80.dp.toPx() }
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isVoicePressed = true
                        onVoiceStart()
                        var canceled = false
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val slideUpPx = -change.position.y
                                if (slideUpPx > cancelThresholdPx) {
                                    if (!canceled) {
                                        canceled = true
                                        onSlidingToCancelChange(true)
                                    }
                                } else {
                                    if (canceled) {
                                        canceled = false
                                        onSlidingToCancelChange(false)
                                    }
                                }
                            } while (true)
                        } catch (_: kotlinx.coroutines.CancellationException) {
                        } finally {
                            isVoicePressed = false
                            onVoiceEnd(canceled)
                        }
                    }
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
                contentDescription = stringResource(R.string.lobster_send),
                tint = if (sendEnabled)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun LobsterEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = "✨",
            title = stringResource(R.string.lobster_empty),
            subtitle = stringResource(R.string.lobster_empty_hint),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** 微信风格录音弹窗 */
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
            // 录音圆框（麦克风 + 波浪）
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
                    // 上滑取消状态：显示上箭头 + 文字
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "↑",
                            fontSize = 28.sp,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "取消发送",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = Color.White
                        )
                    }
                } else {
                    // 录音状态：麦克风 + 声波动画
                    Box(contentAlignment = Alignment.Center) {
                        // 声波动画（在麦克风后面）
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
                        // 麦克风图标
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

            // 提示文字
            Text(
                text = if (isSlidingToCancel) "松开手指，取消发送" else "手指上滑，取消发送",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
