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

    /** TTS 未就绪时的 speak() 待处理列表（第一条直接朗读） */
    private val pendingSpeakTexts = mutableListOf<String>()

    /** TTS 未就绪时的 speakQueued() 待处理列表（追加到队列） */
    private val pendingQueueItems = mutableListOf<Pair<String, String>>()

    /** 首次真正朗读前是否需要预热（引擎首次朗读易无声；stop() 后也可能触发） */
    private var needsPrime = true

    /** utteranceId → 朗读文本，用于 onStart/onDone 时严格打印实际朗读的内容，定位"第一次朗读没声音"问题 */
    private val utteranceTexts = mutableMapOf<String, String>()

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
                // 预热/启动前发音不改变播放状态，避免 UI 误显示"正在播放"却无声
                if (utteranceId == "tts_warmup" || utteranceId == "tts_prime") return
                Log.d(TAG, "TTS 开始朗读: $utteranceId, 内容=${utteranceTexts[utteranceId] ?: "未知"}")
                isSpeaking = true
                onSpeakingStateChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "tts_warmup" || utteranceId == "tts_prime") return
                Log.d(TAG, "TTS 朗读结束: $utteranceId, 内容=${utteranceTexts[utteranceId] ?: "未知"}")
                utteranceTexts.remove(utteranceId)
                isSpeaking = false
                onSpeakingStateChanged?.invoke(false)
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == "tts_warmup" || utteranceId == "tts_prime") return
                Log.e(TAG, "TTS 朗读出错: $utteranceId, 内容=${utteranceTexts[utteranceId] ?: "未知"}")
                utteranceTexts.remove(utteranceId)
                isSpeaking = false
                onSpeakingStateChanged?.invoke(false)
            }
        })

        warmupTts()

        // 处理所有待处理的播放请求
        flushPendingRequests()
    }

    /** TTS 就绪后，统一处理所有待处理的 speak 和 speakQueued 请求 */
    private fun flushPendingRequests() {
        // 先处理 speak() 的待处理文本（最后一个 speak 会 flush 前面的 speak）
        val speakTexts = pendingSpeakTexts.toList()
        pendingSpeakTexts.clear()

        // 再处理 speakQueued() 的待处理队列
        val queueItems = pendingQueueItems.toList()
        pendingQueueItems.clear()

        // 先朗读最后一个 speak（因为 speak 使用 QUEUE_FLUSH，只保留最后一条）
        if (speakTexts.isNotEmpty()) {
            val lastSpeak = speakTexts.last()
            Log.d(TAG, "flushPendingRequests: 播放最后一个 speak 请求")
            speakInternal(lastSpeak)
        }

        // 追加所有 speakQueued 请求
        if (queueItems.isNotEmpty()) {
            Log.d(TAG, "flushPendingRequests: 追加 ${queueItems.size} 个排队播放请求")
            queueItems.forEach { (text, id) ->
                speakQueuedInternal(text, id)
            }
        }

        // 调用外部设置的初始化回调
        val pendingCallback = onInitCallback
        onInitCallback = null
        pendingCallback?.invoke()
    }

    /** 对 TTS 引擎做一个静默预热，解决首次无声问题（初始化时调用一次） */
    private fun warmupTts() {
        try {
            // 音量用 0.02 而非 0：部分引擎会直接丢弃"音量0"的发音，导致预热无效
            val warmupParams = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.02f)
            }
            tts?.speak("。", TextToSpeech.QUEUE_FLUSH, warmupParams, "tts_warmup")
            utteranceTexts["tts_warmup"] = "。（初始化预热）"
            Log.d(TAG, "预热 speak 完成")
        } catch (e: Exception) {
            Log.w(TAG, "预热失败", e)
        }
    }

    /**
     * 首次真正朗读前立即预热。
     * 初始化时的预热与首次朗读间隔太久，引擎音频管道可能已空闲，
     * 部分引擎（如 OPPO）"空闲后第一次朗读"仍会无声。在真正朗读前一刻再发一个
     * 近静音发音，确保引擎管道被激活。
     */
    private fun primeTtsIfNeeded() {
        if (!needsPrime) return
        needsPrime = false
        try {
            val primeParams = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.02f)
            }
            tts?.speak("。", TextToSpeech.QUEUE_FLUSH, primeParams, "tts_prime")
            utteranceTexts["tts_prime"] = "。（首次朗读前近静音预热）"
            Log.d(TAG, "首次朗读前预热（近静音）已发送")
        } catch (e: Exception) {
            Log.w(TAG, "首次朗读前预热失败", e)
        }
    }

    /**
     * 朗读指定的文本。
     * 如果 TTS 未初始化完成，会将请求加入待处理队列，等待初始化后再朗读。
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.d(TAG, "TTS 未就绪，speak() 加入待处理队列")
            pendingSpeakTexts.add(text)
            // 设置回调（如果还没设置），确保初始化后能处理所有待处理请求
            if (onInitCallback == null) {
                onInitCallback = {
                    flushPendingRequests()
                }
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
        primeTtsIfNeeded()
        Log.d(TAG, "朗读文本: ${text.take(50)}...")
        Log.d(TAG, "朗读文本(全文,严格打印): >>>$text<<<")

        val utteranceId = "tts_utterance_${System.currentTimeMillis()}"
        utteranceTexts[utteranceId] = text
        val params = Bundle()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Log.d(TAG, "speak() 返回值: $result, utteranceId=$utteranceId")
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "speak() 返回 ERROR")
        }
    }

    /**
     * 排队朗读文本，追加到当前 TTS 队列末尾。
     * 用于自动播放多个 AI 回复。
     * 如果 TTS 未就绪，会将请求加入待处理队列。
     */
    fun speakQueued(text: String, utteranceId: String = "auto_play_${System.currentTimeMillis()}") {
        if (!isInitialized) {
            Log.d(TAG, "TTS 未就绪，speakQueued() 加入待处理队列")
            pendingQueueItems.add(text to utteranceId)
            // 设置回调（如果还没设置），确保初始化后能处理所有待处理请求
            if (onInitCallback == null) {
                onInitCallback = {
                    flushPendingRequests()
                }
            }
            return
        }
        speakQueuedInternal(text, utteranceId)
    }

    /** 实际执行排队朗读（内部方法） */
    private fun speakQueuedInternal(text: String, utteranceId: String) {
        if (tts == null) {
            Log.e(TAG, "TTS 引擎为空，无法朗读")
            return
        }
        primeTtsIfNeeded()
        Log.d(TAG, "排队朗读(全文,严格打印): >>>$text<<<")
        utteranceTexts[utteranceId] = text
        val params = Bundle()
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        Log.d(TAG, "speakQueued(${text.take(30)}...) 返回值: $result, utteranceId=$utteranceId")
    }

    /** 停止当前朗读 */
    fun stop() {
        // 清理所有待处理的播放请求
        pendingSpeakTexts.clear()
        pendingQueueItems.clear()
        utteranceTexts.clear()
        onInitCallback = null

        if (!isInitialized) {
            return
        }
        Log.d(TAG, "停止 TTS 朗读")
        tts?.stop()
        isSpeaking = false
        // 部分引擎 stop() 后的第一次朗读也会无声，重新武装预热
        needsPrime = true
        onSpeakingStateChanged?.invoke(false)
    }

    /** 释放 TTS 资源 */
    fun shutdown() {
        try {
            // 清理所有待处理的播放请求
            pendingSpeakTexts.clear()
            pendingQueueItems.clear()
            onInitCallback = null

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
