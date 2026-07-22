package com.example.hsiaopu.network

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
 * 聊天 API 接口
 * 
 * 定义了与 AI 聊天服务端通信的网络请求方法。
 * Retrofit 会根据注解自动生成实现类。
 */
interface ChatApiService{
    // 1. 普通对话请求（非流式）
    @POST("chat/completions")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    // 2. 流式对话请求（暂不用）
    // @Streaming
    // @POST("chat/completions")
    // @Headers("Accept: text/event-stream")
    // suspend fun sendMessageStream(@Body request: ChatRequest): Response<ResponseBody>

    // 3. 获取模型列表
    @GET("models")
    suspend fun getModels(): ModelsResponse
}


// ===== 模型列表  响应数据类 =====

data class ModelsResponse(
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String,
    val `object`: String,//转义。object与kt冲突，所以用反引号包裹起来
    val created: Long,
    val owned_by: String
)
