package com.example.hsiaopu.di

import android.content.Context
import androidx.room.Room
import com.example.hsiaopu.data.local.AppDatabase
import com.example.hsiaopu.data.local.ConversationDao
import com.example.hsiaopu.data.local.MessageDao
import com.example.hsiaopu.data.local.ShellHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

//数据库依赖注入模块告诉 Hilt 如何创建数据库和 DAO 对象
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // 提供 AppDatabase 实例，整个 App 只有一个实例（单例）
    @Provides                       // 👈 “我是仓库管理员，谁要货就来找我”
    @Singleton                      // @Singleton 保证的是“这个函数造出来的东西”全局只有一个。【Single  ton：唯一  的东西】
    fun provideAppDatabase(         // 👈 “这个函数的名字叫：提供数据库”
        @ApplicationContext context: Context  // 👈 “我需要App的身份证（上下文）才能干活”
    ): AppDatabase {                // 👈 “我造出来的东西是一个AppDatabase类”
                    return Room.databaseBuilder( // 👈 “开始动工盖房子”
                        context,                // 👈 “在App的地盘上盖”
                        AppDatabase::class.java,// 👈 “照着这张设计图（AppDatabase类）盖”
                        "hsiaopu_db"            // 👈 “房子名字叫hsiaopu_db”
                    ).build()                   // 👈 “盖好了，交付使用！”
                    //造出了一个完整的数据库，里面包含了你在 AppDatabase 里声明的所有表（ConversationEntity、MessageEntity、ShellHistoryEntity）
                    }
    //DAO（Data Access Object，数据访问对象）是 Room 提供的一种机制，用于在应用中访问数据库中的数据。

    
    @Provides//供应注解，当有人调用provideConversationDao函数的时候，自动去使用db.conversationDao()返回一个ConversationDao实例
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    
    @Provides//供应注解，当有人调用provideMessageDao函数的时候，自动去使用db.messageDao()返回一个MessageDao实例
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    
    @Provides//供应注解，当有人调用provideShellHistoryDao函数的时候，自动去使用db.shellHistoryDao()返回一个ShellHistoryDao实例
    fun provideShellHistoryDao(db: AppDatabase): ShellHistoryDao = db.shellHistoryDao()
}