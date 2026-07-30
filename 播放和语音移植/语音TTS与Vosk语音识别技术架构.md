# HsiaoWear 语音 TTS 与 Vosk 语音识别技术架构

> **迁移指南**：如需将语音功能迁移到另一个项目，以下是所有需要复制的文件和对应位置。

## 迁移指南：文件清单

### 必须复制的文件

| 文件路径 | 作用 | 备注 |
|---|---|---|
| [`app/src/main/java/.../util/TtsManager.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/TtsManager.kt) | TTS 核心管理类 | 包含 `TtsManager` 类和 `rememberTtsManager()` Composable 辅助函数 |
| [`app/src/main/java/.../util/VoskSpeechHelper.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/VoskSpeechHelper.kt) | Vosk 语音识别管理类 | 模型下载、加载、录音、识别回调 |
| [`app/src/main/java/.../ui/screen/LobsterScreen.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/ui/screen/LobsterScreen.kt) | AI 对话页面 | 集成 TTS + Vosk + 录音弹窗 + 自动播放的完整 UI |
| [`app/src/main/AndroidManifest.xml`](file:///z:/2projects/HsiaoWear/app/src/main/AndroidManifest.xml) | 权限声明 | `RECORD_AUDIO` 权限 + TTS 引擎可见性 `<queries>` |

### 依赖配置

| 配置文件 | 关键配置 |
|---|---|
| [`app/build.gradle.kts`](file:///z:/2projects/HsiaoWear/app/build.gradle.kts) | Vosk Android 依赖 + JNA AAR 替换 |
| [`gradle/libs.versions.toml`](file:///z:/2projects/HsiaoWear/gradle/libs.versions.toml) | `vosk-android` 版本定义 |

### 可选/辅助文件

| 文件路径 | 作用 | 备注 |
|---|---|---|
| [`app/src/main/jniLibs/`](file:///z:/2projects/HsiaoWear/app/src/main/jniLibs/) | JNA 原生库 | 需手动放置 `libjnidispatch.so` 到各架构目录 |
| [`app/src/main/java/.../ui/components/CommonComponents.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/ui/components/CommonComponents.kt) | 通用 UI 组件 | 如用到 `EmptyState` 等组件需一并复制 |

### 迁移步骤

1. 复制 `TtsManager.kt`、`VoskSpeechHelper.kt`、`LobsterScreen.kt` 到新项目的对应包路径
2. 在 `build.gradle.kts` 中添加 Vosk + JNA 依赖
3. 在 `AndroidManifest.xml` 中添加 `RECORD_AUDIO` 权限和 TTS 引擎 `<queries>` 声明
4. 将 `jniLibs/` 中的 JNA `.so` 文件放置到新项目的 `app/src/main/jniLibs/` 目录
5. 在新项目中创建一个 Compose 页面，调用 `LobsterScreen(viewModel, paddingValues)`
6. 需要提供 `LobsterViewModel`（Hilt 注入）或替换为其他 ViewModel

---

## 目录

1. [TTS 文字朗读系统](#1-tts-文字朗读系统)
2. [Vosk 语音识别系统](#2-vosk-语音识别系统)
3. [自动播放功能](#3-自动播放功能)
4. [录音状态浮层（微信风格）](#4-录音状态浮层微信风格)
5. [发送逻辑与异步处理](#5-发送逻辑与异步处理)
6. [依赖与配置](#6-依赖与配置)
7. [常见问题与调试](#7-常见问题与调试)

---

## 1. TTS 文字朗读系统

### 1.1 架构概述

```
LobsterScreen (UI)
    │  点击朗读按钮 / 自动播放触发 / 开始录音时停止
    ▼
TtsManager (核心管理类)
    │  speak() / speakQueued() / stop() / shutdown()
    ▼
Android TextToSpeech API
    │  bindService → TTS_SERVICE
    ▼
TTS Engine (Google / OPPO / 小米 / 讯飞 / 百度)
    │  synthesizeToFile / speak
    ▼
AudioTrack → 扬声器
```

### 1.2 TtsManager 类 ([`util/TtsManager.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/TtsManager.kt))

#### 生命周期

```
初始化 → 引擎发现 → 绑定 → 就绪 → speak/stop → shutdown
                                               │
                                           开始录音时自动 stop()
```

#### 引擎发现与 fallback 机制

```kotlin
// 1. 默认 TextToSpeech(context, listener) → 依赖系统 TTS 服务框架
// 失败时逐一尝试已知引擎
val knownTtsEnginePackages = listOf(
    "com.oplus.ttsaccessibilityengine",  // OPPO / OnePlus
    "com.google.android.tts",            // Google
    "com.xiaomi.mibrain.speech",         // 小米
    "com.iflytek.speechcloud",           // 讯飞
    "com.baidu.duersdk.tts",            // 百度
)
```

**关键方法：**

| 方法 | 功能 | TTS 队列模式 |
|---|---|---|
| `speak(text)` | 朗读文本（打断当前） | `QUEUE_FLUSH` |
| `speakQueued(text)` | 追加到队列末尾 | `QUEUE_ADD` |
| `stop()` | 停止朗读清空队列 | `QUEUE_FLUSH` + 空文本 |
| `shutdown()` | 释放资源 | — |

#### 初始化流程

```
logAvailableEngines()        → 通过 PackageManager 查询所有 TTS_SERVICE
createTts(context, null)     → 默认 TextToSpeech(context)
├── 成功 → onTtsReady()      → 设置语言、语速、音调、UtteranceProgressListener
└── 失败 → tryNextEngine()   → 逐一尝试 knownTtsEnginePackages
    ├── 成功 → onTtsReady()
    └── 全部失败 → 日志 "TTS 不可用"
```

#### 引擎可见性修复（Android 11+）

```xml
<!-- AndroidManifest.xml - 关键！Android 11+ 包可见性限制 -->
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

不加此声明，`PackageManager.queryIntentServices(TTS_SERVICE)` 返回空列表。

#### 语言设置策略

```kotlin
// 依次尝试，取第一个可用的
val localesToTry = listOf(
    Locale.SIMPLIFIED_CHINESE to "简体中文",
    Locale.CHINESE to "中文",
    Locale.US to "英语"
)
// 判断标准：LANG_COUNTRY_AVAILABLE || LANG_AVAILABLE
```

#### 预热机制

```kotlin
private fun warmupTts() {
    // 静默预热（音量 0），解决小米/OPPO 引擎首次无声
    val warmupParams = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f)
    }
    tts?.speak(" ", TextToSpeech.QUEUE_FLUSH, warmupParams, "tts_warmup")
}
```

#### 回调监听

```kotlin
tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?)  // 开始朗读
    override fun onDone(utteranceId: String?)   // 朗读结束
    override fun onError(utteranceId: String?)  // 朗读出错
})
// isSpeaking 状态 + onSpeakingStateChanged 回调
```

#### Compose 生命周期管理

```kotlin
@Composable
fun rememberTtsManager(): TtsManager {
    val context = LocalContext.current
    val manager = remember { TtsManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            manager.shutdown()  // 离开页面自动释放
        }
    }
    return manager
}
```

#### 开始录音时自动停止 TTS

```kotlin
onVoiceStart = {
    ttsManager.stop()           // 停止 TTS 朗读
    speakingMessageId = null    // 清除朗读状态
    voskHelper.startRecording()
    ...
}
```

---

## 2. Vosk 语音识别系统

### 2.1 架构概述

```
LobsterScreen (UI)
    │  按下语音按钮 → startRecording()
    ▼
VoskSpeechHelper (管理类)
    │  加载模型 → 启动 SpeechService
    ▼
Vosk Native Library (libvosk.so)
    │  麦克风音频流 → 解码
    ▼
onPartialResult → 实时更新 inputText （输入框可见）
onResult        → 最终结果 → 自动发送（松手后）
```

### 2.2 VoskSpeechHelper 类 ([`util/VoskSpeechHelper.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/VoskSpeechHelper.kt))

#### 状态机

```
IDLE → MODEL_DOWNLOADING → MODEL_READY → RECORDING → IDLE
                                                    │
                                                ERROR (重试)
```

#### 模型下载与加载

```kotlin
// 模型来源：AlphaCephei 中文小模型
private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"

// 存储在 App 私有目录
val modelDir = File(context.filesDir, MODEL_DIR_NAME)
```

**关键验证：** 加载前检查目录完整性

```kotlin
// 检查 modelDir 下是否包含 am/、conf/ 等关键子目录
val requiredDirs = listOf("am", "conf")
val missing = requiredDirs.filter { !File(modelDir, it).exists() }
```

如果目录不完整或原生指针无效，自动删除并重新下载。

#### 录音流程

```kotlin
// 开始录音
fun startRecording() {
    speechService?.startListening(object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            // 实时部分结果 → 回调 → 更新 inputText（输入框实时显示）
        }
        override fun onResult(hypothesis: String) {
            // 完整分段结果 → 回调 → 松手时自动发送
        }
        override fun onFinalResult(hypothesis: String) {
            // 最终结果（停止时触发）→ 回调 → 自动发送
        }
        override fun onError(e: Exception) { ... }
        override fun onTimeout() { ... }
    })
}

// 停止录音
fun stopRecording() {
    speechService?.stop()    // 会同步/异步触发 onResult / onFinalResult
    speechService = null
    isRecording = false
}
```

#### 权限处理

```kotlin
// AndroidManifest.xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

// 运行时请求
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        voskHelper.initialize(scope)
        voskHelper.startRecording()
    }
}
```

### 2.3 JNA 依赖处理（关键坑）

Vosk Android 依赖 JNA（Java Native Access），但传递依赖是 JAR 版本，不包含 Android 的 `.so` 文件。

```kotlin
// build.gradle.kts - 正确配置
implementation(libs.vosk.android) {
    exclude(group = "net.java.dev.jna", module = "jna")  // 排除 JAR
}
implementation("net.java.dev.jna:jna:5.14.0@aar")        // 显式 AAR
```

JNA AAR 包含 `libjnidispatch.so`，需放置到 `jniLibs/` 目录：

```
app/src/main/jniLibs/
├── arm64-v8a/libjnidispatch.so
├── armeabi-v7a/libjnidispatch.so
├── x86/libjnidispatch.so
└── x86_64/libjnidispatch.so
```

### 2.4 模型原生指针验证

Vosk 的 `Model` 继承 JNA `PointerType`，加载后需要验证原生指针是否有效：

```kotlin
private fun isModelNativePointerValid(): Boolean {
    // 通过反射获取 PointerType 的 pointer 字段
    val ptrTypeClass = model::class.java.superclass
    val pointerField = ptrTypeClass.getDeclaredField("pointer")
    pointerField.isAccessible = true
    val ptr = pointerField.get(model)
    return ptr != null  // null → 模型文件损坏
}
```

---

## 3. 自动播放功能

### 3.1 逻辑流程

```
用户开启"自动朗读"开关 (autoPlay = true，默认开启)
    │
    ├── 用户发送消息
    │   └── LaunchedEffect(messages.lastOrNull{it.isUser}?.id)
    │       └── ttsManager.stop()   ← 停止当前朗读
    │
    ├── 开始语音输入
    │   └── onVoiceStart()
    │       ├── ttsManager.stop()   ← 停止当前朗读
    │       └── 开始录音
    │
    └── AI 回复到达 (messages.size 变化)
        └── LaunchedEffect(messages.size, autoPlay)
            └── 获取最后一条用户消息之后的 AI 回复
                ├── 第一条: ttsManager.speak()       ← QUEUE_FLUSH
                └── 后续:   ttsManager.speakQueued()  ← QUEUE_ADD
```

### 3.2 关键代码

```kotlin
// 自动播放开关（默认 true）
var autoPlay by remember { mutableStateOf(true) }
val lastAutoPlayedMessageId = remember { mutableStateOf<Long?>(null) }

// 监听新用户消息 → 停止 TTS
LaunchedEffect(messages.lastOrNull { it.isUser }?.id) {
    if (autoPlay) {
        ttsManager.stop()
        lastAutoPlayedMessageId.value = null
    }
}

// 监听新 AI 回复 → 排队朗读
LaunchedEffect(messages.size, autoPlay) {
    if (!autoPlay || messages.isEmpty()) return@LaunchedEffect
    val lastUserIdx = messages.indexOfLast { it.isUser }
    val unspoken = messages.drop(lastUserIdx + 1)
        .filter { !it.isUser && it.id != lastAutoPlayedMessageId.value }
    unspoken.forEachIndexed { i, msg ->
        if (i == 0) ttsManager.speak(msg.text)
        else ttsManager.speakQueued(msg.text)
        lastAutoPlayedMessageId.value = msg.id
    }
}
```

### 3.3 UI 开关

右上角极简扬声器图标按钮，使用 `combinedClickable`：

```kotlin
Box(
    modifier = Modifier
        .size(36.dp)
        .combinedClickable(
            onClick = {
                autoPlay = !autoPlay
                if (!autoPlay) {
                    ttsManager.stop()
                    lastAutoPlayedMessageId.value = null
                }
            },
            onLongClick = {
                Toast.makeText(context, "更换语音引擎：系统设置 → 辅助功能 → 文字转语音 → 首选引擎", ...).show()
            }
        ),
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
        tint = if (autoPlay) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.size(18.dp)
    )
}
```

- **点击**：切换自动朗读开关
- **长按**：Toast 提示 TTS 引擎设置路径

---

## 4. 录音状态浮层（微信风格）

### 4.1 触发条件

`isVoiceRecording = true` 时显示全屏半透明浮层。覆盖在页面之上，不影响下方布局。

### 4.2 UI 布局

```
┌─────────────────────────────┐
│     半透明遮罩 (scrim)       │
│                             │
│         ┌──────────┐        │
│         │  ○  ○  ○ │        │  声波动画（4层扩散圆环）
│         │   🎤     │        │  麦克风图标
│         └──────────┘        │  圆角 20dp 深色方块
│                             │
│  ┌─────────────────────┐    │
│   手指上滑，取消发送      │    │  圆角提示文字块
│  └─────────────────────┘    │
│                             │
└─────────────────────────────┘

上滑取消状态：
┌─────────────────────────────┐
│                             │
│         ┌──────────┐        │
│         │    ↑      │        │  红色方块 + 上箭头
│         │  取消发送  │        │
│         └──────────┘        │
│                             │
│  ┌─────────────────────┐    │
│   松开手指，取消发送      │    │  红色提示文字块
│  └─────────────────────┘    │
└─────────────────────────────┘
```

### 4.3 动画实现

```kotlin
// 声波动画：4层扩散圆环，逐层脉冲
val waveAnim by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    label = "wave"
)

for (i in 0..3) {
    val alpha = ((waveAnim * 4 - i.toFloat()).coerceIn(0f, 1f)) * 0.5f
    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .size((48 + i * 20).dp)
                .scale(1f + waveAnim * 0.15f)
                .background(Color.White.copy(alpha = alpha * 0.3f), RoundedCornerShape(50))
        )
    }
}
// 中心麦克风图标
Icon(Icons.Default.Mic, tint = Color.White, modifier = Modifier.size(40.dp))
```

### 4.4 上滑取消手势检测

使用 `awaitEachGesture` + `awaitFirstDown`，监听手指位置变化：

```kotlin
Box(
    modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(50))
        .background(micBg)
        .pointerInput(Unit) {
            val cancelThresholdPx = with(density) { 80.dp.toPx() }
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isVoicePressed = true
                onVoiceStart()
                var canceled = false
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val slideUpPx = -change.position.y  // 手指上滑距离
                        if (slideUpPx > cancelThresholdPx) {
                            if (!canceled) {
                                canceled = true
                                onSlidingToCancelChange(true)  // → 弹窗变红
                            }
                        } else {
                            if (canceled) {
                                canceled = false
                                onSlidingToCancelChange(false) // → 恢复录音状态
                            }
                        }
                    } while (true)
                } catch (_: CancellationException) {
                } finally {
                    isVoicePressed = false
                    onVoiceEnd(canceled)  // 松手，传回是否取消
                }
            }
        },
    contentAlignment = Alignment.Center
) {
    Icon(Icons.Default.Mic, tint = micTint, modifier = Modifier.size(22.dp))
}
```

**关键点：**
- 使用 `change.position.y`（相对于按钮坐标），上滑为负值
- `-change.position.y > 80.dp` 触发取消
- 不依赖 `buttonGlobalY`，无需 `onGloballyPositioned`
- `requireUnconsumed = false` 确保即使输入框消费了触碰事件也能收到

---

## 5. 发送逻辑与异步处理

### 5.1 松手自动发送流程

```
用户按下麦克风按钮
    │
    ▼
awaitFirstDown() → isVoicePressed = true → onVoiceStart() → startRecording()
    │
    │  录音过程中：
    │  onPartialResult → inputText = text （输入框实时更新）
    │
用户松开手指
    │
    ▼
onVoiceEnd(isCancel)
    │
    ├── 是取消状态 → 不发送
    │
    └── 非取消状态 → pendingVoiceSend = true
                          │
                          ▼
                     voskHelper.stopRecording()
                          │
                          ▼
                     ┌── 同步：onResult 在 stop() 内部触发
                     │   └── pendingVoiceSend == true → 发送
                     │
                     └── 异步：onResult 稍后在主线程触发
                         └── pendingVoiceSend == true → 发送
```

### 5.2 pendingVoiceSend 标志

```kotlin
// 状态定义
var pendingVoiceSend by remember { mutableStateOf(false) }

// onVoiceEnd：松手时标记等待发送
onVoiceEnd = { isCancel ->
    if (isVoiceRecording) {
        if (!isCancel) {
            pendingVoiceSend = true   // 先标记，再停止录音
        }
        voskHelper.stopRecording()   // 这会触发 onResult
        isVoiceRecording = false
    }
}

// onResult：收到最终识别结果后发送
voskHelper.onResult = { text ->
    if (text.isNotBlank()) {
        inputText = text             // 更新输入框
        if (pendingVoiceSend) {      // 标记存在，说明是松手后的结果
            pendingVoiceSend = false
            viewModel.sendMessage(text)
            inputText = ""
        }
    }
}
```

**为什么必须先标记再停止录音：**

| 场景 | onResult 时机 | 处理 |
|---|---|---|
| `stop()` 同步触发 onResult | 在 `pendingVoiceSend = true` 之后 | ✅ 正确发送 |
| `stop()` 异步触发 onResult | `pendingVoiceSend` 仍为 `true` | ✅ 正确发送 |

如果反过来（先 stopRecording 再设置标志），同步场景会漏发。

### 5.3 实时识别文字更新

```kotlin
// 部分识别结果 → 实时更新输入框
voskHelper.onPartialResult = { text ->
    if (text.isNotBlank()) {
        inputText = text  // 输入框实时显示识别文字
    }
}

// 最终结果 → 设置到输入框 + 自动发送
voskHelper.onResult = { text ->
    if (text.isNotBlank()) {
        inputText = text
        if (pendingVoiceSend) {
            pendingVoiceSend = false
            viewModel.sendMessage(text)
            inputText = ""
        }
    }
}
```

---

## 6. 依赖与配置

### 6.1 build.gradle.kts

```kotlin
// TTS：无额外依赖，使用 Android SDK 内置 API
// 仅需 AndroidManifest.xml 中加 <queries>

// Vosk
implementation(libs.vosk.android) {
    exclude(group = "net.java.dev.jna", module = "jna")
}
implementation("net.java.dev.jna:jna:5.14.0@aar")
```

### 6.2 version catalog (libs.versions.toml)

```toml
[versions]
vosk-android = "0.3.47+"

[libraries]
vosk-android = { module = "com.alphacephei:vosk-android", version.ref = "vosk-android" }
```

### 6.3 AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Android 11+ 包可见性 -->
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

### 6.4 jniLibs 目录结构

```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── libjnidispatch.so
├── armeabi-v7a/
│   └── libjnidispatch.so
├── x86/
│   └── libjnidispatch.so
└── x86_64/
    └── libjnidispatch.so
```

从 JNA AAR 中提取：
```
提取 .aar → 解压 → jni/ 目录下的各架构 .so 文件
```

---

## 7. 常见问题与调试

### 7.1 日志 Tag

| 模块 | Tag | 命令 |
|---|---|---|
| TTS 朗读 | `HsiaoWear-TTS` | `adb logcat -s HsiaoWear-TTS` |
| 语音识别 | `HsiaoWear-Voice` | `adb logcat -s HsiaoWear-Voice` |
| Vosk 内部 | `VoskAPI` | `adb logcat -s VoskAPI` |
| 自动播放 | `HsiaoWear-AutoPlay` | `adb logcat -s HsiaoWear-AutoPlay` |
| UI 布局 | `HsiaoWear-TodayUI` | `adb logcat -s HsiaoWear-TodayUI` |

### 7.2 调试 TTS 引擎列表

```kotlin
adb logcat -s HsiaoWear-TTS | findstr "系统可见"
// 输出示例:
// 系统可见的 TTS 引擎:
//   [1] com.google.android.tts/...GoogleTtsService (enabled=true)
//   [2] com.oplus.ttsaccessibilityengine/...TtsService (enabled=true)
```

### 7.3 已知问题

| 问题 | 原因 | 解决 |
|---|---|---|
| TTS status=-1 | 系统 TTS 框架缺失（OPPO 等 ROM） | 安装 Google TTS 或使用 fallback 引擎 |
| TTS 无声音 | 引擎已初始化但语音数据未下载 | 去设置 → 文字转语音 → 下载语音数据 |
| `libjnidispatch.so` not found | JNA 传递依赖是 JAR 非 AAR | 使用 `@aar` 显式依赖 + jniLibs |
| Vosk SIGSEGV | 模型文件损坏或不完整 | 自动检测并重新下载 |
| 语音按钮无法点击 | OutlinedTextField 与 mic 按钮同级时抢触碰 | 将 mic 按钮独立到容器外 |
| 识别结果不准确 | 中文小模型精度有限 | 更换 vosk-model-cn-0.22 大全模型 |

### 7.4 ADB 命令

```powershell
# 查看默认 TTS 引擎
adb shell settings get secure tts_default_synth

# 设置默认 TTS 引擎
adb shell settings put secure tts_default_synth com.google.android.tts

# 打开 TTS 设置页面
adb shell am start -a android.settings.TTS_SETTINGS

# 查看 Vosk 模型目录
adb shell ls -la /data/data/com.example.hsiaowear/files/vosk-model-small-cn-0.22/

# 清除 App 数据（重置所有状态）
adb shell pm clear com.example.hsiaowear

# 查看 Vosk 模型下载进度
adb logcat -s VoskSpeechHelper
```

### 7.5 代码文件索引

| 文件 | 作用 |
|---|---|
| [`util/TtsManager.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/TtsManager.kt) | TTS 核心管理类 + Composable remember 辅助函数 |
| [`util/VoskSpeechHelper.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/util/VoskSpeechHelper.kt) | Vosk 语音识别管理类（模型下载/加载/录音/回调） |
| [`ui/screen/LobsterScreen.kt`](file:///z:/2projects/HsiaoWear/app/src/main/java/com/example/hsiaowear/ui/screen/LobsterScreen.kt) | AI 对话页面（集成 TTS + Vosk + 录音弹窗 + 自动播放） |
| [`AndroidManifest.xml`](file:///z:/2projects/HsiaoWear/app/src/main/AndroidManifest.xml) | 权限与查询声明 |
| [`app/build.gradle.kts`](file:///z:/2projects/HsiaoWear/app/build.gradle.kts) | JNA 依赖配置 |
