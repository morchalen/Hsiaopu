package com.example.hsiaopu
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.example.hsiaopu.ui.screen.HomeScreen
import com.example.hsiaopu.ui.screen.OnboardingScreen
import com.example.hsiaopu.ui.screen.SettingsScreen
import com.example.hsiaopu.ui.screen.ShellScreen
import com.example.hsiaopu.ui.theme.*
import com.example.hsiaopu.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ComponentActivity.setContent 这个扩展函数的 Lambda 语法写的哈
        setContent { // 为当前 Activity 加载 Compose UI 内容（往 Activity 里填充页面内容）
            HsiaopuTheme { // 套主题
                MainScreen() // 画 UI
            }
        }
    }
}

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,   // Compose 的矢量图标类型
    val unselectedIcon: ImageVector, // Compose 的矢量图标类型
    val index: Int
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class) // 知情同意书："我自愿使用还在实验阶段的 API"
@Composable
fun MainScreen() {
    // ViewModel：给 View（视图）准备的 Model（模型）
    val chatViewModel: ChatViewModel = hiltViewModel() // 定义一个 ChatViewModel 类型的对象，名字叫 chatViewModel
    // 梦的开始，开始逐级注入依赖了

    val uiState by chatViewModel.uiState.collectAsState() // 把 uiState 的值，委托给后面这个函数 collectAsState 来赋值
    // 从 ViewModel 拿数据，数据变了 UI 自动刷新

    val adaptiveInfo = currentWindowAdaptiveInfo() // 从系统拿窗口信息，判断屏幕大小

    var showOnboarding by remember { mutableStateOf<Boolean?>(null) } // null = 加载中，不闪屏
    val scope = rememberCoroutineScope()

    // 检查是否已完成引导页（异步读取 DataStore）
    LaunchedEffect(Unit) {
        val completed = chatViewModel.dataStore.isOnboardingCompleted()
        showOnboarding = !completed // 未完成 → true（显示引导页），已完成 → false（进入主界面）
    }

    // 加载中：不渲染任何内容，等 DataStore 读完
    if (showOnboarding == null) return

    // 如果引导页还未完成，显示引导页界面
    if (showOnboarding == true) {
        // 进入，执行 OnboardingScreen 组件，显示引导页界面
        // OnboardingScreen 组件的参数：
        // onComplete：引导页完成后的回调

        // 这里使用 Lambda 语法写的哈
        // OnboardingScreen 是一个引导页组件，onComplete 是引导页完成后的回调
        OnboardingScreen(onComplete = {
            // 先执行的是 showOnboarding = false，然后协程才在后台慢慢存数据。

            // scope 是与当前 Composable 生命周期绑定的协程作用域
            // launch 启动一个协程，在该协程中执行挂起函数（suspend function）
            // 1️⃣ 启动一个协程去存数据（不阻塞）
            scope.launch {
                // 将 "引导页已完成" 的标记（true）持久化存储到本地 DataStore 中
                // 这样下次启动 App 时，读取到的就是 true，就不会再显示引导页了
                chatViewModel.dataStore.markOnboardingCompleted() // 这个是来自 SettingsDataStore.kt 的一个被标记了挂起的协程函数
                // 这个函数的作用是将 "引导页已完成" 的标记（true）持久化存储到本地 DataStore 中，结合前面的触发函数 onComplete，这个意思就是当引导页完成之后，将 "引导页已完成" 的标记（true）存储到本地 DataStore 中
            }
            // 2️⃣ 立即执行这句，不等协程！ 👈
            // 数据存储完成后，切换状态，隐藏引导页，进入主界面
            showOnboarding = false // showOnboarding 是一个 mutableStateOf，它的改变会触发重组（Recomposition）。执行 showOnboarding = false 后，Compose 会立即重新执行 setContent 里的代码，跳过引导页，直接进入主界面。
        })
        return // 这个跟 if 没关系哈，别跟 for 搞混了，这个地方纯粹是为了退出这个 OnboardingScreen 组件，因为对于代码来说，组件显示关闭了，但是代码还在，进入其他的界面。
        // 这里返回去的是 MainScreen()，而不是 OnboardingScreen()，因为刚刚已经设置了跳过引导了，返回重新渲染 MainScreen()，跳过引导页，直接进入主界面。
    }

    val isExpanded = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    if (isExpanded) {
        AdaptiveLayout(chatViewModel = chatViewModel)
    } else {
        PhoneLayout(chatViewModel = chatViewModel)
    }
}
// ========== 导航项数据类 ==========
data class NavItem(
    val index: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

// ==================== 手机布局 ====================

@Composable
fun PhoneLayout(chatViewModel: ChatViewModel) {
    val navItems = listOf(
        BottomNavItem("对话", Icons.Filled.Chat, Icons.Outlined.Chat, 0),
        BottomNavItem("Shell", Icons.Filled.Terminal, Icons.Outlined.Terminal, 1),
        BottomNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings, 2)
    )

    // 使用 PagerState 管理页面切换，全面替代 selectedTab 变量
    val pagerState = rememberPagerState(initialPage = 0) { navItems.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                navItems.forEach { item ->
                    val isSelected = pagerState.currentPage == item.index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            // 点击时平滑滚动到对应页面，或者用 scrollToPage(item.index) 瞬间切换
                            scope.launch { pagerState.animateScrollToPage(item.index) }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // ============ 内容容器 ============
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // ============ 使用高性能的 HorizontalPager 替代 AnimatedContent ============
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false // 如果你不想让用户用手指左右滑屏幕切页面，设为 false；想支持就设为 true
            ) { page ->
                when (page) {
                    0 -> HomeScreen(viewModel = chatViewModel)
                    1 -> ShellScreen(
                        settingsDataStore = chatViewModel.dataStore,
                        shellHistoryRepository = chatViewModel.shellRepo,
                        shellCommandBus = chatViewModel.cmdBus,
                        chatClient = chatViewModel.chatClient,
                        appSettings = chatViewModel.getCurrentSettings()
                    )
                    2 -> SettingsScreen(viewModel = chatViewModel)
                }
            }
        }
    }
}

// ==================== 大屏幕布局 ====================

@Composable
fun AdaptiveLayout(chatViewModel: ChatViewModel) {
    val uiState by chatViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // ========== 1. 导航项数据 ==========
    val navItems = listOf(
        NavItem(0, Icons.Filled.Chat, Icons.Outlined.Chat, "对话"),
        NavItem(1, Icons.Filled.Terminal, Icons.Outlined.Terminal, "Shell"),
        NavItem(2, Icons.Filled.Settings, Icons.Outlined.Settings, "设置")
    )
Scaffold { innerPadding ->
    Row(modifier = Modifier.fillMaxSize()
                .padding(innerPadding)) {
        // ========== 2. 侧边导航栏 ==========
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            // 循环创建导航项
            navItems.forEach { item ->
                NavigationRailItem(
                    selected = selectedTab == item.index,
                    onClick = { selectedTab = item.index },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == item.index) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // ========== 3. 内容区域（所有页面统一动画） ==========
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 200 else -200
                    (slideInHorizontally(tween(400)) { direction } + fadeIn(tween(400)))
                        .togetherWith(
                            slideOutHorizontally(tween(300)) { -direction } + fadeOut(tween(250))
                        )
                },
                label = "tab_content_adaptive"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(viewModel = chatViewModel, isTablet = true)
                    1 -> ShellScreen(
                        settingsDataStore = chatViewModel.dataStore,// 传递数据存储实例
                        shellHistoryRepository = chatViewModel.shellRepo,// 传递历史记录仓库实例
                        shellCommandBus = chatViewModel.cmdBus,// 传递命令总线实例
                        chatClient = chatViewModel.chatClient,// 传递 AI 提供器注册器实例
                        appSettings = chatViewModel.getCurrentSettings()// 传递当前应用设置实例
                    )
                    2 -> SettingsScreen(viewModel = chatViewModel)
                }
            }
        }
    }}
}

// ==================== 调试预览部分 ====================

@Preview(showBackground = true, widthDp = 1280) // 预览在 1280dp 宽度下的效果
@Composable
fun MainScreenPreview() {
    HsiaopuTheme {
        MainScreen()
    }
}
