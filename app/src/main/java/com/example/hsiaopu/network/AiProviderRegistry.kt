package com.example.hsiaopu.network

import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderRegistry @Inject constructor(
    private val openAICompatibleProvider: OpenAICompatibleProvider
) {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        settings: AppSettings
    ): String {
        return openAICompatibleProvider.sendMessage(messages, settings)
    }

    fun sendMessageStream(
        messages: List<ChatMessage>,
        settings: AppSettings
    ): Flow<String> {
        return openAICompatibleProvider.sendMessageStream(messages, settings)
    }

    fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double {
        return openAICompatibleProvider.estimateCost(model, promptTokens, completionTokens)
    }

    suspend fun fetchModels(settings: AppSettings): List<String> {
        return openAICompatibleProvider.fetchModels(settings)
    }
}