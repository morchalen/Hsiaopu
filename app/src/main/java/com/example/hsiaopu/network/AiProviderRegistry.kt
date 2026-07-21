package com.example.hsiaopu.network

import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 提供者注册中心
 * 
 * 这是一个统一的门面（Facade）类，负责管理和路由所有 AI 服务提供者的调用。
 * 目前它直接委托给 OpenAI 兼容的提供者，但未来可以扩展支持更多不同的 AI 服务商
 * （如 Anthropic、Google Gemini、百度文心等）。
 * 
 * 使用 @Singleton 注解表示整个应用生命周期内只创建这一个实例，
 * 由 Dagger/Hilt 依赖注入框架管理。
 */

@Singleton
class AiProviderRegistry @Inject constructor(
    // 通过依赖注入获取 OpenAI 兼容的 AI 提供者实例
    // 这是当前唯一实际执行 AI 调用的实现类
    private val openAICompatibleProvider: OpenAICompatibleProvider
) {
    /**
     * 发送聊天消息（非流式，一次性返回完整结果）
     * 
     * 适用场景：需要完整回复后再处理，如摘要生成、代码补全等。
     * 
     * @param messages 聊天历史记录列表，包含用户和助手的对话
     * @param settings 应用设置，包含 API 密钥、模型名称、温度参数等
     * @return AI 生成的完整回复文本
     * 
     * @throws Exception 网络错误、认证失败或 API 错误时抛出异常
     */
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        settings: AppSettings
    ): String {
        return openAICompatibleProvider.sendMessage(messages, settings)
    }

    /**
     * 发送聊天消息（流式，逐字/逐词返回）
     * 
     * 适用场景：需要实时展示生成过程，提升用户体验，如聊天机器人打字效果。
     * 
     * 返回一个 Flow<String>，调用方可以 collect 每个数据块，
     * 每收到一个块就立即更新 UI，而不必等待完整响应。
     * 
     * @param messages 聊天历史记录列表
     * @param settings 应用设置
     * @return 流式响应的 Flow，每个 String 是一个文本片段（chunk）
     *
    // fun sendMessageStream(
    //     messages: List<ChatMessage>,
    //     settings: AppSettings
    // ): Flow<String> {
    //     return openAICompatibleProvider.sendMessageStream(messages, settings)
    // }

    /**
     * 估算调用 AI 模型的费用
     * 
     * 根据模型名称和输入/输出的 token 数量，计算本次请求的大致花费。
     * 不同模型的定价不同（如 GPT-4 比 GPT-3.5 更贵），
     * 这个函数会查询内部的定价表进行计算。
     * 
     * @param model 模型名称（如 "gpt-4", "gpt-3.5-turbo"）
     * @param promptTokens 提示词消耗的 token 数（输入）
     * @param completionTokens 生成回复消耗的 token 数（输出）
     * @return 估算的费用（通常以美元为单位）
     */
    fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double {
        return openAICompatibleProvider.estimateCost(model, promptTokens, completionTokens)
    }

    /**
     * 获取当前 AI 服务商支持的模型列表
     * 
     * 用于动态填充 UI 中的模型选择下拉框，让用户切换不同模型。
     * 例如 OpenAI 返回 ["gpt-4", "gpt-3.5-turbo", "gpt-4-turbo"]。
     * 
     * @param settings 应用设置（包含 API 密钥和基础 URL）
     * @return 模型名称的字符串列表
     * 
     * @throws Exception 网络错误或认证失败时抛出异常
     */
    suspend fun fetchModels(settings: AppSettings): List<String> {
        return openAICompatibleProvider.fetchModels(settings)
    }
}