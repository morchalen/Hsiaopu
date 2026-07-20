package com.example.hsiaopu.data.local   // 包名：放在 data/local 下，很规范
// 数据库本身
// 数据库实例
import androidx.room.Database            // Room 的数据库注解
import androidx.room.RoomDatabase        // Room 数据库基类

@Database(                              // 👈 标记这个类是数据库
    entities = [                        // 👈 声明这个数据库有哪些表
        ConversationEntity::class,      // 会话表
        MessageEntity::class,           // 消息表
        ShellHistoryEntity::class       // Shell 历史记录表
    ],
    version = 3,                        // 👈 数据库版本号，现在是第 3 版
    exportSchema = false                // 👈 不导出 schema 文件（调试时 false）
)
abstract class AppDatabase : RoomDatabase() {  // 👈 继承 RoomDatabase
    // 声明 DAO（数据访问对象），每个表对应一个 DAO
    //抽象类，用来定义“有什么表”和“提供什么 DAO”，里面不能有具体的实现代码。
    abstract fun conversationDao(): ConversationDao   // 操作会话表【返回值是ConversationDao实例（通过RoomDatabase的createDao方法创建）】
    abstract fun messageDao(): MessageDao             // 操作消息表【返回值是MessageDao实例（通过RoomDatabase的createDao方法创建）】
    abstract fun shellHistoryDao(): ShellHistoryDao   // 操作 Shell 历史表【返回值是ShellHistoryDao实例（通过RoomDatabase的createDao方法创建）】
}
    //         UI(Activity/Compose)
    //                  │
    //                  ▼
    //            ViewModel (VM)
    //                  │
    //       ┌──────────┴──────────┐
    //       │                     │
    //       ▼                     ▼
    //  Repository           ShellCommandBus
    //       │                     │
    //       ▼                     │
    //      DAO                    │
    //       │                     │
    //       ▼                     │
    //  AppDatabase                │
    //       │                     │
    //       ▼                     ▼
    //     SQLite            Shell服务/终端