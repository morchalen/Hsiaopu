package com.example.hsiaopu.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hsiaopu.data.local.ConversationEntity
import com.example.hsiaopu.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationDrawerContent(
    conversations: List<ConversationEntity>,//所有会话
    // 当前选中的会话 ID
    currentId: Long?,//当前选中的会话ID
    // 回调（Callback）	把一段代码作为参数传给别人，让别人在合适的时候调用
    //需要主代码传递给过来实际参数，我们这里只是形参，用来写方法的而已
    onSelect: (Long) -> Unit,//这些名字都是自己想的哈，不是什么官方背景
    onDelete: (Long) -> Unit,//当触发删除按钮时调用
    onRename: (Long, String) -> Unit,//当触发重命名按钮时调用
    //当触发新建对话按钮时调用
    onNewChat: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    var editingId by remember { mutableStateOf<Long?>(null) } // 当前正在重命名的会话 ID
    var editingTitle by remember { mutableStateOf("") }        // 重命名时的标题

    // 侧边抽屉面板
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp).imePadding(), 
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        content = {
        // ========== 顶部标题栏 ==========
        Column(
                modifier = Modifier
                    .fillMaxWidth()           // 1️⃣ 宽度填满父容器
                    .statusBarsPadding()      // 2️⃣ 顶部留出状态栏高度（系统自动）
                    .padding(horizontal = 20.dp, vertical = 12.dp)  // 3️⃣ 内边距
        ) {
            //第一行标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                //第一个元素贴左边，最后一个元素贴右边，中间的元素均匀分布，空白间距相等
                //在水平方向（左右）上，把子元素之间的空白空间均匀分配，让第一个元素靠左，最后一个靠右，中间的均匀分布。
                horizontalArrangement = Arrangement.SpaceBetween,
                //在垂直方向（上下）上，让所有子元素在竖直方向居中对齐
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 靠左：App 名称
                Text(
                    "Hsiaopu",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

            }
            //第二行空白行
            Spacer(modifier = Modifier.height(4.dp))
            // 第三行副标题
            Text(
                "对话记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)// 水平分隔线

        // ========== 会话列表区域 ==========
        if (conversations.isEmpty()) {
            // 空状态提示，box布局，居中对齐，垂直方向居中对齐
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,//内容在容器中上下左右完全居中。
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    //第一行图标
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    )
                    //第二行空白行
                    Spacer(modifier = Modifier.height(8.dp))
                    //第三行文本
                    Text(
                        "暂无对话记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(//items,item是专门配合 LazyColumn 用的
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {//懒加载列里面的一个遍历列表items，会使用item来渲染每个项，conversations是会话列表对象，key是会话id，content是每个会话的渲染内容
                items(conversations, key = { it.id },itemContent = { conversation ->
                    //conversations是是绘画列表对象，conversation是指某一个会话对象，一个一个来遍历绘制conversations列表的
                    val isSelected = conversation.id == currentId//判断当前会话是否是选中的会话
                    DrawerItem(
                        conversation = conversation,//当前绘制的这个会话项
                        isSelected = isSelected,
                        isEditing = editingId == conversation.id,//判断当前会话是否是编辑中的会话
                        editingTitle = if (editingId == conversation.id) editingTitle else conversation.title,
                        dateFormat = dateFormat,
                        onSelect = { onSelect(conversation.id) },
                        onStartEdit = { editingId = conversation.id; editingTitle = conversation.title },
                        onEditingTitleChange = { editingTitle = it },
                        onConfirmEdit = { onRename(conversation.id, editingTitle); editingId = null },
                        onCancelEdit = { editingId = null },
                        onDelete = { onDelete(conversation.id) }
                    )}) 
                //每个话项之间添加8dp的竖直间距
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
        }
    ) 
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(//抽屉项
    conversation: ConversationEntity,//当前会话
    isSelected: Boolean,//是否选中
    isEditing: Boolean,//是否编辑中
    editingTitle: String,//当前编辑的标题
    dateFormat: SimpleDateFormat,//日期格式
    onSelect: () -> Unit,//当点击项时调用
    onStartEdit: () -> Unit,//当点击编辑按钮时调用
    onEditingTitleChange: (String) -> Unit,//当编辑标题改变时调用
    onConfirmEdit: () -> Unit,//当点击确认按钮时调用
    onCancelEdit: () -> Unit,//当点击取消按钮时调用
    onDelete: () -> Unit//当点击删除按钮时调用
) {
    if (isEditing) {//针对单单一个会话项
        // ========== 编辑模式：内联重命名 ==========
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = editingTitle,//当前编辑的标题
                onValueChange = onEditingTitleChange,//当编辑用 ，更新editingTitle
                               modifier = Modifier.weight(1f),  //占满剩余空间
                singleLine = true,//单行输入
                shape = RoundedCornerShape(8.dp),//圆角矩形
                textStyle = MaterialTheme.typography.bodyMedium//文本样式
            )
            Spacer(modifier = Modifier.width(4.dp))
            // 确认按钮
            IconButton(onClick = onConfirmEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            // 取消按钮
            IconButton(onClick = onCancelEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {//针对单单一个会话项
        // ========== 正常模式：长按触发重命名/删除菜单 ==========
        var showMenu by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(//点击项时调用onSelect，长按项时设置showMenu为true，由于这个参数为mutableStateOf，则会触发重新绘制，从而重组点击事件
                        onClick = onSelect,
                        onLongClick = { showMenu = true }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {


                // 会话标题
                Text(//传递内容，设置即可
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )


                // 长按弹出菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = if (isSystemInDarkTheme()) DarkSurfaceVariant else LightSurfaceVariant,
                    shadowElevation = 0.dp,//关闭阴影
                    tonalElevation = 0.dp//关闭阴影
                ) {
                    // ── 重命名 ──
                    DropdownMenuItem(
                        text = {
                            Text(
                                "重命名",
                                color = if (isSystemInDarkTheme()) DarkOnSurface else LightOnSurface
                            )
                        },
                        onClick = {
                            showMenu = false
                            onStartEdit()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null
                            )
                        }
                    )


                    // ── 删除 ──
                    DropdownMenuItem(
                        text = {
                            Text(
                                "删除",
                                color = ErrorRed
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = ErrorRed
                            )
                        }
                    )
                }
            }
        }
    }}