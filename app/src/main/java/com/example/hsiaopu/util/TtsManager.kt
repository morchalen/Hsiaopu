package com.example.hsiaopu.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * TTS（文字转语音）管理器，用于朗读 AI 回复文本。
 * 需要在 Compose 中通过 remember + DisposableEffect 管理生命周期。
 */
class TtsManager(context: Context) {

    companion object {
        private const val TAG = "Hsiaopu-TTS"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** TTS 初始化回调 */
    private var onInitCallback: (() -> Unit)? = null

    /** 朗读状态变化回调 */
    var onSpeakingStateChanged: ((isSpeaking: Boolean) -> Unit)? = null

    /** 当前是否正在朗读 */
    var isSpeaking: Boolean = false
        private set

    /**
     * 已知的 OEM TTS 引擎包名列表，用于默认引擎初始化失败时逐个尝试。
     */
    private val knownTtsEnginePackages = listOf(
        "com.oplus.ttsaccessibilityengine",  // OPPO / OnePlus
        "com.google.android.tts",            // Google
        "com.xiaomi.mibrain.speech",         // 小米
        "com.iflytek.speechcloud",           // 讯飞
        "com.baidu.duersdk.tts",            // 百度
    )

    /** 当前正在尝试的引擎索引（用于 fallback 链） */
    private var fallbackEngineIndex = -1

    init {
        logAvailableEngines(context)
        createTts(context, engine = null)
    }

    /** 打印所有系统可见的 TTS 引擎 */
    private fun logAvailableEngines(context: Context) {
        try {
            val pm = context.packageManager
            val intent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val services = pm.queryIntentServices(intent, android.content.pm.PackageManager.GET_META_DATA)
            if (services.isEmpty()) {
                Log.w(TAG, "系统未找到任何 TTS 引擎服务（可能缺少 <queries> 声明）")
            } else {
                Log.i(TAG, "系统可见的 TTS 引擎:")
                services.forEachIndexed { i, info ->
                    Log.i(TAG, "  [${i + 1}] ${info.serviceInfo.packageName}/${info.serviceInfo.name}" +
                            " (enabled=${info.serviceInfo.enabled})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询 TTS 引擎列表失败: ${e.message}")
        }
    }

    private fun createTts(context: Context, engine: String?) {
        val initListener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                onTtsReady()
            } else if (engine == null) {
                Log.w(TAG, "默认 TTS 引擎初始化失败(status=$status)，逐一尝试已知引擎")
                tryNextEngine(context)
            } else {
                Log.d(TAG, "引擎 $engine 初始化失败(status=$status)")
                tryNextEngine(context)
            }
        }

        tts = if (engine != null) {
            Log.d(TAG, "尝试指定引擎: $engine")
            TextToSpeech(context, initListener, engine)
        } else {
            TextToSpeech(context, initListener)
        }
    }

    /** 尝试下一个已知引擎 */
    private fun tryNextEngine(context: Context) {
        fallbackEngineIndex++
        if (fallbackEngineIndex >= knownTtsEnginePackages.size) {
            Log.e(TAG, "所有已知引擎均无法初始化，TTS 不可用")
            val pendingCallback = onInitCallback
            onInitCallback = null
            pendingCallback?.invoke()
            return
        }
        val pkg = knownTtsEnginePackages[fallbackEngineIndex]
        try {
            val holder = arrayOfNulls<TextToSpeech>(1)
            val newTts = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "✓ 引擎 $pkg 初始化成功")
                    try { tts?.shutdown() } catch (_: Exception) {}
                    tts = holder[0]
                    onTtsReady()
                } else {
                    Log.d(TAG, "引擎 $pkg 初始化失败(status=$status)")
                    try { holder[0]?.shutdown() } catch (_: Exception) {}
                    tryNextEngine(context)
                }
            }, pkg)
            holder[0] = newTts
        } catch (e: Exception) {
            Log.d(TAG, "引擎 $pkg 无法加载: ${e.message}")
            tryNextEngine(context)
        }
    }

    private fun onTtsReady() {
        isInitialized = true

        val engineInfo = try {
            tts?.let { t ->
                "${t.voice?.name ?: "unknown"}, engine=${t.defaultEngine}"
            }
        } catch (_: Exception) { "unknown" }
        Log.d(TAG, "TTS 初始化成功, engineInfo=$engineInfo")

        val localesToTry = listOf(
            Locale.SIMPLIFIED_CHINESE to "简体中文",
            Locale.CHINESE to "中文",
            Locale.US to "英语"
        )
        var localeSet = false
        for ((locale, label) in localesToTry) {
            val result = tts?.setLanguage(locale)
            Log.d(TAG, "设置语言 $label($locale): $result")
            if (result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_AVAILABLE
            ) {
                Log.i(TAG, "✓ 使用 $label TTS")
                localeSet = true
                break
            }
        }
        if (!localeSet) {
            Log.w(TAG, "所有语言均不可用，尝试默认引擎语言")
        }

        tts?.setSpeechRate(1.0f)
        tts?.setPitch(1.0f)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS 开始朗读: $utteranceId")
                isSpeaking = true
                onSpeakingStateChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS 朗读结束: $utteranceId")
                isSpeaking = false
                onSpeakingStateChanged?.invoke(false)
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS 朗读出错: $utteranceId")
                isSpeaking = false
                onSpeakingStateChanged?.invoke(false)
            }
        })

        warmupTts()

        val pendingCallback = onInitCallback
        onInitCallback = null
        pendingCallback?.invoke()
    }

    /** 对 TTS 引擎做一个静默预热，解决首次无声问题 */
    private fun warmupTts() {
        try {
            val warmupParams = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f)
            }
            tts?.speak(" ", TextToSpeech.QUEUE_FLUSH, warmupParams, "tts_warmup")
            Log.d(TAG, "预热 speak 完成")
        } catch (e: Exception) {
            Log.w(TAG, "预热失败", e)
        }
    }

    /**
     * 朗读指定的文本。
     * 如果 TTS 未初始化完成，会等待初始化后再朗读。
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.d(TAG, "TTS 未就绪，排队等待初始化后朗读")
            onInitCallback = {
                speakInternal(text)
                onInitCallback = null
            }
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        if (tts == null) {
            Log.e(TAG, "TTS 引擎为空，无法朗读")
            return
        }
        Log.d(TAG, "朗读文本: ${text.take(50)}...")

        val params = Bundle()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_utterance")
        Log.d(TAG, "speak() 返回值: $result")
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "speak() 返回 ERROR")
        }
    }

    /**
     * 排队朗读文本，追加到当前 TTS 队列末尾。
     * 用于自动播放多个 AI 回复。
     */
    fun speakQueued(text: String, utteranceId: String = "auto_play_${System.currentTimeMillis()}") {
        if (!isInitialized) {
            Log.d(TAG, "TTS 未就绪，无法加入自动播放队列")
            return
        }
        if (tts == null) {
            Log.e(TAG, "TTS 引擎为空，无法朗读")
            return
        }
        val params = Bundle()
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        Log.d(TAG, "speakQueued(${text.take(30)}...) 返回值: $result")
    }

    /** 停止当前朗读 */
    fun stop() {
        if (!isInitialized) {
            onInitCallback = null
            return
        }
        Log.d(TAG, "停止 TTS 朗读")
        tts?.stop()
        isSpeaking = false
        onSpeakingStateChanged?.invoke(false)
    }

    /** 释放 TTS 资源 */
    fun shutdown() {
        try {
            if (isInitialized) {
                tts?.stop()
                tts?.shutdown()
            }
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
    }
}

/**
 * 在 Composable 中记住一个 TtsManager 实例，并在离开时自动释放资源。
 */
@Composable
fun rememberTtsManager(): TtsManager {
    val context = LocalContext.current
    val manager = remember { TtsManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            manager.shutdown()
        }
    }
    return manager
}
