// ──────────────────────────────────────────────────────────────────────────────
// ChatViewModel.kt
//
// 这个文件是 Hsiaopu 的"大脑"——它负责管理对话页面的一切数据和逻辑。
// 在本文件中你会学到：
//   1. 什么是 ViewModel（词根拆解 + 通俗比喻）
//   2. 什么是 MVVM 架构
//   3. 这个 App 的对话流程（从用户输入到 AI 回复，再到执行 Shell 命令）
// ──────────────────────────────────────────────────────────────────────────────

// ==============================
// 第一关：包的声明
// ==============================
// "package" 就像快递的地址，告诉系统这个文件住在哪个文件夹。
// 这个住在 com.example.hsiaopu.viewmodel 文件夹里。
package com.example.hsiaopu.viewmodel

// ==============================
// 第二关：导入语句（import）
// ==============================
// import 就像去超市采购——把别人写好的工具拿过来用，不用自己从头造。
// 下面每一行都是在"引入" Android 系统或别人写好的代码库。

import android.content.Context
// Context 是 Android 的"上下文"，可以理解为 App 的"身份证"——
// 有了它，你才能访问系统的各种服务（比如检查网络、读取设置）。

import android.net.ConnectivityManager
// ConnectivityManager 是网络管家——专门负责检查手机有没有联网。

import android.net.NetworkCapabilities
// NetworkCapabilities 是网络的"能力说明书"——告诉你当前网络是 WiFi 还是流量。

import androidx.lifecycle.ViewModel
// ★★★ 重点来了：ViewModel ★★★
// ViewModel 由两个单词组成：
//   View = "视图" = 你看到的界面（屏幕上的按钮、文字、输入框等）
//   Model = "模型/数据" = 背后存的数据和逻辑
// 合起来 ViewModel = "视图的数据管家"
//
// 用开餐馆打比方：
//   View（视图）= 前台服务员 / 菜单 / 餐桌
//   Model（数据）= 后厨的食材 / 配方 / 库存账本
//   ViewModel = "传菜员"——
//     1) 前台（View）告诉传菜员"客人要一份炒饭"
//     2) 传菜员（ViewModel）去后厨（Model）拿食材
//     3) 传菜员把做好的菜端回前台给客人
//
// 通俗理解：
//   ViewModel 就是"中间人"——屏幕想显示什么、用户点了一下按钮该干嘛，
//   都由 ViewModel 来处理，这样屏幕（View）就只需要专心画界面，
//   不需要操心数据从哪来、怎么存。

import androidx.lifecycle.viewModelScope
// viewModelScope 是 ViewModel 自带的"工作区域"。
// 好比厨房里有一个指定的"操作台"——所有需要等一会儿才能完成的任务
// （比如发网络请求、查数据库），都放在这个操作台上做。
// 当 ViewModel 被销毁时（比如 App 关闭），操作台上的任务也会自动取消，
// 不会造成资源浪费。

// ---------- 项目内部导入（Hsiaopu 自己写的代码） ----------
import com.example.hsiaopu.data.AppSettings
// AppSettings 是"设置清单"——记录了用户填的 API Key、服务器地址、模型名称等。
// 好比餐厅的"顾客口味偏好卡"：喜欢什么辣度、有什么忌口。

import com.example.hsiaopu.data.ChatMessage
// ChatMessage 是"聊天消息"的格式——每一条消息都有三个信息：
//   role（身份：是用户说的还是 AI 说的）
//   content（内容：具体说了什么话）
//   timestamp（时间戳：什么时候说的）

import com.example.hsiaopu.data.SettingsDataStore
// SettingsDataStore 是"设置保险柜"——把用户的设置存到手机本地，
// 即使关掉 App 再打开，设置也不会丢。

import com.example.hsiaopu.data.ThemeSettings
// ThemeSettings 是"主题设置"——记录用户喜欢深色模式还是浅色模式，
// 喜欢什么颜色的主题。

import com.example.hsiaopu.data.UsageInfo
// UsageInfo 是"用量信息"——记录用了多少 token（AI 的计费单位），
// 花了多少钱。

import com.example.hsiaopu.data.local.ConversationEntity
// ConversationEntity 是"对话记录"的数据库格式。
// Entity = 实体 = 数据库里的一张表格。
// 每一条记录对应一个对话（比如"今天和 AI 的聊天"）。

import com.example.hsiaopu.data.local.MessageEntity
// MessageEntity 是"消息记录"的数据库格式——每个对话里的每一条消息。

import com.example.hsiaopu.data.ShellCommandBus
// ShellCommandBus 是"Shell 命令总线"——好比一条传送带：
// 对话页面把要执行的 Shell 命令放在传送带上，
// Shell 页面从传送带上取走命令去执行，再把结果放回来。

import com.example.hsiaopu.data.repository.ChatRepository
// ChatRepository 是"聊天仓库管理员"——
// 管理对话和消息的存取（存到数据库、从数据库读取）。

import com.example.hsiaopu.data.repository.ShellHistoryRepository
// ShellHistoryRepository 是"Shell 历史仓库管理员"——
// 管理 Shell 命令执行记录的存取。

import com.example.hsiaopu.network.AiProviderRegistry
// AiProviderRegistry 是"AI 供应商登记处"——
// 就像一个电话簿，记录着各个 AI 服务商（DeepSeek、OpenAI 等）
// 的联系方式和功能。

import com.example.hsiaopu.network.ProviderInfo
// ProviderInfo 是某个 AI 供应商的"名片"——
// 上面写着供应商的名字、支持哪些功能等。

import com.example.hsiaopu.network.UsageStats
// UsageStats 是"用量统计表"——记录总共用了多少 token 和花了多少钱。

import com.example.hsiaopu.system.SysResult
// SysResult 是"系统操作结果"——执行一个 Shell 命令后，
// 返回的"成功/失败"、输出信息等。

import com.example.hsiaopu.system.SystemControlExecutor
// SystemControlExecutor 是"系统控制执行器"——
// 通过 Shizuku（一个 Android 权限工具）执行系统命令。

// ---------- Hilt 依赖注入 ----------
import dagger.hilt.android.lifecycle.HiltViewModel
// HiltViewModel 是 Hilt 框架提供的"ViewModel 专属标签"。
// Hilt 是 Android 的"自动配送员"——它会自动帮你创建对象，
// 你不用自己手动 new 对象。
// HiltViewModel 告诉 Hilt："这个 ViewModel 需要配送服务，请关照它。"

import dagger.hilt.android.qualifiers.ApplicationContext
// ApplicationContext 是一个"特殊标签"——
// 它告诉 Hilt："我要的是 App 级别的身份证（Context），
// 不要弄错成 Activity 级别的。"

// ---------- Kotlin 协程 ----------
import kotlinx.coroutines.flow.*
// Flow 是 Kotlin 里的"数据河流"——
// 数据像河水一样源源不断地流过来，你可以在岸边（界面）随时取水。
// StateFlow 是其中一种特殊河流——它永远保存着最新的一份数据。
// MutableStateFlow 是可以手动往里面放新数据的河流。

import kotlinx.coroutines.launch
// launch 是"发射"的意思——把一个任务发射出去，让它异步执行
// （不需要等它完成，程序可以继续往下走）。

// ---------- Java 标准库 ----------
import javax.inject.Inject
// Inject 是"注入"标签——告诉 Hilt："请帮我自动创建这个对象并送过来。"

// =============================================================================
// 第三关：ChatUiState —— 整个聊天界面的"快照"
// =============================================================================
// 想象你在看一部电影，按下暂停键——这时屏幕上显示的所有画面就是一个"状态"。
// ChatUiState 就是聊天界面的"暂停快照"，包含了这一刻所有的信息。
//
// data class 是 Kotlin 中的"数据类"，专门用来装数据，就像超市里的购物篮。
//
// 为什么不用普通 class？
//   因为 data class 自动帮你实现了：比较是否相等、复制一份修改等常用功能。
// =============================================================================
data class ChatUiState(
    // conversations：对话列表（对话记录里所有的对话）
    // List<ConversationEntity> 就像微信聊天列表——
    // 每个条目显示一个对话的标题和最后一条消息的时间。
    val conversations: List<ConversationEntity> = emptyList(),//定义并且初始化一个列表，类型是ConversationEntity，名字是conversations

    // currentConversationId：当前正在聊的是哪个对话？
    // Long? 表示"可能是 null"——如果用户还没选对话，这里就是 null。
    val currentConversationId: Long? = null,

    // messages：当前对话里的所有消息
    val messages: List<ChatMessage> = emptyList(),

    // isLoading：AI 是不是正在回复？
    // true = AI 正在"打字"，界面上应该显示一个加载转圈
    // false = AI 回复完了，或者还没开始
    val isLoading: Boolean = false,

    // streamingContent：AI 正在一个字一个字往外蹦的内容
    // 就像 ChatGPT 那种"流式输出"——AI 一边生成一边显示，
    // 用户不用等全部生成完才能看到。
    val streamingContent: String = "",

    // error：出错了？如果有错误信息就存在这里
    // null = 一切正常
    // "Network error" = 网络出问题了
    val error: String? = null,

    // tokenStats：统计信息——用了多少 token，花了多少钱
    val tokenStats: UsageStats = UsageStats(),

    // isOnline：手机现在有没有联网？
    // true = 已联网，false = 断网了
    // val isOnline: Boolean = true
)

// =============================================================================
// 第四关：ChatViewModel —— 聊天界面的"总指挥"
// =============================================================================
//
// 注解（Annotation）@HiltViewModel：
//   就像给这个类贴了一个标签，告诉 Hilt 框架：
//   "这个 ViewModel 需要你帮忙创建和管理哦。"
//
// class ChatViewModel @Inject constructor(...) : ViewModel()
// @Inject constructor  →  修饰 ChatViewModel 自己的构造函数
//这里的父类ViewModel是无参构造函数，所以这里也需要无参构造函数，他会在自己的代码构建，我们不管



// ❌ 没有 Hilt（手动传参）
// kotlin
// class Student(
//     val className: String,
//     val score: Int,
//     name: String,
//     age: Int,
//     sex: String
// ) : Person(name, age, sex)

// // 每次创建都要手动传所有参数
// fun main() {
//     val student1 = Student("高三1班", 95, "小明", 18, "男")
//     val student2 = Student("高三2班", 88, "小红", 17, "女")
//     val student3 = Student("高三1班", 92, "小刚", 19, "男")
//     // 每次都写一大堆 😱
// }
// ✅ 有 Hilt（自动传参）
// kotlin
// class Student @Inject constructor(
//     val className: String,
//     val score: Int,
//     name: String,
//     age: Int,
//     sex: String
// ) : Person(name, age, sex)

// // 使用时：Hilt 自动传参
// class SomeActivity {
//     val student: Student by inject()  
//     // ↑ Hilt 自动把 "高三1班、95、小明、18、男" 传进去
//     // 你啥都不用写！
// }


//   这是一个类，名字叫 ChatViewModel，它继承了 ViewModel（别人写好的基础框架）。
//   constructor 是构造函数——就是"创建这个对象时需要什么原料"。
//   @Inject constructor 说明："这些原料由 Hilt 自动配送，不需要手动准备。"
//
// 冒号后面的 ViewModel() 表示"我是从 ViewModel 继承来的"——
//   好比"学生"继承自"人类"——学生有人类的所有特性，再加上自己的特性。
//   ChatViewModel 有 ViewModel 的所有基础功能（比如自动管理生命周期），
//   再加上自己特有的功能（聊天、设置等）。
// =============================================================================
@HiltViewModel
class ChatViewModel @Inject constructor(
    // ↓↓↓ 以下都是"构造函数参数"——创建 ChatViewModel 时需要的"原料" ↓↓↓

    private val repository: ChatRepository,
    // repository 是"聊天仓库"——所有和数据库打交道的事都委托给这个仓库。
    // 比如"保存一条新消息"、"读取历史对话列表"。

    private val providerRegistry: AiProviderRegistry,
    // providerRegistry 是"AI 服务商登记处"——记录着所有的 AI 服务商。
    // 就像手机通讯录，存着 DeepSeek、OpenAI 等各家 AI 的联系方式。

    private val settingsDataStore: SettingsDataStore,
    // settingsDataStore 是"设置保险柜"——把用户的设置读到内存里，
    // 或者把内存里的设置存回手机本地。

    private val shellHistoryRepository: ShellHistoryRepository,
    // shellHistoryRepository 是"Shell 历史仓库"——管理 Shell 命令历史记录的存取。

    private val shellCommandBus: ShellCommandBus,
    // shellCommandBus 是"Shell 命令传送带"——用来把命令发送给 Shell 页面，
    // 以及从 Shell 页面接收执行结果。

    @ApplicationContext private val context: Context
    // @ApplicationContext 标签 + Context：App 的"身份证"。
    // 有了它，ViewModel 才能调用系统的服务，比如检查网络是否连接。
) : ViewModel() {//继承一个类时，必须调用它的构造函数，所以必须加括号 ()；有参就(xx,xxx)，没有参就()；继承接口不用括号

    // ==========================================================================
    // 第五关：公开的快捷通道（public properties）
    // ==========================================================================
    // 下面的几个属性都是"只读的快捷方式"——
    // 其他页面（UI）可以通过这些快捷方式来访问 ViewModel 内部的对象。
    //
    // get() 是 Kotlin 的"自定义 getter"——不直接返回变量值，
    // 而是执行一段代码后返回结果。
    // 这里的 get() = settingsDataStore 意思是：
    // "当别人访问 dataStore 时，实际上返回的是 settingsDataStore 这个对象。"
    // ==========================================================================

    /** dataStore：让其他页面也能读写"设置保险柜"（比如标记新手引导是否已看完）。 */
    val dataStore: SettingsDataStore get() = settingsDataStore

    /** shellRepo：让其他页面（比如 Shell 页面）也能访问 Shell 执行记录。 */
    val shellRepo: ShellHistoryRepository get() = shellHistoryRepository

    /** cmdBus：让 Shell 页面也能使用"命令传送带"来收发消息。 */
    val cmdBus: ShellCommandBus get() = shellCommandBus

    /** aiProvider：让其他页面（比如智能生成命令）也能调用 AI 服务。 */
    val aiProvider: AiProviderRegistry get() = providerRegistry

    /**
     * getCurrentSettings()：获取当前最新的设置。
     * 有些 UI 代码需要读取当前的 API 设置等，通过这个方法获取快照。
     */
    fun getCurrentSettings(): AppSettings = _settings.value

    // ==========================================================================
    // 第六关：状态变量（State variables）
    // ==========================================================================
    // 下面的几个变量都以 _ 开头，这是 Kotlin 的一个命名惯例——
    // _xxx 表示"这是私有的（private），不对外暴露"。
    //
    // MutableStateFlow = "可变的状态河流"——
    //   你可以往里面写入新数据（就像往河里倒水）。
    // StateFlow = "只读的状态河流"——
    //   别人只能读，不能写。
    //
    // 编码惯例：
    //   private val _xxx = MutableStateFlow(...)  ← 私有的，可以读写
    //   val xxx: StateFlow<...> = _xxx.asStateFlow()  ← 公开的，只读
    //
    // 这样设计的好处：只有 ViewModel 自己能修改状态，其他页面只能读取，
    // 保证了数据不会被乱改。
    // ==========================================================================

    /**
     * _uiState：聊天界面的"完整快照"。
     * 包含：对话列表、当前选中的对话、消息列表、加载状态、错误信息等。
     * 这是最重要的状态——界面上的几乎所有内容都从这里读取。
     */
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * _settings：用户的"设置"。
     * 包括：API Key（密钥）、服务器地址、模型名称、系统提示词等。
     */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * _themeSettings：用户的"主题设置"。
     * 包括：深色/浅色模式、强调色、字体大小等。
     */
    private val _themeSettings = MutableStateFlow(ThemeSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    /**
     * _providers：可用的 AI 服务商列表。
     * 比如：DeepSeek、OpenAI 等各家 AI 服务商的信息。
     */
    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers: StateFlow<List<ProviderInfo>> = _providers.asStateFlow()

    // ==========================================================================
    // 第七关：初始化代码块（init block）
    // ==========================================================================
    // init 是一个特殊的代码块——当这个类的对象被创建时，init 里的代码会自动执行。
    // 就像你进餐厅坐下，服务员会自动给你倒一杯水——不需要你特意说"给我倒水"。
    //
    // 这里 init 做了四件事（按顺序）：
    //   1. 获取所有 AI 服务商的名片
    //   2. 从"设置保险柜"读取用户设置
    //   3. 从"设置保险柜"读取主题设置
    //   4. 从本地数据库加载历史对话
    //   5. 启动网络状态监听器（每 5 秒检查一次有没有联网）
    // ==========================================================================
    init {
        // 第一步：获取所有 AI 服务商的信息
        _providers.value = providerRegistry.getAllProviders()

        // 第二步：监听"用户设置"的变化
        // viewModelScope.launch { ... } 意思是：
        // 在 ViewModel 的工作区域里，启动一个异步任务。
        // launch 启动的任务不会阻塞主线程——App 不会卡住。
        //
        // settingsDataStore.settingsFlow.collect { ... } 意思是：
        // 持续监听"设置保险柜"的数据流，每次有变化时，
        // 就把最新值更新到 _settings 中。
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { _settings.value = it }
        }

        // 第三步：监听"主题设置"的变化
        viewModelScope.launch {
            settingsDataStore.themeSettingsFlow.collect { _themeSettings.value = it }
        }

        // 第四步：监听"对话列表"的变化
        // repository.getAllConversations() 从数据库读取所有对话
        // .collect { ... } 持续监听变化
        // _uiState.update { it.copy(conversations = conversations) }
        //   用 Kotlin 的 update 方法，把最新的对话列表更新到状态中。
        viewModelScope.launch {
            repository.getAllConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }

        // 第五步：持续监控网络状态
        // while (true) 是个无限循环——每 5 秒检查一次有没有联网。
        // ConnectivityManager 是系统自带的"网络管家"。
        // getNetworkCapabilities(it) 检查当前网络的能力（能不能上网）。
//        viewModelScope.launch {
//            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
//            while (true) {
//                val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
//                _uiState.update { it.copy(isOnline = caps != null) }
//                kotlinx.coroutines.delay(5000)  // 等 5 秒再检查一次
//            }
//        }
    }

    // ==========================================================================
    // 第八关：对话管理（Conversation Management）
    // ==========================================================================
    // 下面这几个方法都是关于"对话"的基本操作——
    // 创建新对话、选择对话、删除对话、重命名对话。
    //
    // 就像微信聊天列表：你可以建群、点开某个群聊、删除群聊、改群名。
    // ==========================================================================

    /**
     * createNewConversation()：创建一个新的空白对话。
     * 调用仓库（ChatRepository）在数据库里新加一条记录，
     * 然后立刻切换到新创建的对话。
     *
     * 点击"新建对话"按钮时就会触发这个方法。
     */
    fun createNewConversation() {
        viewModelScope.launch {
            val id = repository.createConversation()  // 在数据库里创建新对话
            selectConversation(id)  // 切换到新建的对话
        }
    }

    /**
     * selectConversation(id)：切换到指定的对话。
     * 当用户点击对话列表中的某一条时触发。
     *
     * 做了两件事：
     *   1. 更新状态中的 currentConversationId
     *   2. 从数据库加载该对话的所有消息
     *
     * @param id 要切换到的对话的 ID（数据库里的唯一编号）
     */
    fun selectConversation(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentConversationId = id, error = null) }
            repository.getMessagesByConversation(id).collect { entities ->
                val messages = entities.map { ChatMessage(it.role, it.content, it.timestamp) }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    /**
     * deleteConversation(id)：删除指定的对话。
     * 删除后如果当前正在显示的就是这个对话，
     * 会自动跳转到下一个可用的对话。
     *
     * @param id 要删除的对话的 ID
     */
    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)  // 从数据库删除
            // 如果当前显示的就是被删除的对话
            if (_uiState.value.currentConversationId == id) {
                // 找列表中的下一个对话
                val next = _uiState.value.conversations.firstOrNull { it.id != id }
                if (next != null) selectConversation(next.id)  // 跳转到下一个
                else _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
                // 如果没有其他对话了，就清空当前状态
            }
        }
    }

    /**
     * renameConversation(id, title)：重命名对话。
     * 把指定对话的标题改成新的名称。
     *
     * @param id 要改名的对话 ID
     * @param title 新的标题
     */
    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch { repository.updateConversationTitle(id, title) }
    }

    // ==========================================================================
    // 第九关：导入/导出（Export / Import）
    // ==========================================================================
    // 这两个方法可以把对话导出成 Markdown（.md）或 JSON 格式。
    // Markdown 是人类可读的纯文本格式（用 # 表示标题，** 表示加粗）。
    // JSON 是电脑程序容易解析的数据格式。
    // ==========================================================================

    /**
     * exportConversationAsMarkdown(id)：把指定对话导出为 Markdown 格式。
     *
     * Markdown 是什么？
     *   一种纯文本格式，用简单的符号标记排版。
     *   比如 # 表示"标题"，**文字** 表示"加粗"。
     *   GitHub 和很多笔记软件都支持这种格式。
     *
     * 返回结果是一段格式化的文本，用户可以复制保存。
     *
     * @param id 要导出的对话 ID
     * @return Markdown 格式的字符串
     */
    fun exportConversationAsMarkdown(id: Long): String {
        val messages = _uiState.value.messages
        val conv = _uiState.value.conversations.find { it.id == id }
        return buildString {  // buildString 是 Kotlin 的"字符串建造器"
            appendLine("# ${conv?.title ?: "Conversation"}")  // 第一行：标题
            appendLine()
            messages.forEach { msg ->
                val role = if (msg.role == "user") "You" else "AI"  // 身份
                appendLine("**$role**:")       // **You**: 或 **AI**:
                appendLine(msg.content)        // 消息内容
                appendLine()
            }
        }
    }

    /**
     * exportConversationAsJson(id)：把指定对话导出为 JSON 格式。
     *
     * JSON 是程序之间交换数据时常用的格式，结构简单：
     *   {"key1": "value1", "key2": "value2"}
     *
     * @param id 要导出的对话 ID
     * @return JSON 格式的字符串
     */
    fun exportConversationAsJson(id: Long): String {
        val conv = _uiState.value.conversations.find { it.id == id }
        val messages = _uiState.value.messages.map { msg ->
            // 把每一条消息转成 JSON 格式的文本
            """{"role":"${msg.role}","content":${escapeJson(msg.content)},"timestamp":${msg.timestamp}}"""
        }
        return """{"title":"${conv?.title ?: ""}","messages":[${messages.joinToString(",")}]}"""
    }

    /**
     * escapeJson(s)：对 JSON 中的特殊字符进行"转义"。
     *
     * 为什么需要转义？
     *   如果消息内容里有双引号（"），直接放进 JSON 里就会破坏格式。
     *   就像做饭时如果你要在汤里加"汤"字，得加引号说"这是汤"一样。
     *   转义就是在特殊字符前面加反斜杠（\），告诉程序：
     *   "这个引号是内容的一部分，不是格式的一部分。"
     *
     * @param s 原始字符串
     * @return 转义后的字符串（外面带了双引号）
     */
    private fun escapeJson(s: String): String {
        return "\"" + s.replace("\\", "\\\\")   // 把 \ 变成 \\
            .replace("\"", "\\\"")              // 把 " 变成 "
            .replace("\n", "\\n")               // 把换行变成 \n
            .replace("\r", "\\r") + "\""          // 把回车变成 \r
    }

    // ==========================================================================
    // 第十关：设置管理（Settings）
    // ==========================================================================
    // 下面一连串的方法都是"设置"的读写操作——
    // 每一个设置都对应两个动作：
    //   1. 更新内存中的值（_settings.update { ... }）
    //   2. 持久化到本地（settingsDataStore.xxx(...)）
    //
    // 为什么内存和本地都要存？
    //   内存读取快，但 App 关闭就没了；本地存储慢，但关机重启还在。
    //   同时做两者，既保证界面响应快，又保证设置不会丢失。
    // ==========================================================================

    /** 更新 API Key（AI 服务的密钥）。没有它，AI 不会理你。 */
    fun updateApiKey(key: String) {
        _settings.update { it.copy(apiKey = key) }
        viewModelScope.launch { settingsDataStore.updateApiKey(key) }
    }

    /** 更新 API 服务器地址。比如 https://api.deepseek.com/v1 */
    fun updateApiEndpoint(endpoint: String) {
        _settings.update { it.copy(apiEndpoint = endpoint) }
        viewModelScope.launch { settingsDataStore.updateApiEndpoint(endpoint) }
    }

    /** 更新模型名称。比如 deepseek-v4-flash 或 gpt-4o */
    fun updateModelName(model: String) {
        _settings.update { it.copy(modelName = model) }
        viewModelScope.launch { settingsDataStore.updateModelName(model) }
    }

    /** 更新系统提示词。告诉 AI"你是什么角色"、"应该怎么做"。 */
    fun updateSystemPrompt(prompt: String) {
        _settings.update { it.copy(systemPrompt = prompt) }
        viewModelScope.launch { settingsDataStore.updateSystemPrompt(prompt) }
    }

    /**
     * 更新温度参数（temperature）。
     * 温度控制 AI 回答的"创造力"——数值越高，AI 说话越天马行空；
     * 数值越低，AI 说话越保守、越精确。
     * 范围通常是 0.0 ~ 2.0。一般对话用 0.7 左右比较合适。
     */
    fun updateTemperature(temp: Double) {
        _settings.update { it.copy(temperature = temp) }
        viewModelScope.launch { settingsDataStore.updateTemperature(temp) }
    }

    /**
     * 更新最大 Token 数（maxTokens）。
     * Token 是 AI 的"字"——但不是按中文一个字一个 token 算，
     * 而是按英文的单词的一部分算。
     * 这个设置控制 AI 回答能写多长。数值越大，回答可能越长。
     */
    fun updateMaxTokens(tokens: Int) {
        _settings.update { it.copy(maxTokens = tokens) }
        viewModelScope.launch { settingsDataStore.updateMaxTokens(tokens) }
    }

    /** 更新当前使用的 AI 服务商（比如从 DeepSeek 切换到 OpenAI）。 */
    fun updateProviderId(id: String) {
        _settings.update { it.copy(providerId = id) }
        viewModelScope.launch { settingsDataStore.updateProviderId(id) }
    }

    // ==========================================================================
    // 第十一关：主题设置（Theme）
    // ==========================================================================
    // 和设置管理类似，但存的是"外观"相关的数据。
    // ==========================================================================

    /** 更新深色/浅色模式。值可以是 "system"（跟随系统）、"light"（浅色）、"dark"（深色）。 */
    fun updateDarkTheme(isDark: String) {
        _themeSettings.update { it.copy(isDarkTheme = isDark) }
        viewModelScope.launch { settingsDataStore.updateDarkTheme(isDark) }
    }

    /** 更新主题色（强调色）。比如蓝色、紫色、绿色等。 */
    fun updateAccentColor(color: String) {
        _themeSettings.update { it.copy(accentColor = color) }
        viewModelScope.launch { settingsDataStore.updateAccentColor(color) }
    }

    /** 更新字体缩放比例。数值越大，界面文字越大。 */
    fun updateFontScale(scale: Int) {
        _themeSettings.update { it.copy(fontScale = scale) }
        viewModelScope.launch { settingsDataStore.updateFontScale(scale) }
    }

    // ==========================================================================
    // 第十二关：发送消息（Send Message）—— 核心功能
    // ==========================================================================
    // sendMessage() 是聊天页面最核心的方法——
    // 用户在输入框打字后点"发送"，就会触发这个方法。
    //
    // 这个方法做了以下几件事：
    //   1. 检查是否正在加载（防止重复发送）
    //   2. 检查 API Key 是否设置（没有密钥就报错）
    //   3. 检查网络是否可用（断网提醒）
    //   4. 如果没有选对话，自动新建一个
    //   5. 调用 doSendWithTools() 真正发送消息
    // ==========================================================================

    /**
     * sendMessage(content)：发送一条用户消息。
     *
     * @param content 用户输入的文字内容
     */
    fun sendMessage(content: String) {
        val currentSettings = _settings.value       // 获取当前设置
        val convId = _uiState.value.currentConversationId  // 获取当前对话 ID

        // ===== 安全检查 1：防止重复发送 =====
        // 如果 AI 正在回复（isLoading = true），用户又点发送，
        // 直接忽略这次请求，防止出错。
        if (_uiState.value.isLoading) return

        // ===== 安全检查 2：API Key 检查 =====
        // API Key 就像"门票"——没有门票，AI 不会理你。
        if (currentSettings.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请在设置中填写 API Key") }
            return
        }

        // ===== 安全检查 3：网络检查 =====
        // 如果没联网，提示用户，但消息还是可以发出去（后面会排队）。
//        if (!_uiState.value.isOnline) {
//            _uiState.update { it.copy(error = "网络不可用，消息将在联网后发送") }
//        }

        // ===== 安全检查 4：如果没有选对话，自动创建一个 =====
        if (convId == null) {
            viewModelScope.launch {
                val id = repository.createConversation(getConversationTitle(content))
                _uiState.update { it.copy(currentConversationId = id) }
                doSendWithTools(id, content, currentSettings)
            }
            return
        }

        // ===== 一切就绪，发送消息 =====
        doSendWithTools(convId, content, currentSettings)
    }

    // ==========================================================================
    // 第十三关：发送消息 + AI 工具调用（核心魔法）
    // ==========================================================================
    //
    // ★★★ 这是整个 ChatViewModel 最复杂、也最重要的方法 ★★★
    //
    // 什么是"工具调用"？
    //   普通的聊天：用户说"帮我打开 WiFi" → AI 回复"好的，你可以在设置里打开"
    //   有工具调用的聊天：用户说"帮我打开 WiFi" → AI 回复"[TOOL:enable_wifi]" →
    //     系统检测到 [TOOL:...] 标记 → 自动执行对应 Shell 命令 →
    //     WiFi 真的打开了！
    //
    // 流程概览：
    //   第 1 步：把用户消息保存到数据库
    //   第 2 步：构建系统提示词（告诉 AI "你可以使用这些工具"）
    //   第 3 步：把消息发给 AI，接收 AI 的流式回复
    //   第 4 步：解析 AI 回复中的 [TOOL:xxx] 标记
    //   第 5 步：如果有工具调用，执行对应命令
    //   第 6 步：把工具执行结果发回给 AI，让 AI 总结
    //   第 7 步：保存最终回复到数据库
    //   第 8 步：更新界面状态、统计 Token 用量
    // ==========================================================================

    /**
     * doSendWithTools(convId, content, settings)：
     * 实际的发送逻辑，支持 AI 调用系统工具。
     *
     * Kotlin 中的 private 表示"只有这个类内部才能调用这个方法"。
     * 外部代码不能直接调用，只能通过 sendMessage() 间接触发。
     *
     * @param convId 当前对话的 ID
     * @param content 用户输入的内容
     * @param settings 当前的 App 设置
     */
    private fun doSendWithTools(convId: Long, content: String, settings: AppSettings) {
        // ── 第一步：把用户消息加入状态列表 ──
        val userMsg = ChatMessage(role = "user", content = content)
        _uiState.update { it.copy(
            messages = it.messages + userMsg,  // 追加到消息列表
            isLoading = true,                    // 显示加载状态
            streamingContent = "",               // 清空流式内容
            error = null                         // 清空之前的错误
        ) }

        // ── 第二步：把用户消息保存到本地数据库 ──
        viewModelScope.launch {
            repository.insertMessage(MessageEntity(
                conversationId = convId,
                role = "user",
                content = content
            ))
        }

        // ── 第三步到第八步：核心流程 ──
        viewModelScope.launch {
            try {
                // ── 步骤 A：构建系统提示词 ──
                // buildToolSystemPrompt() 会生成一段话，告诉 AI：
                // "你是手机助手，你可以用这些工具控制手机……"
                val systemPrompt = buildToolSystemPrompt(settings.systemPrompt)
                val messages = buildList {
                    add(ChatMessage(role = "system", content = systemPrompt))
                    addAll(_uiState.value.messages)
                }

                // ── 步骤 B：第一轮 AI 请求 ──
                // sendMessageStream 是"流式"请求——AI 一个字一个字地返回。
                var fullContent = ""
                providerRegistry.sendMessageStream(
                    settings.providerId,
                    messages,
                    settings
                ).collect { chunk ->
                    fullContent += chunk
                    _uiState.update { it.copy(streamingContent = fullContent) }
                }

                // ── 步骤 C：解析并执行工具调用 ──
                // 检查 AI 回复中是否有 [TOOL:xxx] 标记
                val (processedContent, toolResults) = executeToolsInContent(fullContent)

                val finalContent: String
                if (toolResults.isNotEmpty()) {
                    // ── 步骤 D：有工具执行结果，让 AI 做总结 ──
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

                    // 第二轮 AI 请求：把工具结果发给 AI，让 AI 总结
                    val secondMessages = messages + listOf(
                        ChatMessage(role = "assistant", content = processedContent),
                        ChatMessage(role = "user",
                            content = "请根据以上执行结果，用中文向我汇报。")
                    )

                    var secondContent = ""
                    try {
                        providerRegistry.sendMessageStream(
                            settings.providerId,
                            secondMessages,
                            settings
                        ).collect { chunk ->
                            secondContent += chunk
                            _uiState.update {
                                it.copy(streamingContent =
                                    "$processedContent\n\n$toolResultText\n\n$secondContent")
                            }
                        }
                    } catch (_: Exception) {
                        // 如果第二轮失败（比如网络断了），直接展示工具结果
                    }

                    finalContent = if (secondContent.isNotBlank()) {
                        "$processedContent\n\n$toolResultText\n\n$secondContent"
                    } else {
                        "$processedContent\n\n$toolResultText"
                    }
                } else {
                    // 没有工具调用，直接用 AI 的原始回复
                    finalContent = processedContent
                }

                // ── 步骤 E：Token 统计 ──
                // Token 是 AI 的计价单位，大概 4 个英文字符 = 1 个 token。
                // 这里只是估算，不是精确计算。
                val promptChars = messages.sumOf { it.content.length }
                val completionChars = finalContent.length
                val estimatedPromptTokens = (promptChars / 4).toLong()
                val estimatedCompletionTokens = (completionChars / 4).toLong()
                val cost = providerRegistry.estimateCost(
                    settings.providerId, settings.modelName,
                    estimatedPromptTokens, estimatedCompletionTokens
                )
                val stats = _uiState.value.tokenStats.copy(
                    promptTokens = _uiState.value.tokenStats.promptTokens + estimatedPromptTokens,
                    completionTokens = _uiState.value.tokenStats.completionTokens + estimatedCompletionTokens,
                    totalTokens = _uiState.value.tokenStats.totalTokens +
                            estimatedPromptTokens + estimatedCompletionTokens,
                    estimatedCost = _uiState.value.tokenStats.estimatedCost + cost
                )
                _uiState.update { it.copy(tokenStats = stats) }

                // ── 步骤 F：保存到数据库并更新界面 ──
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
                // ── 异常处理：如果上面任何一步出错了，就在这里处理 ──
                _uiState.update { it.copy(
                    isLoading = false,
                    streamingContent = "",
                    error = e.message ?: "网络错误"
                ) }
            }
        }
    }

    // ==========================================================================
    // 第十四关：构建工具系统提示词
    // ==========================================================================
    //
    // 这个方法生成一段"使用说明书"，告诉 AI：
    //   "你可以使用以下工具来控制这台 Android 手机……"
    //
    // 为什么需要这个？
    //   普通的 AI 只知道"说话"，不知道"做事"。
    //   通过系统提示词告诉 AI："当你看到 [TOOL:xxx] 标记时，
    //   用户手机会自动执行对应的命令。"
    // ==========================================================================

    /**
     * buildToolSystemPrompt(userPrompt)：构建系统提示词。
     *
     * 返回的字符串包含：
     *   1. 用户自定义的系统提示词（如果有的话）
     *   2. 所有可用的工具列表（开关、调节、查询、应用管理、文件操作等）
     *   3. 使用规则（怎么输出工具标记、什么情况下要确认等）
     *
     * @param userPrompt 用户在设置里写的自定义提示词
     * @return 完整的系统提示词
     */
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

        val base = if (userPrompt.isNotBlank()) "$userPrompt\n\n$tools" else tools
        return base
    }

    // ==========================================================================
    // 第十五关：解析工具调用并执行
    // ==========================================================================
    //
    // 当 AI 回复中包含 [TOOL:xxx] 标记时，这个方法负责：
    //   1. 用正则表达式找到所有的 [TOOL:...] 标记
    //   2. 对每个标记，执行对应的 Shell 命令
    //   3. 把 [TOOL:xxx] 替换为命令执行的结果
    // ==========================================================================

    /**
     * executeToolsInContent(content)：解析 AI 回复中的工具标记并执行。
     *
     * 比如 AI 回复了：
     *   "好的，我来帮你查看 CPU 信息。[TOOL:get_cpu_info]"
     *
     * 这个方法会：
     *   1. 检测到 [TOOL:get_cpu_info]
     *   2. 执行 cat /proc/cpuinfo（查看 CPU 信息）
     *   3. 把 [TOOL:get_cpu_info] 替换为实际的 CPU 信息
     *
     * @param content AI 的原始回复
     * @return Pair<处理后的文本, 所有工具执行的结果列表>
     */
    private suspend fun executeToolsInContent(content: String): Pair<String, List<SysResult>> {
        // 正则表达式：匹配 [TOOL:xxx:params] 格式的标记
        val toolRegex = Regex("""\[TOOL:([a-z_]+)(?::([^\]]*))?\]""")
        val matches = toolRegex.findAll(content)
        if (!matches.any()) return Pair(content, emptyList())  // 没有工具标记，直接返回

        val results = mutableListOf<SysResult>()
        var processed = content

        for (match in matches) {
            val action = match.groupValues[1]  // 工具名称，比如 "get_cpu_info"
            val paramsStr = match.groupValues.getOrNull(2) ?: ""  // 参数，比如 "host=8.8.8.8,count=4"
            val params = parseParams(paramsStr)  // 解析成 Map

            val result = executeToolAction(action, params)  // 执行命令
            results.add(result)

            // 把 [TOOL:xxx] 替换成执行结果的描述
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

    /**
     * parseParams(paramsStr)：解析参数字符串。
     *
     * 把 "host=8.8.8.8,count=4" 这样的字符串，
     * 转成 { "host" -> "8.8.8.8", "count" -> "4" } 这样的 Map。
     *
     * @param paramsStr 原始参数字符串
     * @return 解析后的参数 Map
     */
    private fun parseParams(paramsStr: String): Map<String, String> {
        if (paramsStr.isBlank()) return emptyMap()
        return paramsStr.split(",").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            } else null
        }.toMap()
    }

    // ==========================================================================
    // 第十六关：工具动作 → Shell 命令的映射表
    // ==========================================================================
    //
    // 这个方法是一个"翻译器"——
    // 把 AI 能理解的动作名称（如 "enable_wifi"），
    // 翻译成 Android 系统能执行的 Shell 命令（如 "svc wifi enable"）。
    //
    // 这就像一本字典：
    //   "打开WiFi" → "svc wifi enable"
    //   "查看存储" → "df -h"
    //   ...
    // ==========================================================================

    /**
     * getShellCommandForAction(action, params)：
     * 根据动作名称和参数，生成对应的 Shell 命令。
     *
     * @param action 动作名称（如 "enable_wifi"）
     * @param params 参数（如 { "ssid" -> "MyWiFi", "password" -> "123456" }）
     * @return 对应的 Shell 命令，如果找不到匹配则返回 null
     */
    private fun getShellCommandForAction(action: String, params: Map<String, String>): String? {
        return when (action) {
            // === 开关控制组 ===
            "enable_wifi" -> "svc wifi enable"                         // 打开 WiFi
            "disable_wifi" -> "svc wifi disable"                      // 关闭 WiFi
            "enable_bluetooth" -> "svc bluetooth enable"              // 打开蓝牙
            "disable_bluetooth" -> "svc bluetooth disable"            // 关闭蓝牙
            "enable_hotspot" -> {                                      // 打开热点
                val ssid = params["ssid"] ?: "Hsiaopu"
                val password = params["password"] ?: "12345678"
                "cmd wifi start-softap \"$ssid\" wpa2-psk \"$password\""
            }
            "disable_hotspot" -> "cmd wifi stop-softap"               // 关闭热点
            "enable_mobile_data" -> "svc data enable"                 // 打开移动数据
            "disable_mobile_data" -> "svc data disable"               // 关闭移动数据
            "enable_airplane_mode" -> "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE"  // 打开飞行模式
            "disable_airplane_mode" -> "settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE" // 关闭飞行模式
            "enable_nfc" -> "svc nfc enable"                          // 打开 NFC
            "disable_nfc" -> "svc nfc disable"                        // 关闭 NFC

            // === 调节组 ===
            "set_brightness" -> "settings put system screen_brightness ${params["level"] ?: "128"}"  // 设置亮度
            "get_brightness" -> "settings get system screen_brightness"  // 获取当前亮度
            "set_volume" -> {                                          // 设置音量
                val stream = params["stream"] ?: "music"
                val streamCode = when (stream) { "ring" -> 2; "alarm" -> 4; else -> 3 }
                "media volume --stream $streamCode --set ${params["level"] ?: "8"}"
            }
            "get_volume" -> {                                          // 获取音量
                val stream = params["stream"] ?: "music"
                val streamCode = when (stream) { "ring" -> 2; "alarm" -> 4; else -> 3 }
                "media volume --stream $streamCode --get"
            }

            // === 查询组 ===
            "get_cpu_info" -> "cat /proc/cpuinfo | head -40"          // CPU 信息
            "get_memory_info" -> "cat /proc/meminfo | head -20"       // 内存信息
            "get_uptime" -> "cat /proc/uptime"                        // 运行时间
            "get_kernel_version" -> "uname -a"                        // 内核版本
            "get_battery_info" -> "dumpsys battery"                   // 电池信息
            "get_cpu_temp" -> "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null || echo 'N/A'"  // CPU 温度
            "get_ip_address" -> "ip addr show wlan0 2>/dev/null | grep 'inet ' || ip addr show eth0 2>/dev/null | grep 'inet '"  // IP 地址
            "get_wifi_networks" -> "dumpsys wifi | grep 'SSID:' | head -10"  // WiFi 网络列表
            "get_disk_usage" -> "df -h"                               // 磁盘使用
            "get_top_processes" -> "ps -A -o PID,USER,NAME | tail -20"  // 进程列表
            "get_network_interfaces" -> "ip addr show"                // 网络接口
            "get_sensor_list" -> "dumpsys sensorservice | grep -A 1 'Sensor List'"  // 传感器列表
            "get_display_info" -> "dumpsys window displays | head -30"  // 显示信息
            "get_installed_packages" -> "pm list packages | tail -30"  // 已安装应用
            "get_running_services" -> "dumpsys activity services | grep 'ServiceRecord' | tail -20"  // 运行中的服务
            "get_routing_table" -> "ip route show"                    // 路由表
            "get_dns_info" -> "getprop net.dns1 && getprop net.dns2" // DNS 信息
            "get_mount_info" -> "mount | grep -v '^rootfs'"          // 挂载点
            "get_prop" -> "getprop ${params["key"] ?: ""}"           // 系统属性
            "ping_test" -> "ping -c ${params["count"] ?: "4"} -W 2 ${params["host"] ?: "8.8.8.8"}"  // Ping 测试

            // === 应用管理 ===
            "force_stop" -> "am force-stop ${params["package"] ?: ""}"  // 强制停止应用
            "clear_data" -> "pm clear ${params["package"] ?: ""}"       // 清除应用数据
            "uninstall" -> "pm uninstall ${params["package"] ?: ""}"    // 卸载应用

            // === 文件操作 ===
            "list_files" -> "ls -lah ${params["path"] ?: "/sdcard"}"   // 列出文件
            "read_file" -> "cat ${params["path"] ?: ""} | head -50"     // 读取文件
            "delete_file" -> "rm -f ${params["path"] ?: ""}"            // 删除文件

            // === 截屏 ===
            "screenshot" -> "screencap -p ${params["path"] ?: "/sdcard/Pictures/screenshot_hsiaopu.png"}"  // 截屏

            // === 系统操作 ===
            "reboot" -> "reboot"                                        // 重启
            "reboot_recovery" -> "reboot recovery"                     // 重启到 Recovery 模式
            "reboot_bootloader" -> "reboot bootloader"                 // 重启到 Bootloader 模式
            "shutdown" -> "reboot -p"                                  // 关机

            // 没有匹配的动作
            else -> null
        }
    }

    // ==========================================================================
    // 第十七关：执行工具动作
    // ==========================================================================
    //
    // executeToolAction() 是实际执行命令的方法——
    // 它把命令通过 ShellCommandBus 发送到 Shell 页面，
    // 然后等待 Shell 页面返回执行结果。
    //
    // 这就好比：
    //   ViewModel 写好了一个"任务单（Shell 命令）"，
    //   放到"传送带（ShellCommandBus）"上，
    //   Shell 页面从传送带上取走任务单去执行，
    //   然后把"完成报告（执行结果）"放回传送带，
    //   ViewModel 收到报告后继续处理。
    // ==========================================================================

    /**
     * executeToolAction(action, params)：执行一个工具动作。
     *
     * 流程：
     *   1. 把动作名称翻译成 Shell 命令
     *   2. 通过 ShellCommandBus 发送给 Shell 页面
     *   3. 等待 Shell 页面的执行结果（最多等 30 秒）
     *   4. 返回执行结果
     *
     * @param action 动作名称
     * @param params 参数
     * @return 执行结果（成功/失败 + 输出信息）
     */
    private suspend fun executeToolAction(action: String, params: Map<String, String>): SysResult {
        val shellCommand = getShellCommandForAction(action, params)
        if (shellCommand == null) {
            return SysResult(action, false, "不支持的操作: $action", "", false)
        }

        // 通过"命令传送带"发送命令到 Shell 页面
        shellCommandBus.sendCommand(shellCommand)

        return try {
            // withTimeout(30000) = 最多等 30 秒
            val shellResult = kotlinx.coroutines.withTimeout(30000) {
                // first { it.command == shellCommand } = 等待 Shell 页面执行完并返回结果
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
            // 超时了——Shell 页面可能没打开
            SysResult(action, false, "Shell 执行超时，请确保 Shell 页面已打开", "", false)
        }
    }

    // ==========================================================================
    // 第十八关：辅助方法
    // ==========================================================================

    /**
     * clearError()：清除错误信息。
     * 当用户看到错误提示并点了"关闭"按钮时调用。
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * getConversationTitle(content)：根据对话内容生成标题。
     *
     * 规则很简单：从消息内容里取前 30 个字符作为标题。
     * 如果超过 30 个字符，后面加省略号（...）。
     *
     * 比如：
     *   输入："帮我查看一下当前的 CPU 使用率和内存占用情况"
     *   输出："帮我查看一下当前的 CPU 使用率..."
     *
     * @param content 消息内容
     * @return 截取后的标题
     */
    private fun getConversationTitle(content: String): String {
        val cleaned = content.replace("\n", " ").trim()
        return if (cleaned.length > 30) cleaned.take(30) + "..." else cleaned
    }
}
