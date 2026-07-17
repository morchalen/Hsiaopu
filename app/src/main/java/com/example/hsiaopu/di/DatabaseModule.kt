package com.example.hsiaopu.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS shell_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    command TEXT NOT NULL,
                    stdout TEXT NOT NULL,
                    stderr TEXT NOT NULL,
                    exitCode INTEGER NOT NULL DEFAULT -1,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hsiaopu_db"
        ).addMigrations(MIGRATION_1_2)
         .build()
    }

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideShellHistoryDao(db: AppDatabase): ShellHistoryDao = db.shellHistoryDao()
}