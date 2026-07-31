# HsiaoWear Bug 修复日志

## 2026-07-31

### [修复] AI 回复不显示真实错误信息（API 错误被吞）

- **问题**：当 API Key 余额不足、Key 无效等原因请求失败时，App 只显示"网络错误"，用户无法知道真正原因。
- **根因**：`ChatClient.sendMessage()` 直接调用 Retrofit 的 `execute()`，遇到 HTTP 错误（如 402、401）时 Retrofit 抛出 `HttpException`，但错误信息在 `ChatViewModel` 的 `catch` 里被 `e.message ?: "网络错误"` 吃掉。
- **修复方案**：
  - 新增 `ChatClient.sendMessageSafe()` 方法，捕获 `HttpException` 并解析服务端返回的 JSON 错误体（`{"error":{"message":"..."}}`），把真实错误信息抛出来
  - 新增 `parseErrorMessage()` 工具方法解析 JSON 错误
  - `ChatViewModel.doSendWithTools()` 中两处 `chatClient.sendMessage()` 改为 `chatClient.sendMessageSafe()`
  - 现在 API 余额不足时，App 会显示 "Insufficient Balance" 而不是"网络错误"
- **涉及文件**：`ChatClient.kt`、`ChatViewModel.kt`

---

### [修复] Shell 页面切换闪退（SQLiteBlobTooBigException: Row too big）

- **问题**：切换到 Shell 页面时，App 立即闪退。
- **根因**：Shell 历史命令输出（stdout/stderr）中存在超大行（超过 2MB），而 Android SQLite 的 CursorWindow 大小固定为 2MB，`ShellHistoryDao.getAllHistory()` 在加载这些行时抛出 `SQLiteBlobTooBigException`。
- **修复方案**：
  1. **写入截断**：在 `ShellHistoryRepository.insertHistory()` 中，command 最多存 8KB，stdout/stderr 分别最多存 128KB（`take()` 截断），确保单行远小于 2MB。
  2. **查询限流**：`ShellHistoryDao.getAllHistory()` 和 `getAllHistorySync()` 添加 `LIMIT 500`，避免一次加载海量历史。
  3. **清理旧数据**：对用户设备执行 `adb shell pm clear com.example.hsiaopu`，清除已经损坏的旧数据库（否则即使代码修复，旧的超大行仍然会在下次查询时报错）。
- **涉及文件**：`ShellHistoryRepository.kt`、`Daos.kt`

---

### [优化] Vosk 模型改为 APK 内打包，移除网络下载

- **问题**：每次首次安装都需要网络下载约 40MB 的语音模型，下载慢且可能失败。
- **根因**：无——用户要求直接打包以消除网络依赖。
- **优化方案**（第 2 版）：
  - 下载 `vosk-model-small-cn-0.22.zip` 并放入 `app/src/main/assets/` 目录
  - **完全移除**网络下载路径（`downloadModel()`、`MODEL_URL`、`HttpURLConnection`、`URL` 等全部删除）
  - `initialize()` 改为仅为：已解压 → `loadModel()`，未解压 → `extractModelFromAssets()` → `loadModel()`
  - 解压失败直接报 `ERROR`，不再回退网络下载（纯离线）
  - 添加 `isInitializing` 标志，防止重复初始化
  - 删除 `downloadProgress` 等不再需要的字段
  - 删除 `State.MODEL_DOWNLOADING` 枚举值
- **涉及文件**：`VoskSpeechHelper.kt`、`HomeScreen.kt`

---

### [修复] 语音识别 Model() 构造崩溃（UnsatisfiedLinkError）

- **问题**：点击麦克风后一直显示"正在准备语音识别..."，最终超时无响应。
- **根因**：项目缺少 `app/src/main/jniLibs/` 目录下的 JNA 原生库 `libjnidispatch.so`。Vosk 的 `Model()` 构造依赖于 JNA，没有原生 `.so` 文件时抛出 `UnsatisfiedLinkError`（继承 `Error` 而非 `Exception`），未被 `catch (e: Exception)` 捕获，导致协程静默崩溃，状态永远停在初始化中。
- **修复方案**：从参考项目 `Z:\2projects\HsiaoWear` 复制完整的 `jniLibs/` 目录（含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 四种架构的 `libjnidispatch.so`）。
- **涉及文件**：新增 `app/src/main/jniLibs/` 目录

## 2026-07-30

### [修复] 页面初始化时自动播放旧 AI 回复

- **问题**：进入对话页面后，自动朗读功能会将之前对话中已有的 AI 回复全部朗读一遍。
- **根因**：`lastAutoPlayedMessageId` 在组件初始化时为 `null`，导致所有已有的 AI 回复都被视为"未播放过"，触发自动播放。
- **修复方案**：记录页面首次加载时的消息数量 `initialMessageCount`，只对加载后新增的 AI 回复进行自动播放。
- **涉及文件**：`HomeScreen.kt`

---

### [修复] TTS 初始化失败（语音朗读无声音）

- **问题**：语音朗读功能完全无声音，日志显示"所有已知引擎均无法初始化"。
- **根因**：Android 11+ 上缺少 `<queries>` 声明，系统不允许应用查询 TTS 引擎服务，导致 `TextToSpeech` 初始化失败。
- **修复方案**：在 `AndroidManifest.xml` 中添加 `TTS_SERVICE` 的查询声明。
- **涉及文件**：`AndroidManifest.xml`

---

### [修复] 语音录制按钮无法点击

- **问题**：点击麦克风按钮无任何反应，无法启动录音。
- **根因**：手势检测错误地使用了 `detectDragGesturesAfterLongPress`（需要长按才触发），而参考项目使用的是 `awaitEachGesture` + `awaitFirstDown`（触摸即触发）。
- **修复方案**：恢复为参考项目的原始手势检测方案。
- **涉及文件**：`HomeScreen.kt`

---

### [修复] 语音录制按钮无响应

- **问题**：按住录音按钮没有任何反应，录音弹窗不显示。
- **根因**：`onVoiceStart` 中只有 `if (voskHelper.isModelReady)` 成立时才启动录音。首次进入页面时 Vosk 模型正在后台下载，此时 `isModelReady = false`，代码静默跳过，无任何用户反馈。
- **修复方案**：
  - 将 `permissionLauncher` 声明提前到 `DisposableEffect` 之前，解决变量引用顺序问题
  - `onVoiceStart` 根据模型状态（未初始化/下载中/就绪/错误）分别处理：
    - 就绪 → 直接录音
    - 下载中 → Toast 提示并标记 `voiceRequestPending`
    - 错误 → 重新初始化并标记 `voiceRequestPending`
    - 未初始化 → Toast 提示并触发初始化
  - 模型就绪后自动恢复录音（`voiceRequestPending` 机制）
  - 权限授予后自动继续录音流程
- **涉及文件**：`HomeScreen.kt`

---

### [修复] 语音识别 Model() 构造崩溃（UnsatisfiedLinkError）

- **问题**：点击麦克风后一直显示"正在准备语音识别..."，最终超时无响应。
- **根因**：项目缺少 `app/src/main/jniLibs/` 目录下的 JNA 原生库 `libjnidispatch.so`。Vosk 的 `Model()` 构造依赖于 JNA，没有原生 `.so` 文件时抛出 `UnsatisfiedLinkError`（继承 `Error` 而非 `Exception`），未被 `catch (e: Exception)` 捕获，导致协程静默崩溃，状态永远停在初始化中。
- **修复方案**：从参考项目 `Z:\2projects\HsiaoWear` 复制完整的 `jniLibs/` 目录（含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 四种架构的 `libjnidispatch.so`）。
- **涉及文件**：新增 `app/src/main/jniLibs/` 目录

---

### [优化] Vosk 模型改为 APK 内打包，移除网络下载

- **问题**：每次首次安装都需要网络下载约 40MB 的语音模型，下载慢且可能失败。
- **根因**：无——用户要求直接打包以消除网络依赖。
- **优化方案**（第 2 版）：
  - 下载 `vosk-model-small-cn-0.22.zip` 并放入 `app/src/main/assets/` 目录
  - **完全移除**网络下载路径（`downloadModel()`、`MODEL_URL`、`HttpURLConnection`、`URL` 等全部删除）
  - `initialize()` 改为仅为：已解压 → `loadModel()`，未解压 → `extractModelFromAssets()` → `loadModel()`
  - 解压失败直接报 `ERROR`，不再回退网络下载（纯离线）
  - 添加 `isInitializing` 标志，防止重复初始化
  - 删除 `downloadProgress` 等不再需要的字段
  - 删除 `State.MODEL_DOWNLOADING` 枚举值
- **涉及文件**：`VoskSpeechHelper.kt`、`HomeScreen.kt`
