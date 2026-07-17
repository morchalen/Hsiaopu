package com.example.hsiaopu.network

import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderRegistry @Inject constructor(
    private val deepSeekProvider: DeepSeekProvider,
    private val openAICompatibleProvider: OpenAICompatibleProvider
) {
    private val providers: List<AiProvider> = listOf(deepSeekProvider, openAICompatibleProvider)

    fun getAllProviders(): List<ProviderInfo> = providers.map { it.info }

    fun getProvider(id: String): AiProvider? = providers.find { it.info.id == id }

    fun getDefaultProvider(): AiProvider = deepSeekProvider

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
}