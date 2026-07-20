package com.example.hsiaopu.ui.screen
// 必须添加这个导入
import androidx.compose.material3.Icon
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hsiaopu.ui.theme.*
import kotlinx.coroutines.delay

// =============================================================================
// 数据模型：OnboardingPage
// =============================================================================
// 每一个 OnboardingPage 代表引导页中的一页幻灯片。
// 引导页通常由多个这样的页面组成，用户左右滑动或点击"下一步"来浏览。
//
// 字段说明：
//   icon        → 页面顶部的图标（Material Design 图标，如 Icons.Filled.Chat）
//   title       → 页面大标题，简短概括功能特点
//   description → 详细描述，支持换行符 \n 将内容分成多行
// =============================================================================
data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

// =============================================================================
// 引导页数据源
// =============================================================================
// 这是一个 List<OnboardingPage>，包含了引导页所有要展示的页面。
// 如果你想增加或减少引导页的数量，只需在这里添加/删除 OnboardingPage 即可，
// 后续的页面指示器、按钮逻辑等都会自动适配页面数量。
//
// =============================================================================
val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Chat,
        title = "自然语言交互",
        description = "只需使用日常用语，即可轻松调用各项系统功能，让设备操作更懂你。"
    ),
    OnboardingPage(
        icon = Icons.Filled.Terminal,
        title = "Shell 控制台",
        description = "为专业用户提供终端环境，允许手动执行自定义的命令行操作。"
    ),
    OnboardingPage(
        icon = Icons.Filled.Build,
        title = "需要 Shizuku 支持",
        description = "若要获取完整的系统级访问权限，请确保您的设备已安装并手动授权 Shizuku。"
    ),
    OnboardingPage(
        icon = Icons.Filled.Warning,
        title = "安全与风险提示",
        description = "允许执行 Shell 命令可能会改变系统状态或带来安全隐患。请确认您了解这些潜在风险并选择继续。"
    )
)

// =============================================================================
// OnboardingScreen — 引导页主界面
// =============================================================================
//
// 整个引导页的启动流程（在 MainActivity.kt 中）：
//
//   1. MainActivity 在首次启动时，调用 SettingsDataStore.isOnboardingCompleted()
//      检查本地存储中是否已标记"引导页已完成"。
//
//   2. 如果返回 false（从未完成过引导），则显示 OnboardingScreen；
//      如果返回 true（已完成过），则跳过引导页，直接进入主界面。
//
//   3. 当用户在引导页中：
//        - 点击"跳过"按钮 → 调用 onComplete()，标记引导完成，进入主界面
//        - 浏览到最后一页并点击"开始使用" → 调用 onComplete()，标记引导完成
//
//   4. onComplete() 在 MainActivity 中做了两件事：
//        a) chatViewModel.dataStore.markOnboardingCompleted()
//           → 将 KEY_ONBOARDING_COMPLETED = true 写入 DataStore（永久存储）
//        b) showOnboarding = false
//           → 关闭引导页，显示主界面
//
//   5. 下次启动 App 时，isOnboardingCompleted() 返回 true，直接跳过引导页。
//
// 关于 DataStore：
//   DataStore 是 Android 官方的轻量级键值存储方案，用于替代旧的 SharedPreferences。
//   它基于 Kotlin 协程，支持异步读写，不会阻塞主线程。
//   存储在 DataStore 中的数据会永久保留，直到用户清除 App 数据。
//
// 参数说明：
//   onComplete → 一个 lambda 回调函数，在引导完成时由本页面调用
//                （MainActivity 传入，负责"标记完成 + 关闭引导页"的逻辑）
// =============================================================================
@Composable
//形参说明：
//   onComplete → 一个 lambda 回调函数，在引导完成时由本页面调用
//                （MainActivity 传入，负责"标记完成 + 关闭引导页"的逻辑）
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    // -------------------------------------------------------------------------
    // 状态变量
    // -------------------------------------------------------------------------
    // pageCount：引导页的总页数，从 onboardingPages 列表自动计算
    val pageCount = onboardingPages.size

    // currentPage：当前显示的是第几页（0 = 第一页，1 = 第二页，...）
    // remember { mutableIntStateOf(0) }：
    //   - remember：告诉 Compose "在界面重组时记住这个值，不要重新初始化"
    //   - mutableIntStateOf：创建一个可被 Compose 观察的整数状态，
    //     当这个值变化时，所有读取它的 Composable 会自动重新渲染（重组）
    var currentPage by remember { mutableIntStateOf(0) }

    // isButtonPressed：当前按钮是否处于"被按下"状态
    // 用于实现按钮的缩放动画（按下时缩小到 0.92 倍，松开时恢复）
    // mutableStateOf(false)：类似于 mutableIntStateOf，但适用于布尔值
    var isButtonPressed by remember { mutableStateOf(false) }



    // =========================================================================
    // 主布局：Box 是全屏容器，Column 是垂直排列的子元素
    // =========================================================================
    Box(
        modifier = Modifier
            .fillMaxSize()    // 填充整个屏幕
            .background(MaterialTheme.colorScheme.background) // 背景色跟随主题
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally // horizontalAlignment：水平对齐方式，子元素水平居中
            // Alignment：对齐；Horizontal：水平的
            ) {
            // -----------------------------------------------------------------
            // 顶部弹性空白：占据可用空间的 15%
            // Modifier.weight(0.15f) 意思是"按 0.15 的比例分配剩余空间"
            // 这样可以在不同屏幕尺寸上自适应，而不是写死一个 dp 值
            // -----------------------------------------------------------------
            Spacer(modifier = Modifier.weight(0.15f))
            //weight：权重，用于在 Column/Row 中分配几成比例的高度或者宽度
            // -----------------------------------------------------------------
            // 页面切换动画区域：AnimatedContent
            // -----------------------------------------------------------------
            // AnimatedContent 是 Compose 提供的切换动画组件。
            // 当 targetState（这里是 currentPage）发生变化时：
            //   1. 旧内容以"滑出 + 淡出"的方式离开屏幕
            //   2. 新内容以"滑入 + 淡入"的方式进入屏幕
            //
            // transitionSpec 定义了动画的具体行为：
            //   - direction：如果用户往前翻（targetState > initialState），向右滑入（200px）；
            //                如果向后翻，向左滑入（-200px）
            //   - slideInHorizontally(tween(400))：水平滑入，持续 400ms
            //   - fadeIn(tween(500))：淡入，持续 500ms
            //   - togetherWith：将"进入动画"和"退出动画"组合在一起执行
            // -----------------------------------------------------------------
            AnimatedContent(
                targetState = currentPage,
                label = "onboarding_page",
                 content = {
                     page ->
                    // 从数据源中取出当前页的数据
                    val data = onboardingPages[page]
                    // 渲染单页内容（图标 + 标题 + 描述）
                    OnboardingPageContent(data = data)
            }
            ) 

            // 底部弹性空白：占据可用空间的 10%
            Spacer(modifier = Modifier.weight(0.1f))

            // -----------------------------------------------------------------
            // 页面指示器（三个小圆点）
            // -----------------------------------------------------------------
            // 作用：告诉用户"你现在在第几页，总共有多少页"
            // 设计：
            //   - 当前页的指示器 → 长条（24dp 宽），高亮色
            //   - 非当前页的指示器 → 小圆点（8dp 宽），半透明
            // animateDpAsState：当宽度变化时，用 300ms 的平滑动画过渡
            // -----------------------------------------------------------------
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp), // 圆点之间间隔 8dp
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                // repeat(pageCount)：循环 pageCount 次，index 从 0 到 pageCount-1
                repeat(pageCount) { index ->
                    val isActive = currentPage == index // 当前指示器是否激活
                    // 动画：激活时宽度 24dp，非激活时 8dp，过渡时间 300ms
                    val width by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "indicator_width"
                    )
                    Box(
                        modifier = Modifier
                            .size(width, 8.dp)          // 宽 = 动画值，高 = 8dp
                            .clip(RoundedCornerShape(4.dp)) // 圆角裁剪（圆角半径 4dp）
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // -----------------------------------------------------------------
            // 按钮区域：左侧"跳过"，右侧"下一步" / "开始使用"
            // -----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween // 左右两端对齐
            ) {
                // =============================================================
                // 左侧按钮：跳过 / 上一页
                // =============================================================
                // 逻辑：
                //   - 第一页（currentPage == 0）→ 显示"跳过"按钮，点击直接完成引导
                //   - 中间页（0 < currentPage < pageCount - 1）→ 显示"上一页"按钮
                //   - 最后一页（currentPage == pageCount - 1）→ 不显示左侧按钮
                // =============================================================
                if (currentPage == 0) {
                    // 第一页：显示"跳过"
                    TextButton(onClick = onComplete) {
                        Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                } else {
                    // 中间页：显示"上一页"
                    TextButton(onClick = { currentPage-- }) {
                        Text("上一页", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } 

                // =============================================================
                // 右侧按钮：下一步 / 开始使用（带按下缩放动画）
                // =============================================================
                // animateFloatAsState：创建按钮缩放动画
                //   - 未按下时：scale = 1f（原始大小）
                //   - 按下时：scale = 0.92f（缩小到 92%），实现"按压感"
                // =============================================================
                val buttonScale by animateFloatAsState(
                    targetValue = if (isButtonPressed) 0.92f else 1f,
                    animationSpec = tween(300),
                    label = "btn_scale"
                )
                Button(
                    onClick = {
                        // 1. 设置按压状态为 true → 触发缩小动画
                        isButtonPressed = true
                        // 2. 判断当前位置：
                        //    - 不是最后一页 → currentPage++，切换到下一页
                        //    - 是最后一页 → 调用 onComplete()，引导结束
                        if (currentPage < pageCount - 1) {
                            currentPage++
                        } else {
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(24.dp), // 圆角 24dp 的按钮
                    modifier = Modifier
                        .height(48.dp)
                        .scale(buttonScale) // 应用缩放动画
                ) {
                    Text(
                        // 按钮文字：非最后页显示"下一步"，最后页显示"开始使用"
                        text = if (currentPage < pageCount - 1) "下一步" else "开始使用",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 底部间距
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // =========================================================================
    // 按钮按压状态重置
    // =========================================================================
    // LaunchedEffect 是 Compose 的副作用 API：
    //   - 当 key（这里是 isButtonPressed）变为 true 时，启动协程
    //   - 等待 150ms 后将 isButtonPressed 重置为 false
    //   - 这样就实现了"按下 → 缩小 → 150ms 后 → 恢复"的动画效果
    //
    // 注意：如果 key 值没变，LaunchedEffect 不会重新执行。
    //       这里只有当 isButtonPressed 从 false 变为 true 时才触发。
    // =========================================================================
    LaunchedEffect(isButtonPressed) {
        //Launched：启动之后；Effect；：效果
        // 它的位置不决定执行逻辑，Compose 只关心它是否在同一个 @Composable 函数里。
        //按钮防抖，防止快速连点导致重复点击
    // 这是“附带作用”：显示 UI 的同时，顺便加载数据
        if (isButtonPressed) {//触发条件，它变化时 LaunchedEffect 才会执行
            delay(150)//等待按钮缩放动画播放完再重置状态
            isButtonPressed = false
        }
    }
}

// =============================================================================
// OnboardingPageContent — 单页引导内容
// =============================================================================
// 负责渲染一个引导页的内容：图标、标题、描述
//
// 参数说明：
//   data            → OnboardingPage 数据对象，包含图标、标题、描述、渐变色
// =============================================================================
@Composable
private fun OnboardingPageContent(
    data: OnboardingPage
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp) // 引导页水平边距 32dp（规范三）
    ) {
        // ---------------------------------------------------------------------
        // 图标区域：圆形背景 + 呼吸脉动动画
        // ---------------------------------------------------------------------
        // Box 是一个可以叠加子元素的容器（类似 FrameLayout）
        // 这里把图标放在一个灰色圆形背景上
        // ---------------------------------------------------------------------
        Box(
            modifier = Modifier
                .size(120.dp)                          // 容器 120x120 dp
                .clip(CircleShape)                      // 裁剪成圆形
                .background(SystemGray4),
            contentAlignment = Alignment.Center         // 图标居中
        ) {
         
            // 图标：大小为 56dp * pulse，tint 为白色
            Icon(
                painter = rememberVectorPainter(data.icon),
                contentDescription = data.title,
                modifier = Modifier.size(56.dp),
                tint = White
            )
        }

        // 图标和标题之间的间距
        Spacer(modifier = Modifier.height(32.dp))

        // ---------------------------------------------------------------------
        // 标题文字
        // ---------------------------------------------------------------------
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineMedium, // Material3 标题中号样式
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center    // 居中对齐
        )

        // 标题和描述之间的间距
        Spacer(modifier = Modifier.height(16.dp))

        // ---------------------------------------------------------------------
        // 描述文字
        // ---------------------------------------------------------------------
        Text(
            text = data.description,
            style = MaterialTheme.typography.bodyLarge,     // Material3 正文大号样式
            color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要文字颜色
            textAlign = TextAlign.Start,
            lineHeight = 26.sp   // 行高 26sp，让多行文字有舒适的阅读间距
        )
    }
}
