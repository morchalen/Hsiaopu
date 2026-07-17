package com.example.hsiaopu.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
//DAO：Data Access Object，数据库访问对象
@Dao
interface ConversationDao {
    // "把所有的对话都给我，按更新时间从新到旧排"
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    // "把 id = 某个值 的那个对话给我"
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    // "插入一条新对话"
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    // "更新一条对话"
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    // "删除一条对话"
    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
}

@Dao
interface MessageDao {
    // "把某个对话里的所有消息都拿来，按时间从旧到新排"
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>

    // "存一条消息"
    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    // "一次性存多条消息"
    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    // "删除多条消息"
    @Delete
    suspend fun deleteMessages(messages: List<MessageEntity>)

    // "把某个对话下的所有消息全部删掉"
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long)
}
@Dao
interface ShellHistoryDao {
    // "把所有 Shell 命令历史拿来，按时间排"
    @Query("SELECT * FROM shell_history ORDER BY timestamp ASC")
    fun getAllHistory(): Flow<List<ShellHistoryEntity>>

    // 同上，但是一次性获取（不自动更新）
    @Query("SELECT * FROM shell_history ORDER BY timestamp ASC")
    suspend fun getAllHistorySync(): List<ShellHistoryEntity>

    // "存一条命令历史"
    @Insert
    suspend fun insertHistory(history: ShellHistoryEntity): Long

    // "删除一条命令历史"
    @Delete
    suspend fun deleteHistory(history: ShellHistoryEntity)

    // "清空所有命令历史"
    @Query("DELETE FROM shell_history")
    suspend fun clearAllHistory()

    // "统计一共有多少条命令历史"
    @Query("SELECT COUNT(*) FROM shell_history")
    suspend fun getHistoryCount(): Int
}