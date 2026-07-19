package com.example.hsiaopu.data

import com.google.gson.annotations.SerializedName

data class AppSettings(
    val apiKey: String = "",
    val apiEndpoint: String = "https://api.deepseek.com/v1/chat/completions",
    val modelName: String = "deepseek-chat",
    val systemPrompt: String = "你是一个智能AI助手，请用简洁、专业的方式回答用户的问题。",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048
)

// ========== Network Request/Response ==========
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    val stream: Boolean = true
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: UsageInfo? = null
)

data class Choice(
    val message: Message?,
    val delta: Delta?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class Delta(
    val role: String?,
    val content: String?
)

data class UsageInfo(
    @SerializedName("prompt_tokens") val promptTokens: Long = 0,
    @SerializedName("completion_tokens") val completionTokens: Long = 0,
    @SerializedName("total_tokens") val totalTokens: Long = 0
)

// ========== Chat Message (UI model)  ==========
data class ChatMessage(// 聊天消息模型
    val role: String,       // "user" | "assistant" | "system"
    val content: String,// 消息内容
    val timestamp: Long = System.currentTimeMillis()// 消息时间戳
)