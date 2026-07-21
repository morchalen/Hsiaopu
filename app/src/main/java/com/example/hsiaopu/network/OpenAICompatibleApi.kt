package com.example.hsiaopu.network
//看着是接口，实际上是 Retrofit 会 自动生成的代理对象
import com.example.hsiaopu.data.ChatRequest
import com.example.hsiaopu.data.ChatResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * OpenAI API 接口
 * 
 * 这个接口定义了所有与 AI 服务端通信的网络请求方法。
 * Retrofit 会根据注解自动生成实现类，我们只需要调用这些 suspend 函数即可。
 */
interface OpenAICompatibleApi {
    
    // 1. 普通对话请求（非流式）
    //发送一条消息给 AI，等待完整回复
    @POST("chat/completions")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    // 2. 流式对话请求（支持打字机效果）
    // @Streaming                    // ← 告诉 Retrofit：别一次性把响应读进内存
    // @POST("chat/completions")     // ← 告诉 Retrofit：用 POST 方法，地址是这个
    // @Headers("Accept: text/event-stream")  // 添加请求头← 告诉服务器：我要流式格式
    // suspend fun sendMessageStream(@Body request: ChatRequest): Response<ResponseBody>

    // 3. 获取具体api地址可用的模型列表
    @GET("models")
    suspend fun getModels(): ModelsResponse
}


// 数据类定义（用于解析服务器返回的模型列表）
/**
 * 模型列表响应体
 * 
 * 对应服务器返回的 JSON 结构：
 * {
 *   "data": [
 *     {"id": "gpt-4", "object": "model", "created": 1686936685, "owned_by": "openai"},
 *     {"id": "gpt-3.5-turbo", "object": "model", "created": 1686936685, "owned_by": "openai"}
 *   ]
 * }
 * 
 * data 模型信息列表
 */
data class ModelsResponse(
    val data: List<ModelInfo>
)


data class ModelInfo(
    val id: String, // 模型唯一标识
    val `object`: String,     // ← 反引号转义，因为 object 是 Kotlin 关键字；object是对象类型（固定为 "model"）
    val created: Long, // 创建时间戳（Unix 秒数）
    val owned_by: String // 模型所属方（如 "openai"）
)