package com.example.hsiaopu.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
// 把 Kotlin 数据类（data class）标记为数据库里的一张表。
@Entity(tableName = "conversations")// 定义数据库表名
data class ConversationEntity(// 定义数据库实体类，是数据类
    // 主键，自动递增
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,//数据库里面对应的id字段
    // 聊天标题
    val title: String = "新对话",//数据库里面对应的title字段
    // 创建时间，默认值为当前时间戳
    val createdAt: Long = System.currentTimeMillis(),//数据库里面对应的createdAt字段
    // 更新时间，默认值为当前时间戳
    val updatedAt: Long = System.currentTimeMillis()//数据库里面对应的updatedAt字段
)