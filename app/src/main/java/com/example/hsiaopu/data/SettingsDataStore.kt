package com.example.hsiaopu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 扩展属性：为 Context 创建一个 DataStore 实例，用于持久化存储键值对
// 数据会保存在文件名为 "hsiaopu_settings" 的本地文件中
// by lazy 委托确保只有在第一次访问时才创建，整个 App 生命周期只有一个实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hsiaopu_settings")

/**
 * 应用设置数据存储类
 * 使用 Android DataStore（替代 SharedPreferences）来持久化保存 App 的各项配置
 * @Singleton 表示整个 App 中只有一个实例（单例模式）
 * @Inject 表示由 Hilt 依赖注入框架自动提供 context 参数
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context // 应用级别的 Context，不会造成内存泄漏
) {
    // companion object 中的内容相当于 Java 的静态成员，属于类本身而不是实例
    companion object {
        // ── 定义所有存储键（Key），每个 Key 对应一个要保存的数据项 ──

        // API 相关配置
        private val KEY_API_KEY = stringPreferencesKey("api_key")               // API 密钥
        private val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")     // API 请求地址
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")         // 使用的模型名称
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")   // 系统提示词
        private val KEY_TEMPERATURE = doublePreferencesKey("temperature")       // 温度参数（控制回答的随机性）
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")            // 最大 Token 数
        private val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")       // AI 服务提供商 ID

        // 主题与外观配置
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme")         // 深色主题模式
        private val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")     // 强调色
        private val KEY_FONT_SCALE = intPreferencesKey("font_scale")            // 字体缩放

        // 引导页状态
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed") // 引导页是否已完成
    }

    // ── 数据流（Flow）：实时观察数据变化 ──

    /**
     * API 设置数据流
     * 从 DataStore 中读取数据，映射为 AppSettings 对象
     * Flow 是响应式的，当数据发生变化时，所有订阅者会自动收到新的值
     * ?: 是 Elvis 运算符，如果左边的值为 null，就使用右边的默认值
     */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiKey = prefs[KEY_API_KEY] ?: "sk-fad9ba893a764a909157d0426a1a6954",                              // 默认 API Key
            apiEndpoint = prefs[KEY_API_ENDPOINT] ?: "https://api.deepseek.com/v1/chat/completions",        // 默认 DeepSeek API
            modelName = prefs[KEY_MODEL_NAME] ?: "deepseek-chat",                                           // 默认模型
            systemPrompt = prefs[KEY_SYSTEM_PROMPT] ?: "你是一个智能AI助手，请用简洁、专业的方式回答用户的问题。",  // 默认提示词
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7,                                                    // 默认温度 0.7
            maxTokens = prefs[KEY_MAX_TOKENS] ?: 2048,                                                      // 默认最大 Token 数
            providerId = prefs[KEY_PROVIDER_ID] ?: "deepseek"                                               // 默认提供商
        )
    }

    /**
     * 主题设置数据流
     * 订阅后可以实时获取主题相关的配置变化
     */
    val themeSettingsFlow: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            isDarkTheme = prefs[KEY_DARK_THEME] ?: "system",  // 默认跟随系统
            accentColor = prefs[KEY_ACCENT_COLOR] ?: "purple", // 默认紫色
            fontScale = prefs[KEY_FONT_SCALE] ?: 2             // 默认缩放等级 2
        )
    }

    // ── 更新数据的挂起函数（suspend function） ──
    // suspend 函数是协程中的"可暂停函数"，只能在协程或其他 suspend 函数中调用
    // edit { } 方法会原子性地修改 DataStore，保证数据一致性

    /** 更新 API 密钥 */
    suspend fun updateApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    /** 更新 API 请求地址 */
    suspend fun updateApiEndpoint(endpoint: String) {
        context.dataStore.edit { it[KEY_API_ENDPOINT] = endpoint }
    }

    /** 更新 AI 模型名称 */
    suspend fun updateModelName(model: String) {
        context.dataStore.edit { it[KEY_MODEL_NAME] = model }
    }

    /** 更新系统提示词 */
    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    /** 更新温度参数（0.0 ~ 2.0，越高回答越随机） */
    suspend fun updateTemperature(temp: Double) {
        context.dataStore.edit { it[KEY_TEMPERATURE] = temp }
    }

    /** 更新最大 Token 数 */
    suspend fun updateMaxTokens(tokens: Int) {
        context.dataStore.edit { it[KEY_MAX_TOKENS] = tokens }
    }

    /** 更新 AI 服务提供商 ID */
    suspend fun updateProviderId(id: String) {
        context.dataStore.edit { it[KEY_PROVIDER_ID] = id }
    }

    /** 更新深色主题模式（"system" / "light" / "dark"） */
    suspend fun updateDarkTheme(isDark: String) {
        context.dataStore.edit { it[KEY_DARK_THEME] = isDark }
    }

    /** 更新主题强调色 */
    suspend fun updateAccentColor(color: String) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = color }
    }

    /** 更新字体缩放比例 */
    suspend fun updateFontScale(scale: Int) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = scale }
    }

    /**
     * 字体缩放数据流（单独读取，方便在组件中直接订阅）
     * 如果未设置过，默认返回 2
     */
    val fontScaleFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SCALE] ?: 2
    }

    // ── 功能引导相关 ──

    /**
     * 检查某个功能引导是否已经被用户看过
     * @param key 功能引导的标识键
     * @return true 表示已看过，false 表示还没看过
     * .first() 会从 Flow 中取出当前值并挂起等待，取到后立即返回
     */
    suspend fun hasSeenGuide(key: FeatureGuideKey): Boolean {
        return context.dataStore.data.map { prefs ->
            // 从 DataStore 中读取该引导的布尔值，未找到则默认为 false（没看过）
            prefs[booleanPreferencesKey(key.key)] ?: false
        }.first()
    }

    /**
     * 将某个功能引导标记为"已看"
     * 下次调用 hasSeenGuide() 就会返回 true
     */
    suspend fun markGuideSeen(key: FeatureGuideKey) {
        context.dataStore.edit { it[booleanPreferencesKey(key.key)] = true }
    }

    /**
     * 这是一个挂起函数（suspend function），所以它只能在协程（Coroutine）中被调用。
     * 它会从 DataStore 中读取数据，因为 DataStore 的读取是异步且基于 Flow 的，
     * 所以需要用挂起的方式等待 Flow 返回结果。
     * 检查 App 引导页（Onboarding）是否已经完成
     * 用于判断是否需要在启动时显示引导页
     * @return true 表示引导页已完成，false 表示还没完成
     */
    suspend fun isOnboardingCompleted(): Boolean {//suspend挂起，
        return context.dataStore.data.map { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] ?: false  // 默认 false，即首次使用会显示引导页
        }.first()
    }

    /**
     * 标记引导页已完成
     * 用户在引导页点击"完成"后调用，将 onboarding_completed 设为 true
     * 下次启动 App 时，isOnboardingCompleted() 返回 true，就不会再显示引导页了
     */
    suspend fun markOnboardingCompleted() {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }
}

/**
 * 主题设置数据类
 * 用于封装深色主题、强调色、字体缩放三项外观配置
 */
data class ThemeSettings(
    val isDarkTheme: String = "system",   // 深色主题模式："system" / "light" / "dark"
    val accentColor: String = "purple",   // 强调色名称
    val fontScale: Int = 2                // 字体缩放等级
)