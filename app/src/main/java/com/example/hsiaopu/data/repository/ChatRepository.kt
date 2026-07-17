package com.example.hsiaopu.data.repository

import com.example.hsiaopu.data.local.ConversationDao
import com.example.hsiaopu.data.local.ConversationEntity
import com.example.hsiaopu.data.local.MessageDao
import com.example.hsiaopu.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    suspend fun createConversation(title: String = "New Chat"): Long {
        val entity = ConversationEntity(title = title)
        return conversationDao.insertConversation(entity)
    }

    suspend fun updateConversationTitle(id: Long, title: String) {
        val conv = conversationDao.getConversationById(id)
        conv?.let {
            conversationDao.updateConversation(it.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteConversation(id: Long) {
        val conv = conversationDao.getConversationById(id)
        conv?.let { conversationDao.deleteConversation(it) }
    }

    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesByConversation(conversationId)

    suspend fun insertMessage(entity: MessageEntity): Long =
        messageDao.insertMessage(entity)

    suspend fun insertMessages(entities: List<MessageEntity>) =
        messageDao.insertMessages(entities)

    suspend fun deleteMessagesByConversation(conversationId: Long) =
        messageDao.deleteMessagesByConversation(conversationId)
}