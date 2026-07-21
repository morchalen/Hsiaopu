package com.example.hsiaopu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hsiaopu.R
import com.example.hsiaopu.data.ChatMessage
import com.example.hsiaopu.data.local.ConversationEntity
import com.example.hsiaopu.ui.theme.*
import com.example.hsiaopu.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)// 开启实验性基础 API 和实验性 Material3 API
@Composable
fun HomeScreen(viewModel: ChatViewModel, isTablet: Boolean = false) {
    val uiState by viewModel.uiState.collectAsState()// 获取ViewModel 中的 UI 状态
    var inputText by remember { mutableStateOf("") }// 输入框文本
    // listState 是 LazyListState 的实例，用于管理消息列表的状态和行为
    //rememberLazyListState() 就是创建 LazyListState 的一个实例对象，并且这个对象是“记忆化”的，重组时不会丢失状态。( 不用 remember：每次重组都会重新创建，滚动位置会丢失)
    val listState = rememberLazyListState()// 消息列表状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)// 抽屉状态
    // var showScrollToBottom by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }// 删除确认弹窗的对话框ID
    val context = LocalContext.current// 获取上下文
    val scope = rememberCoroutineScope()// 记住协程作用域

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
    // 可见项索引变化时自动更新滚动到底部按钮状态
    // val visibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
    // LaunchedEffect(visibleItemIndex) {
    //     showScrollToBottom = listState.layoutInfo.totalItemsCount > 0 &&
    //         visibleItemIndex != listState.layoutInfo.totalItemsCount - 1
    // }

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
                        items(//遍历列表，每条数据生成一个 UI 组件
                            items = uiState.messages,
                            key = { "msg_${it.timestamp}" }  // 每条消息唯一标识，优化性能
                        ) { message ->//对每一条消息执行的逻辑
                            MessageBubble(//把单条消息显示为气泡
                                message = message,
                                onCopy = { /* 复制消息内容👈 这是个占位/形参 */ }
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
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                }
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
    message: ChatMessage,// 聊天消息数据（角色、内容、时间戳）
    isStreaming: Boolean = false,// 是否为流式输出中（AI 正在回复）
    onCopy: (() -> Unit)? = null// 复制内容到剪贴板的回调
) {
    // ========== 状态与变量 ==========
    val isUser = message.role == "user"                              // 消息的角色。默认的哈
    var showMenu by remember { mutableStateOf(false) }               // 复制菜单是否显示；实时重组
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }  // 时间格式化
    //Locale.getDefault() = “用你手机的默认语言来显示日期和文本。” 😊
    val isDark = isSystemInDarkTheme()                               // 是否深色模式
    val userBubbleColor = if (isDark) UserBubbleDark else UserBubbleLight//重组的时候自动修改
    val assistantBubbleColor = Color.Transparent                     // AI 消息背景透明

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
                // 气泡内容：Markdown 渲染
                MarkdownText(
                    content = message.content,
                    modifier = Modifier.padding(12.dp)
                )
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

/**
 * 聊天输入栏组件
 * 
 * 这是聊天界面底部的输入区域，包含：
 * 1. 输入框（OutlinedTextField）—— 用户打字的地方
 * 2. 发送按钮（FilledIconButton）—— 点击发送消息
 * 
 * @param inputText 当前输入框中的文字（由父组件管理）
 * @param onInputChange 文字变化时的回调（通知父组件更新文字）
 * @param isLoading 是否正在加载（AI 回复中），加载时禁用发送按钮
 * @param onSend 点击发送按钮时的回调
 */
@Composable
fun ChatInputBar(
    inputText: String,                    // 当前输入的文字
    onInputChange: (String) -> Unit,      // 文字变化时通知父组件
    isLoading: Boolean,                   // 是否正在加载（AI 思考中）
    onSend: () -> Unit                    // 点击发送按钮时执行
) {
    // ========== 按钮按压状态 ==========
    // pressed = true 时按钮缩小，模拟物理按压效果
    var pressed by remember { mutableStateOf(false) }

    // ========== 发送按钮缩放动画 ==========
    // 当 pressed = true 时，按钮缩放到 0.85 倍（看起来被按下去）
    // 当 pressed = false 时，按钮恢复 1 倍（弹起来）
    //by = 自动拆包，让你直接拿到 值，不用每次都写 .value ；；；这里的sendScale被自动推导为Float 类型，因为是Modifier.scale来使用的，这个里面本来是应该放置一个浮点数的
    val sendScale by animateFloatAsState(//animateFloatAsState让 Float 值变化时播放平滑动画
        targetValue = if (pressed) 0.85f else 1f,  // 目标缩放值
        animationSpec = tween(200),               // 200ms 动画时长
        label = "send_scale"                      // 调试标签
    )

    // ========== 输入框 ==========
    Box(
    modifier = Modifier
        .fillMaxWidth()
        ){
        // ========== 水平布局：输入框 + 发送按钮 ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()                   // 占满屏幕宽度
                .padding(horizontal = 12.dp, vertical = 8.dp), // 左右12dp，上下8dp
            verticalAlignment = Alignment.Bottom   // 子元素底部对齐
        ) {
            // ========== 输入框 ==========
            OutlinedTextField(
                // ----- 数据和事件 -----
                value = inputText,                 // 当前显示的文字
                onValueChange = onInputChange,     // 用户打字时通知父组件
                
                // ----- 布局 -----
                modifier = Modifier.weight(1f),    // 占满剩余宽度（让按钮固定大小）
                
                // ----- 提示文字 -----
                placeholder = {
                    Text(
                        "发送指令",        // 输入框为空时显示的灰色文字
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                
                // ----- 行数限制 -----
                maxLines = 5,                      // 最多5行，超过滚动
                
                // ----- 文字样式 -----
                textStyle = MaterialTheme.typography.bodyLarge,
                
                // ----- 颜色配置 -----
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),  // 聚焦时边框颜色（主色半透明）
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,            // 失焦时边框颜色
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,           // 聚焦时背景色
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant          // 失焦时背景色
                ),
                
                // ----- 形状 -----
                shape = RoundedCornerShape(20.dp)  // 圆角 20dp（胶囊形状）
            )

            // ========== 输入框和按钮之间的间距 ==========
            Spacer(modifier = Modifier.width(8.dp))

            // ========== 发送按钮 ==========
            FilledIconButton(
                // ----- 点击事件 -----
                onClick = {
                    pressed = true                 // 触发按压动画
                    onSend()                       // 执行发送逻辑
                },
                
                // ----- 启用/禁用 -----
                // 只有输入框有文字 && 不在加载状态时，按钮才可点击
                enabled = inputText.isNotBlank() && !isLoading,
                
                // ----- 样式 -----
                modifier = Modifier
                    .size(48.dp)                   // 按钮大小 48x48
                    .scale(sendScale),             // 跟随缩放动画（按压效果）,提前定义好的哈
                
                // ----- 颜色 -----
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,  // 启用时背景色（主色）
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) // 禁用时背景色（半透明）
                ),
                
                // ----- 形状 -----
                shape = CircleShape                // 圆形
            ) {
                // ----- 按钮图标（纸飞机/发送） -----
                @Suppress("DEPRECATION")
                Icon(
                    Icons.Default.Send,            // 发送图标
                    contentDescription = "发送",   // 无障碍描述
                    tint = MaterialTheme.colorScheme.onPrimary,  // 图标颜色（主色上的文字颜色）
                    modifier = Modifier.size(20.dp) // 图标大小 20dp
                )
            }
        }
    }

    // ========== 重置按压状态 ==========
    // 点击按钮后，100ms 后自动将 pressed 重置为 false，让按钮弹起来
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)                           // 等待 100ms
            pressed = false                      // 重置状态
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



