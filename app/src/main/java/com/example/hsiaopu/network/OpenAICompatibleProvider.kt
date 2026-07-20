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

@Singleton
class OpenAICompatibleProvider @Inject constructor() : AiProvider {
    private val gson = Gson()
    private var _api: OpenAICompatibleApi? = null
    private var _currentSettings: AppSettings? = null

    override val info = ProviderInfo(
        id = "openai_compatible",
        name = "AI Provider",
        description = "兼容 OpenAI API 格式的模型服务（支持 DeepSeek、OpenAI、Anthropic 等）",
        defaultEndpoint = "https://api.deepseek.com/v1/chat/completions",
        defaultModel = "deepseek-chat",
        models = listOf(
            "deepseek-chat", "deepseek-coder", "deepseek-v4", "deepseek-v4-flash",
            "gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo",
            "claude-3.5-sonnet", "claude-3-opus", "claude-3-sonnet"
        )
    )

    private fun getApi(settings: AppSettings): OpenAICompatibleApi {
        if (_api != null && settings == _currentSettings) return _api!!

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${settings.apiKey}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val baseUrl = settings.apiEndpoint.removeSuffix("/chat/completions") + "/"
        _api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAICompatibleApi::class.java)
        _currentSettings = settings
        return _api!!
    }

    override suspend fun sendMessage(messages: List<ChatMessage>, settings: AppSettings): String {
        val api = getApi(settings)
        val apiMessages = messages.map { Message(role = it.role, content = it.content) }
        val request = ChatRequest(
            model = settings.modelName,
            messages = apiMessages,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            stream = false
        )
        val response = api.sendMessage(request)
        return response.choices.firstOrNull()?.message?.content ?: ""
    }

    override fun sendMessageStream(messages: List<ChatMessage>, settings: AppSettings): Flow<String> = flow {
        val api = getApi(settings)
        val apiMessages = messages.map { Message(role = it.role, content = it.content) }
        val request = ChatRequest(
            model = settings.modelName,
            messages = apiMessages,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            stream = true
        )

        val response = api.sendMessageStream(request)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

        val body = response.body() ?: throw Exception("Empty response")
        val reader = BufferedReader(InputStreamReader(body.byteStream()))

        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, StreamChunk::class.java)
                        val content = chunk.choices?.firstOrNull()?.delta?.content ?: ""
                        if (content.isNotEmpty()) emit(content)
                    } catch (_: Exception) {}
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double {
        return when (model) {
            "gpt-4o" -> (promptTokens * 5.0 + completionTokens * 15.0) / 1_000_000
            "gpt-4o-mini" -> (promptTokens * 0.15 + completionTokens * 0.6) / 1_000_000
            "gpt-3.5-turbo" -> (promptTokens * 0.5 + completionTokens * 1.5) / 1_000_000
            else -> 0.0
        }
    }

    suspend fun fetchModels(settings: AppSettings): List<String> {
        return try {
            val api = getApi(settings)
            val response = api.getModels()
            response.data.map { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class StreamChunk(val choices: List<StreamChoice>?)
    private data class StreamChoice(val delta: StreamDelta?)
    private data class StreamDelta(val content: String?)
}