package com.example.hsiaopu.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Vosk 语音识别辅助类。
 * 模型打包在 APK 的 assets 目录中，首次使用从 assets 解压，无需网络下载。
 */
class VoskSpeechHelper(private val context: Context) {

    companion object {
        private const val TAG = "VoskSpeechHelper"
        private const val ASSETS_MODEL_ZIP = "vosk-model-small-cn-0.22.zip"
        private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
        private const val SAMPLE_RATE = 16000.0f
    }

    var isModelReady: Boolean = false
        private set

    var isRecording: Boolean = false
        private set

    var onResult: ((String) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onStateChanged: ((State) -> Unit)? = null

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var isInitializing = false // 防止重复初始化

    enum class State {
        MODEL_EXTRACTING,
        MODEL_READY,
        RECORDING,
        RECOGNIZING,
        ERROR
    }

    private fun getModelPath(): String {
        return File(context.filesDir, MODEL_DIR_NAME).absolutePath
    }

    /** 验证模型目录是否包含必要的文件 */
    private fun isModelDirValid(): Boolean {
        val modelDir = File(getModelPath())
        if (!modelDir.exists() || !modelDir.isDirectory) return false

        val requiredDirs = listOf("am", "conf")
        for (dir in requiredDirs) {
            val subDir = File(modelDir, dir)
            if (!subDir.exists() || !subDir.isDirectory) {
                Log.w(TAG, "模型目录缺少关键子目录: $dir")
                return false
            }
            if (subDir.listFiles().isNullOrEmpty()) {
                Log.w(TAG, "模型子目录为空: $dir")
                return false
            }
        }
        return true
    }

    /**
     * 检查 Model 的原生指针是否有效。
     */
    private fun isModelNativePointerValid(): Boolean {
        val m = model ?: return false
        return try {
            val ptrTypeClass = m::class.java.superclass ?: return false
            val pointerField = ptrTypeClass.getDeclaredField("pointer")
            pointerField.isAccessible = true
            val ptr = pointerField.get(m)
            ptr != null
        } catch (e: Exception) {
            Log.w(TAG, "检查原生指针失败，假设有效", e)
            true
        }
    }

    /**
     * 初始化模型：已就绪则跳过，已下载则加载，否则从 assets 解压。
     */
    fun initialize(scope: CoroutineScope) {
        if (isModelReady || isInitializing) return
        if (isModelDirValid()) {
            loadModel()
            return
        }
        isInitializing = true
        onStateChanged?.invoke(State.MODEL_EXTRACTING)
        scope.launch {
            val success = extractModelFromAssets()
            withContext(Dispatchers.Main) {
                isInitializing = false
                if (success) {
                    loadModel()
                } else {
                    onStateChanged?.invoke(State.ERROR)
                }
            }
        }
    }

    /** 从 assets 解压模型 ZIP 到 filesDir */
    private suspend fun extractModelFromAssets(): Boolean = withContext(Dispatchers.IO) {
        try {
            context.assets.open(ASSETS_MODEL_ZIP).use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    val buffer = ByteArray(8192)
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val targetFile = File(context.filesDir, entry.name)
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos ->
                                var bytesRead: Int
                                while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                                    fos.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
            Log.d(TAG, "从 assets 解压模型成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "从 assets 解压模型失败", e)
            false
        }
    }

    private fun loadModel() {
        try {
            if (!isModelDirValid()) {
                Log.e(TAG, "模型目录无效")
                deleteModelDir()
                onStateChanged?.invoke(State.ERROR)
                return
            }

            model = Model(getModelPath())

            if (!isModelNativePointerValid()) {
                Log.e(TAG, "模型原生指针无效，模型文件可能损坏")
                model = null
                deleteModelDir()
                onStateChanged?.invoke(State.ERROR)
                return
            }

            isModelReady = true
            onStateChanged?.invoke(State.MODEL_READY)
            Log.d(TAG, "Vosk 模型加载成功")
        } catch (e: Throwable) {
            Log.e(TAG, "模型加载失败", e)
            model = null
            isModelReady = false
            onStateChanged?.invoke(State.ERROR)
        }
    }

    /** 删除模型目录 */
    private fun deleteModelDir() {
        try {
            val modelDir = File(getModelPath())
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
                Log.d(TAG, "已删除损坏的模型目录")
            }
        } catch (e: Exception) {
            Log.w(TAG, "删除模型目录失败", e)
        }
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording() {
        if (!isModelReady || isRecording) return
        if (!hasPermission()) {
            onStateChanged?.invoke(State.ERROR)
            return
        }

        val m = model ?: run {
            Log.e(TAG, "model 为空，无法开始录音")
            isModelReady = false
            onStateChanged?.invoke(State.ERROR)
            return
        }

        try {
            recognizer = Recognizer(m, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onResult(hypothesis: String) {
                    Log.d(TAG, "识别结果: $hypothesis")
                    val text = parseResult(hypothesis)
                    if (text.isNotBlank()) {
                        onResult?.invoke(text)
                    }
                    onStateChanged?.invoke(State.MODEL_READY)
                }

                override fun onPartialResult(hypothesis: String) {
                    val text = parseResult(hypothesis)
                    if (text.isNotBlank()) {
                        onPartialResult?.invoke(text)
                    }
                }

                override fun onFinalResult(hypothesis: String) {
                    Log.d(TAG, "最终结果: $hypothesis")
                    val text = parseResult(hypothesis)
                    if (text.isNotBlank()) {
                        onResult?.invoke(text)
                    }
                    onStateChanged?.invoke(State.MODEL_READY)
                }

                override fun onError(e: Exception) {
                    Log.e(TAG, "识别错误", e)
                    isRecording = false
                    onStateChanged?.invoke(State.ERROR)
                }

                override fun onTimeout() {
                    Log.w(TAG, "识别超时")
                    isRecording = false
                    speechService?.stop()
                    speechService = null
                    onStateChanged?.invoke(State.MODEL_READY)
                }
            })

            isRecording = true
            onStateChanged?.invoke(State.RECORDING)
            Log.d(TAG, "开始录音识别")
        } catch (e: Exception) {
            Log.e(TAG, "录音启动失败", e)
            isRecording = false
            onStateChanged?.invoke(State.ERROR)
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        onStateChanged?.invoke(State.RECOGNIZING)

        try {
            speechService?.stop()
            speechService = null
            recognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "SpeechService 停止异常", e)
        }

        onStateChanged?.invoke(State.MODEL_READY)
    }

    private fun parseResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            json.optString("text", "").trim()
        } catch (e: Exception) {
            Log.w(TAG, "结果解析失败", e)
            ""
        }
    }

    fun release() {
        try {
            isRecording = false
            speechService?.stop()
            speechService = null
            recognizer = null
            model = null
            isModelReady = false
            isInitializing = false
        } catch (e: Exception) {
            Log.w(TAG, "资源释放异常", e)
        }
    }
}
