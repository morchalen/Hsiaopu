package com.example.hsiaopu.network

import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import com.example.hsiaopu.data.ChatRequest
import com.example.hsiaopu.data.Message
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

//OpenAI 兼容的 AI 提供者实现类
@Singleton//类冒号后面是接口或者父类，函数冒号后面是返回值类型
class OpenAICompatibleProvider @Inject constructor() : AiProvider {
    
    // Gson 实例用于解析 JSON，线程安全且轻量：把json的string变为kt的实例对象
    private val gson = Gson()//Gson() 不是函数，是构造方法，所以在这里构造一次，后续重复使用，不然很浪费资源
    
    // 当前使用的 API 接口实例（懒加载，使用时才初始化）
    private var _api: OpenAICompatibleApi? = null
    
    // 当前配置的数据类实例，用于判断是否需要重新创建 API 实例
    private var _currentSettings: AppSettings? = null

    /**
     * 提供者信息（固定配置）
     * 
     * 包含该提供者的元数据，用于在 UI 上展示和选择。
     * 这里预置了常见模型列表，但实际可用模型会通过 fetchModels() 动态获取。
     */
    override val info = ProviderInfo(//数据类
        id = "openai_compatible",                    // 唯一标识符
        name = "AI Provider",                        // 显示名称
        description = "兼容 OpenAI API 格式的模型服务",
        defaultEndpoint = "https://api.deepseek.com/v1/chat/completions",  // 默认使用 DeepSeek
        defaultModel = "deepseek-chat",              // 默认模型
        models = listOf(                             // 预设模型列表（UI 下拉框用）
            "deepseek-chat", "deepseek-coder", "deepseek-v4", "deepseek-v4-flash"
        )
    )

    /**
     * 获取或创建 API 接口实例（懒加载 + 缓存）
     * 
     * 这是核心方法，负责：
     * 1. 缓存策略：如果配置未变，直接返回已创建的实例，避免重复构建
     * 2. 构建 OkHttpClient：添加认证头（Bearer Token）和日志拦截器
     * 3. 构建 Retrofit：设置 baseUrl 并创建动态代理
     * 
     * 注意到这里会对 baseUrl 做处理：移除 "/chat/completions" 后缀，
     * 因为 Retrofit 的 @POST 注解中已经包含了这个路径。
     * 
     * @param settings 应用设置（包含 API 密钥、端点、模型等）
     * @return OpenAICompatibleApi 的 Retrofit 代理实例
     */
    private fun getApi(settings: AppSettings): OpenAICompatibleApi {
        // 如果已有实例且配置未变，直接返回缓存
        if (_api != null && settings == _currentSettings) return _api!!

        // 创建日志拦截器，便于调试 API 请求/响应（生产环境可关闭）
        val logging = HttpLoggingInterceptor().apply { 
            level = HttpLoggingInterceptor.Level.BODY 
        }
        
        // 构建 OkHttpClient，配置拦截器和超时
        val client = OkHttpClient.Builder()
            // 拦截器1：自动添加 API Key 认证头
            // 每次发请求前，拦住请求，在 Header 里加上 "Authorization: Bearer xxx"
            // chain = 请求传递链（OkHttp 内部对象），负责拿请求、改请求、继续传
            .addInterceptor { chain ->
                // 拿到当前请求对象（包含 URL、方法、Header、Body 等）
                val request = chain.request()               //拿到一个请求对象
                    .newBuilder()                           // 复制这个请求对象(原版无法修改)，准备修改
                    .addHeader("Authorization", "Bearer ${settings.apiKey}")  // 复制品:加上一个认证头的键值对
                    .build()                                // 把"复制品"变成正式的、可用的请求对象【 确认提交，生成正式请求对象】
                chain.proceed(request)                      // 继续往下传，发给服务器【手动结束修改，放行拦截，刚才拦截了，现在放行】
            }
            
            // 拦截器2：打印请求/响应日志（调试用）
            // logging 是提前创建好的 HttpLoggingInterceptor 对象
            // 和上面写法不同：上面是现场写 Lambda，这里是直接传现成的对象
            .addInterceptor(logging)        
            
            // 连接超时：拨号等服务器"接电话"多久算超时
            .connectTimeout(30, TimeUnit.SECONDS)
            
            // 读取超时：服务器多久不回应就算超时（流式响应要设长一点，AI 可能慢慢吐字）
            .readTimeout(60, TimeUnit.SECONDS)
            
            // 组装完毕，创建 OkHttpClient 实例（真正干活的网络引擎）
            .build()

            // 处理 baseUrl：移除路径末尾的 "/chat/completions" 
            // 因为 OpenAICompatibleApi 接口的 @POST 注解已包含此路径
            val baseUrl = settings.apiEndpoint.removeSuffix("/chat/completions") + "/"
            
            // 构建 Retrofit 实例
            _api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())  // 自动序列化/反序列化 JSON
                .build()
                .create(OpenAICompatibleApi::class.java)  // 生成动态代理实现
                
            // 保存当前配置快照，供下次缓存判断使用
            _currentSettings = settings
            return _api!!
    }

    /**
     * 发送消息（非流式，一次性返回完整回复）
     * 
     * 执行步骤：
     * 1. 获取或创建 API 实例
     * 2. 将 ChatMessage 转换为 API 所需的 Message 格式
     * 3. 构建请求体（设置 stream = false）
     * 4. 发起同步请求并等待完整响应
     * 5. 从响应中提取 AI 回复内容
     * 
     * @param messages 聊天历史（包含用户消息和助手消息）
     * @param settings 应用设置（模型、温度、最大 Token 等）
     * @return AI 生成的完整回复文本，失败时返回空字符串
     */
    override suspend fun sendMessage(messages: List<ChatMessage>, settings: AppSettings): String {
        val api = getApi(settings)
        // 转换消息格式
        val apiMessages = messages.map { Message(role = it.role, content = it.content) }
        // 构建请求（非流式）
        val request = ChatRequest(
            model = settings.modelName,
            messages = apiMessages,
            temperature = settings.temperature,      // 温度参数：控制随机性
            maxTokens = settings.maxTokens,          // 最大输出 Token 数
            stream = false                           // 非流式
        )
        val response = api.sendMessage(request)//调用api里面的而不是递归哈
        // 从第一个 choice 中提取消息内容
        return response.choices.firstOrNull()?.message?.content ?: ""//取第一个回复的内容
        //?. 叫"安全调用操作符"
        //意思是："如果左边的对象不是 null，就继续往右边走；如果是 null，就整个表达式直接返回 null，不再往下执行。"
        
        //?: 叫"Elvis 操作符"
        //意思是："如果左边表达式的结果不是 null，就取左边的值；如果是 null，就用右边的值代替。"
    }

    /**
     * 发送消息（流式，逐块返回）
     * 
     * 使用 Kotlin Flow 实现响应式流式传输：
     * 1. 构建请求时设置 stream = true
     * 2. 使用 OkHttp 的 ResponseBody 获取原始字节流
     * 3. 逐行读取 SSE（Server-Sent Events）格式数据
     * 4. 解析每行 "data: " 前缀后的 JSON 内容
     * 5. 提取 delta.content 字段并发射（emit）给调用方
     * 6. 遇到 "[DONE]" 标记时结束流
     * 
     * 注意：使用 flowOn(Dispatchers.IO) 将耗时操作切换到 IO 线程，
     * 避免阻塞主线程。
     * 
     * @param messages 聊天历史
     * @param settings 应用设置
     * @return Flow<String>，每个发射的 String 是一个文本片段
     * @throws Exception 网络错误或 API 返回非 2xx 状态码时抛出
     */
    // override fun sendMessageStream(messages: List<ChatMessage>, settings: AppSettings): Flow<String> = flow {
    //     val api = getApi(settings)
    //     val apiMessages = messages.map { Message(role = it.role, content = it.content) }
    //     val request = ChatRequest(
    //         model = settings.modelName,
    //         messages = apiMessages,
    //         temperature = settings.temperature,
    //         maxTokens = settings.maxTokens,
    //         stream = true                            // 开启流式模式
    //     )

    //     // 执行流式请求，返回 Response<ResponseBody>
    //     val response = api.sendMessageStream(request)
    //     // 检查 HTTP 状态码，非 2xx 视为失败
    //     if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

    //     // 获取响应体（字节流）
    //     val body = response.body() ?: throw Exception("Empty response")
    //     // 包装为 BufferedReader 以便逐行读取
    //     val reader = BufferedReader(InputStreamReader(body.byteStream()))

    //     // 使用 useLines 自动管理资源（自动关闭流）
    //     reader.useLines { lines ->
    //         for (line in lines) {
    //             // SSE 格式：每一行以 "data: " 开头
    //             if (line.startsWith("data: ")) {
    //                 val data = line.removePrefix("data: ").trim()
    //                 // 流结束标记
    //                 if (data == "[DONE]") break
    //                 try {
    //                     // 解析 JSON 并提取内容
    //                     val chunk = gson.fromJson(data, StreamChunk::class.java)
    //                     val content = chunk.choices?.firstOrNull()?.delta?.content ?: ""
    //                     if (content.isNotEmpty()) emit(content)  // 发射内容给调用方
    //                 } catch (_: Exception) {
    //                     // 忽略解析异常（可能有空行或其他非 JSON 数据）
    //                 }
    //             }
    //         }
    //     }
    // }.flowOn(Dispatchers.IO)  // 切换到 IO 线程执行


    /**
     * 获取服务商支持的模型列表（动态）
     * 
     * 调用 API 的 /v1/models 端点获取实时模型列表。
     * 如果请求失败（如网络错误或认证失败），返回空列表而不是抛出异常，
     * 这样 UI 可以回退到 info.models 的预设列表。
     * 
     * @param settings 应用设置
     * @return 模型名称列表，失败时返回空列表
     */
    suspend fun fetchModels(settings: AppSettings): List<String> {
        return try {
            // 1. 获取 API 实例（重用之前的配置逻辑）
            val api = getApi(settings)
            
            // 2. 调用 API 接口里的 getModels() 方法
            val response = api.getModels()//返回值是一个string的列表哈
            
            // 3. 从响应中提取所有模型的 ID（如 "gpt-4", "deepseek-chat" 等）
            response.data.map { it.id }
            
        } catch (e: Exception) {
            // 4. 如果出任何差错，返回空列表，不让 App 崩溃
            emptyList()
        }
    }
    /**
     * 估算调用 AI 模型的费用
     * 当前实现返回 0，因为开源模型（如 DeepSeek）定价变动频繁，
     * 且用户可能使用自有 API Key，费用信息不透明。
     * 如需精确计费，可在此接入具体模型定价表。
     */
    fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double = 0.0

    // ===== 内部数据类（用于解析流式响应 JSON） =====
    
    /**
     * 流式响应的数据块结构
     * 
     * 对应 SSE 流中每个 data: 后面的 JSON 对象。
     * 示例：{"choices":[{"delta":{"content":"你好"}}]}
     */
    private data class StreamChunk(val choices: List<StreamChoice>?)
    
    /**
     * 流式响应中的选择项
     */
    private data class StreamChoice(val delta: StreamDelta?)
    
    /**
     * 流式响应中的增量内容
     */
    private data class StreamDelta(val content: String?)
}