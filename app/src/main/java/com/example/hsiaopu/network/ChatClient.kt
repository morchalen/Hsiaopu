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

// AI 服务商基本信息
data class ProviderInfo(
    val id: String,              // 唯一标识，如 "openai_compatible"
    val name: String,            // 显示名称，如 "AI Provider"
    val description: String,     // 描述
    val defaultEndpoint: String, // 默认请求地址
    val defaultModel: String,    // 默认模型
    val models: List<String>     // 模型列表
)

/**
 * 聊天客户端 — 封装与 AI API 通信的底层细节。
 * 
 * 职责：构建 OkHttpClient（认证头、日志）、发送请求、解析响应。
 */
@Singleton
class ChatClient @Inject constructor() {

    private val gson = Gson()
    private var _api: ChatApiService? = null
    private var _currentSettings: AppSettings? = null
    /** 服务商基本信息 */
    val info = ProviderInfo(
        id = "openai_compatible",
        name = "ai服务提供厂商",
        description = "兼容 OpenAI API 格式的模型服务",
        defaultEndpoint = "https://api.deepseek.com/v1/chat/completions",
        defaultModel = "默认模型",
        models = listOf("")
    )

    /**
     * 获取或创建 API 接口实例（懒加载 + 缓存）
     */
    private fun getApi(settings: AppSettings): ChatApiService {
        //如果说用户没有修改ai的配置且配置不是空的，那么直接结束
        if (_api != null && settings == _currentSettings) return _api!!
        //日志拦截器，用于打印请求和响应体
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        //构建 OkHttpClient，添加认证头和日志拦截器
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->//加请求头
                val request = chain.request()
                    .newBuilder()
                    .addHeader("Authorization", "Bearer ${settings.apiKey}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)//输出日志
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()//组装成型

        val baseUrl = settings.apiEndpoint.removeSuffix("/chat/completions") + "/"
        //构建 Retrofit 实例，添加 OkHttpClient 和 Gson 转换器
        // 把"网络请求"包装成"函数调用"
        _api = Retrofit.Builder()//Builder设计模式，内部类
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())//JSON字符串→ Kotlin 对象：（只是先声明，后续自动使用）
            .build()//组装成型
            .create(ChatApiService::class.java)//根据定义的接口（ChatApiService），动态生成一个可以调用这些接口方法的对象
        _currentSettings = settings
        return _api!!
    }

    /**
     * 发送消息（非流式，一次性返回完整回复）
     */
    suspend fun sendMessage(messages: List<ChatMessage>, settings: AppSettings): String {
        val api = getApi(settings)
        val apiMessages = messages.map { Message(role = it.role, content = it.content) }
        val request = ChatRequest(
            model = settings.modelName,
            messages = apiMessages,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            stream = false
        )
        // 1. 代码调用
        val response = api.sendMessage(request)
        //  2. ChatApiService实例里面的函数sendMessage收到请求：
        //    - 方法名：sendMessage
        //    - 注解：@POST("chat/completions")
        //    - 参数：@Body request

        // 3. Retrofit 构建 HTTP 请求：
        //    POST https://api.deepseek.com/v1/chat/completions
        //    Headers: Content-Type: application/json
        //    Body: {"model": "deepseek-chat", "messages": [...]}

        // 4. OkHttpClient 真的发出去

        // 5. 收到服务器响应：
        //    {"id": "...", "choices": [...]}

        // 6. GsonConverterFactory 把 JSON 转成 ChatResponse

        // 7. 返回 ChatResponse 给你
        return response.choices.firstOrNull()?.message?.content ?: ""
    }

    /**
     * 发送消息（流式，逐块返回）
     * 当前暂未使用，保留参考
     */
    // fun sendMessageStream(messages: List<ChatMessage>, settings: AppSettings): Flow<String> = flow {
    //     val api = getApi(settings)
    //     val apiMessages = messages.map { Message(role = it.role, content = it.content) }
    //     val request = ChatRequest(
    //         model = settings.modelName,
    //         messages = apiMessages,
    //         temperature = settings.temperature,
    //         maxTokens = settings.maxTokens,
    //         stream = true
    //     )
    //     val response = api.sendMessageStream(request)
    //     if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    //     val body = response.body() ?: throw Exception("Empty response")
    //     val reader = BufferedReader(InputStreamReader(body.byteStream()))
    //     reader.useLines { lines ->
    //         for (line in lines) {
    //             if (line.startsWith("data: ")) {
    //                 val data = line.removePrefix("data: ").trim()
    //                 if (data == "[DONE]") break
    //                 try {
    //                     val chunk = gson.fromJson(data, StreamChunk::class.java)
    //                     val content = chunk.choices?.firstOrNull()?.delta?.content ?: ""
    //                     if (content.isNotEmpty()) emit(content)
    //                 } catch (_: Exception) { }
    //             }
    //         }
    //     }
    // }.flowOn(Dispatchers.IO)

    /**
     * 获取服务商支持的模型列表（动态）
     */
    suspend fun fetchModels(settings: AppSettings): List<String> {
        return try {
            val api = getApi(settings)
            val response = api.getModels()
            response.data.map { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    
    // ===== 内部数据类（流式响应解析用） =====
    // private data class StreamChunk(val choices: List<StreamChoice>?)
    // private data class StreamChoice(val delta: StreamDelta?)
    // private data class StreamDelta(val content: String?)
}
