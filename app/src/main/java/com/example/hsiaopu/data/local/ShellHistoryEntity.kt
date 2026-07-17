package com.example.hsiaopu.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shell_history")// 定义数据库表名
data class ShellHistoryEntity(// 定义数据库实体类，是数据类
    // 主键，自动递增
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    // 命令
    val command: String,
    // 输出
    val stdout: String,
    // 错误输出
    val stderr: String,
    // 退出码，默认值为-1
    @ColumnInfo(defaultValue = "-1") val exitCode: Int = -1,
    // 时间戳，默认值为当前时间戳
    val timestamp: Long = System.currentTimeMillis()
)