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

/**
 * 数据库依赖注入模块
 * 作用：告诉 Hilt 如何创建数据库和 DAO 对象
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // 提供 AppDatabase 实例，整个 App 只有一个实例（单例）
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hsiaopu_db"
        ).build()  // ← 没有 addMigrations，直接 build
    }

    // 提供 ConversationDao
    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    // 提供 MessageDao
    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    // 提供 ShellHistoryDao
    @Provides
    fun provideShellHistoryDao(db: AppDatabase): ShellHistoryDao = db.shellHistoryDao()
}