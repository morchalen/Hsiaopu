// 声明这个类在哪个包（文件夹）下
package com.example.hsiaopu.data.local

// 导入 Room 框架需要的注解
import androidx.room.Entity          // 标记这是一个数据库表
import androidx.room.ForeignKey      // 标记外键关联
import androidx.room.Index           // 标记索引（加速查询）
import androidx.room.PrimaryKey      // 标记主键

/**
 * MessageEntity 消息实体类
 * 
 * 这个类对应数据库里的一张表，每条消息存为一行
 * 
 * 表名：messages
 */
@Entity(
    tableName = "messages",  // 告诉 Room：这个类对应数据库里名叫 "messages" 的表
    
    // ===== 外键约束（Foreign Key）=====
    // 作用：保证数据的一致性，每条消息都必须属于一个存在的对话
    foreignKeys = [
        ForeignKey(
            // 关联到哪个表？ → ConversationEntity 对应的 "conversations" 表
            entity = ConversationEntity::class,
            
            // 父表（conversations）的哪一列？ → id 列
            parentColumns = ["id"],
            
            // 子表（messages）的哪一列？ → conversationId 列
            childColumns = ["conversationId"],
            
            // 当父表（对话）被删除时，子表（消息）怎么办？
            // CASCADE = 级联删除 → 对话删了，它下面的所有消息也自动删除
            onDelete = ForeignKey.CASCADE
        )
    ],
    
    // ===== 索引（Index）=====
    // 作用：提高查询速度
    // 场景：经常按 conversationId 查消息，建索引后查询更快
    indices = [Index("conversationId")]
)
data class MessageEntity(
    // ===== 主键 =====
    // @PrimaryKey：标记这是主键（每行的唯一标识）
    // autoGenerate = true：插入时数据库自动生成 id（1, 2, 3...）
    // val id: Long = 0：定义变量，类型 Long，默认值 0
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    // ===== 外键列 =====
    // 关联到哪个对话（ConversationEntity 的 id）
    // 比如：conversationId = 3 表示这条消息属于第 3 个对话
    val conversationId: Long,
    
    // ===== 角色 =====
    // 谁发的这条消息？
    // "user" = 用户发的
    // "assistant" = AI 发的
    // "system" = 系统发的
    val role: String,
    
    // ===== 消息内容 =====
    // 实际聊天的文字内容
    val content: String,
    
    // ===== 时间戳 =====
    // 消息发送的时间（毫秒数，从 1970 年开始计算）
    // 默认值是当前时间 System.currentTimeMillis()
    val timestamp: Long = System.currentTimeMillis()
)