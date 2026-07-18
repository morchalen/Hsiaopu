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
 * Hilt 依赖注入模块，负责提供 Room 数据库及其相关的 DAO 对象。
 * 
 * 该模块安装于 SingletonComponent 作用域，意味着提供的依赖在整个应用生命周期内保持单例。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供全局唯一的 AppDatabase 实例。
     * 
     * 使用 Room.databaseBuilder 创建数据库，数据库文件名为 "hsiaopu_db"。
     * 该实例在整个应用运行期间只会被创建一次，后续注入时复用同一实例。
     * 
     * @param context 应用上下文，由 Hilt 自动注入
     * @return AppDatabase 单例实例
     */
    @Provides//当有人需要这个东西时，请按这个方法创建它
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hsiaopu_db"
        ).build()
    }

    /**
     * 提供 ConversationDao 数据访问对象。
     * 
     * 通过已创建的 AppDatabase 实例获取对应的 DAO，用于操作会话表。
     * 
     * @param db AppDatabase 实例，由 Hilt 自动注入（复用 provideAppDatabase 提供的单例）
     * @return ConversationDao 实例
     */
    @Provides//当有人需要这个东西时，请按这个方法创建它获取他的返回值；
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()
    //举例：其他一个地方使用private val conversationDao: ConversationDao；
    //意思就是说，我要创建一个参数叫conversationDao，它的类型是ConversationDao；
    //然后，hilt就会去看有@Provides注解的函数，也没有哪个函数返回值是 ConversationDao；如果找到了，那就直接现场执行这个函数；然后获取返回值给他；
    
    @Provides//提供 MessageDao 数据访问对象。
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides//提供 ShellHistoryDao 数据访问对象。
    fun provideShellHistoryDao(db: AppDatabase): ShellHistoryDao = db.shellHistoryDao()
}