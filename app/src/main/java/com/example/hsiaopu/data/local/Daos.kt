package com.example.hsiaopu.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
//DAO：Data Access Object，数据库访问对象

@Dao
//操作会话表
//这里虽然是接口，但是没有实现类，这是Room框架的一个特性，
//因为Room框架会自动实现这个接口，生成对应的数据库操作方法。
interface ConversationDao {
    // 获取conversation表里面的所有行，然后按照updatedAt字段从新到旧排序
    /**
     * 每当有人调用 getAllConversations() 这个函数时，
     * 就自动执行@Query这条 SQL 语句，
     * 然后把查到的结果包装成 Flow<List<ConversationEntity>> 返回。
     */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    //Flow = 数据变了就自动使用冷流发出去，仅此而已。

    // 根据id查询数据（冒号 + 参数名  就是把kt的形参传递给SQL语句中的占位符 :id）
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    // 注解	    要不要写 SQL原因
    // @Insert	❌ 不写	    插入数据的 SQL 是固定的，Room 自动生成
    // @Delete	❌ 不写	    按主键删除，也是固定的
    // @Update	❌ 不写	    按主键更新，也是固定的
    // @Query	✅ 必须写	查询千变万化（条件、排序、联表），Room 猜不到

    // "插入一条新对话"
    @Insert//不用手写SQL语句，Room会自动生成
    suspend fun insertConversation(conversation: ConversationEntity): Long

    // "更新一条对话"
    @Update//按主键更新
    suspend fun updateConversation(conversation: ConversationEntity)

    // "删除一条对话"
    @Delete//按主键删除
    suspend fun deleteConversation(conversation: ConversationEntity)
}

@Dao
//操作消息表
interface MessageDao {
    // "把某个对话里的所有消息都拿来，按时间从旧到新排"
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>

// 注解	    要不要写 SQL	原因
// @Insert	❌ 不写	    插入数据的 SQL 是固定的，Room 自动生成
// @Delete	❌ 不写	    按主键删除，也是固定的
// @Update	❌ 不写	    按主键更新，也是固定的
// @Query	✅ 必须写	    查询千变万化（条件、排序、联表），Room 猜不到

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
//操作命令历史表
interface ShellHistoryDao {
// 注解	    要不要写 SQL	原因
// @Insert	❌ 不写	    插入数据的 SQL 是固定的，Room 自动生成
// @Delete	❌ 不写	    按主键删除，也是固定的
// @Update	❌ 不写	    按主键更新，也是固定的
// @Query	✅ 必须写	    查询千变万化（条件、排序、联表），Room 猜不到
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