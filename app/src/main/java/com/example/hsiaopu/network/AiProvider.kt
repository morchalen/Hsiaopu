package com.example.hsiaopu.network

import com.example.hsiaopu.data.AppSettings
import com.example.hsiaopu.data.ChatMessage
import kotlinx.coroutines.flow.Flow

data class ProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val models: List<String>
)

data class UsageStats(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCost: Double = 0.0
)

interface AiProvider {
    val info: ProviderInfo

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        settings: AppSettings
    ): String

    fun sendMessageStream(
        messages: List<ChatMessage>,
        settings: AppSettings
    ): Flow<String>

    fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double
}