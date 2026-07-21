package com.example.hsiaopu.data

import com.google.gson.annotations.SerializedName

/**
 * 应用设置数据模型
 * 存储在 DataStore 中，用于配置 AI API 相关参数
 */
data class AppSettings(
    val apiKey: String = "",  // AI API 密钥
    val apiEndpoint: String = "https://api.deepseek.com/v1/chat/completions",  // API 请求地址
    val modelName: String = "deepseek-chat",  // 使用的模型名称
    val systemPrompt: String = "你是一个智能AI助手，请用简洁、专业的方式回答用户的问题。",  // 系统提示词
    val temperature: Double = 0.7,  // 温度参数（0-1，越高越随机）
    val maxTokens: Int = 2048  // 最大生成 Token 数
)

// ========== 网络请求/响应模型 ==========

/**
 * AI 聊天请求模型
 * 对应 OpenAI 兼容 API 的请求格式
 */
data class ChatRequest(
    val model: String,  // 模型名称
    val messages: List<Message>,  // 消息列表（包含历史对话）
    val temperature: Double = 0.7,  // 温度参数
    @SerializedName("max_tokens") val maxTokens: Int = 2048,  // 最大生成 Token 数
    val stream: Boolean = true  // 是否流式响应
)

/**
 * 单个消息模型
 * 用于构建请求中的消息列表
 */
data class Message(
    val role: String,  // 角色："user"（用户）、"assistant"（助手）、"system"（系统）
    val content: String  // 消息内容
)

/**
 * AI 聊天响应模型
 * 对应 OpenAI 兼容 API 的响应格式
 */
data class ChatResponse(
    val id: String,  // 响应 ID
    val choices: List<Choice>  // 响应选项列表
)

/**
 * 响应选项模型
 * 流式响应时每次返回一个 Choice
 */
data class Choice(
    val message: Message?,  // 完整消息（非流式时使用）
    val delta: Delta?,  // 增量消息（流式响应时使用）
    @SerializedName("finish_reason") val finishReason: String?  // 结束原因："stop"（正常结束）、"length"（达到最大 Token）等
)

/**
 * 增量消息模型
 * 流式响应时，每次返回的消息增量部分
 */
data class Delta(
    val role: String?,  // 角色（仅在第一条增量消息中包含）
    val content: String?  // 消息内容增量（可能为 null，仅包含部分文本）
)

/**
 * Token 使用量统计模型
 * AI API 响应中返回的 Token 消耗信息
 */
data class UsageInfo(
    @SerializedName("prompt_tokens") val promptTokens: Long = 0,  // 输入提示词消耗的 Token 数
    @SerializedName("completion_tokens") val completionTokens: Long = 0,  // AI 生成内容消耗的 Token 数
    @SerializedName("total_tokens") val totalTokens: Long = 0  // 总共消耗的 Token 数
)

// ========== 功能引导键 ==========

/**
 * 功能引导标识键
 * 用于 DataStore 中记录哪些功能引导已被用户看过
 */
data class FeatureGuideKey(val key: String)

// ========== 聊天消息（UI 模型） ==========

/**
 * UI 层使用的聊天消息模型
 * 用于在界面上展示聊天记录
 */
data class ChatMessage(
    val role: String,  // 角色："user"（用户）、"assistant"（助手）、"system"（系统）
    val content: String,  // 消息内容
    val timestamp: Long = System.currentTimeMillis()  // 消息发送时间戳
)