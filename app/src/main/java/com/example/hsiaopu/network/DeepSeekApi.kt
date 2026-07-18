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

interface DeepSeekApi {
    @POST("chat/completions")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    @Streaming
    @POST("chat/completions")
    @Headers("Accept: text/event-stream")
    suspend fun sendMessageStream(@Body request: ChatRequest): Response<ResponseBody>

    @GET("models")
    suspend fun getModels(): ModelsResponse
}

data class ModelsResponse(val data: List<ModelInfo>)
data class ModelInfo(val id: String, val `object`: String, val created: Long, val owned_by: String)