package com.example.hsiaopu.network
// AI 服务商统一接口：必须实现哪些功能。
import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import kotlinx.coroutines.flow.Flow

// AI 服务商的基本信息
data class ProviderInfo(
    val id: String, // 服务商唯一标识，例如："openai"、"deepseek"
    val name: String, // 服务商名称，例如："OpenAI"
    val description: String, // 服务商描述，用于界面显示
    val defaultEndpoint: String, // 默认请求地址(API Endpoint)例如：https://api.openai.com/v1
    val defaultModel: String, // 默认使用的模型例如：gpt-4.1、deepseek-chat
    val models: List<String>// 当前服务商支持的所有模型列表
)

// AI 服务商接口（规范）
interface AiProvider {
    val info: ProviderInfo// 当前 AI 服务商的基本信息
    // 普通聊天（非流式）:一次等待 AI 全部回答完成，然后返回完整字符串。
    // suspend： 因为需要访问网络，请求时间较长，所以使用协程挂起。
    suspend fun sendMessage(
        // 当前聊天记录（上下文）
        messages: List<ChatMessage>,
        // 当前 AI 配置（模型、Key、温度等）
        settings: AppSettings
        ): String

    // 流式聊天（Streaming）AI 一边生成，一边返回内容。
    // fun sendMessageStream(

    //     // 聊天上下文
    //     messages: List<ChatMessage>,

    //     // AI 配置
    //     settings: AppSettings

    //     ): Flow<String>
}