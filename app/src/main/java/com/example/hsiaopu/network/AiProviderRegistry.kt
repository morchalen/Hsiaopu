package com.example.hsiaopu.network

import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
//用于获取所有注册的AI提供程序的注册表
@Singleton//单例模式，确保在应用生命周期中只有一个实例
class AiProviderRegistry @Inject constructor(
    private val openAICompatibleProvider: OpenAICompatibleProvider
) {
    fun getAllProviders(): List<ProviderInfo> = listOf(openAICompatibleProvider.info)

    fun getProvider(id: String): AiProvider? = if (id == openAICompatibleProvider.info.id) openAICompatibleProvider else null

    fun getDefaultProvider(): AiProvider = openAICompatibleProvider

    suspend fun sendMessage(
        providerId: String,
        messages: List<ChatMessage>,
        settings: AppSettings
    ): String {
        return getProvider(providerId)?.sendMessage(messages, settings) ?: throw Exception("Unknown provider: $providerId")
    }

    fun sendMessageStream(
        providerId: String,
        messages: List<ChatMessage>,
        settings: AppSettings
    ): Flow<String> {
        return getProvider(providerId)?.sendMessageStream(messages, settings)
            ?: throw Exception("Unknown provider: $providerId")
    }

    fun estimateCost(providerId: String, model: String, promptTokens: Long, completionTokens: Long): Double {
        return getProvider(providerId)?.estimateCost(model, promptTokens, completionTokens) ?: 0.0
    }

    suspend fun fetchModels(providerId: String, settings: AppSettings): List<String> {
        return if (providerId == openAICompatibleProvider.info.id) {
            openAICompatibleProvider.fetchModels(settings)
        } else {
            emptyList()
        }
    }
}