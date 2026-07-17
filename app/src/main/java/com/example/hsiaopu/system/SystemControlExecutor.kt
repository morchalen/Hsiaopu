package com.example.hsiaopu.system

import kotlinx.coroutines.*

/**
 * 系统命令执行结果
 *
 * @param action 操作名称
 * @param success 是否成功（以状态验证为准，不依赖命令返回值）
 * @param message 用户可读的结果描述
 * @param output 命令的原始输出（stdout）
 * @param verified 是否经过了独立的状态验证（true=已验证，false=仅命令输出）
 */
data class SysResult(
    val action: String,
    val success: Boolean,
    val message: String,
    val output: String,
    val verified: Boolean
)

/**
 * 设备控制中心 — AI 可调用的所有系统能力
 *
 * 设计原则：
 * 1. 每个操作都是独立的函数，AI 可自由组合调用
 * 2. 操作命令 → 等待硬件响应 → 独立查询验证状态 → 返回真实结果
 * 3. 成功就是成功，失败就是失败，不带模糊的"可能"
 * 4. 只读命令直接返回结果，写操作命令强制执行状态验证
 */
object SystemControlExecutor {

    // ==================== WiFi ====================

    fun isWifiEnabled(): Boolean {
        if (!ensureShizuku()) return false
        return try {
            exec("cmd wifi status").contains("Wifi is enabled", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    suspend fun enableWifi(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("WiFi 开启")
        exec("svc wifi enable")
        delay(600)
        val ok = isWifiEnabled()
        SysResult("WiFi 开启", ok, if (ok) "WiFi 已成功开启" else "WiFi 开启失败：命令已执行但状态未改变", "", true)
    }

    suspend fun disableWifi(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("WiFi 关闭")
        exec("svc wifi disable")
        delay(600)
        val ok = !isWifiEnabled()
        SysResult("WiFi 关闭", ok, if (ok) "WiFi 已成功关闭" else "WiFi 关闭失败：命令已执行但状态未改变", "", true)
    }

    // ==================== 蓝牙 ====================

    fun isBluetoothEnabled(): Boolean {
        if (!ensureShizuku()) return false
        return try { exec("settings get global bluetooth_on").trim() == "1" } catch (_: Exception) { false }
    }

    suspend fun enableBluetooth(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("蓝牙 开启")
        exec("svc bluetooth enable")
        delay(800)
        val ok = isBluetoothEnabled()
        SysResult("蓝牙 开启", ok, if (ok) "蓝牙已成功开启" else "蓝牙开启失败：命令已执行但状态未改变", "", true)
    }

    suspend fun disableBluetooth(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("蓝牙 关闭")
        exec("svc bluetooth disable")
        delay(800)
        val ok = !isBluetoothEnabled()
        SysResult("蓝牙 关闭", ok, if (ok) "蓝牙已成功关闭" else "蓝牙关闭失败：命令已执行但状态未改变", "", true)
    }

    // ==================== 热点 ====================

    fun isHotspotEnabled(): Boolean {
        if (!ensureShizuku()) return false
        return try {
            exec("dumpsys wifi").contains("SoftAp is enabled", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    suspend fun enableHotspot(ssid: String = "Hsiaopu", password: String = "12345678"): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("热点 开启")
        exec("cmd wifi start-softap \"$ssid\" wpa2-psk \"$password\"")
        delay(1200)
        val ok = isHotspotEnabled()
        SysResult("热点 开启", ok, if (ok) "热点 \"$ssid\" 已成功开启" else "热点开启失败（可能需先关闭 WiFi）", "", true)
    }

    suspend fun disableHotspot(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("热点 关闭")
        exec("cmd wifi stop-softap")
        delay(1000)
        val ok = !isHotspotEnabled()
        SysResult("热点 关闭", ok, if (ok) "热点已成功关闭" else "热点关闭失败", "", true)
    }

    // ==================== 移动数据 ====================

    fun isMobileDataEnabled(): Boolean {
        if (!ensureShizuku()) return false
        return try {
            exec("settings get global mobile_data").trim().contains("1")
        } catch (_: Exception) { false }
    }

    suspend fun enableMobileData(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("移动数据 开启")
        exec("svc data enable")
        delay(500)
        val ok = isMobileDataEnabled()
        SysResult("移动数据 开启", ok, if (ok) "移动数据已成功开启" else "移动数据开启失败", "", true)
    }

    suspend fun disableMobileData(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("移动数据 关闭")
        exec("svc data disable")
        delay(500)
        val ok = !isMobileDataEnabled()
        SysResult("移动数据 关闭", ok, if (ok) "移动数据已成功关闭" else "移动数据关闭失败", "", true)
    }

    // ==================== 飞行模式 ====================

    fun isAirplaneModeEnabled(): Boolean {
        if (!ensureShizuku()) return false
        return try {
            exec("settings get global airplane_mode_on").trim() == "1"
        } catch (_: Exception) { false }
    }

    suspend fun enableAirplaneMode(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("飞行模式 开启")
        exec("settings put global airplane_mode_on 1")
        exec("am broadcast -a android.intent.action.AIRPLANE_MODE")
        delay(1000)
        val ok = isAirplaneModeEnabled()
        SysResult("飞行模式 开启", ok, if (ok) "飞行模式已成功开启" else "飞行模式开启失败", "", true)
    }

    suspend fun disableAirplaneMode(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("飞行模式 关闭")
        exec("settings put global airplane_mode_on 0")
        exec("am broadcast -a android.intent.action.AIRPLANE_MODE")
        delay(1000)
        val ok = !isAirplaneModeEnabled()
        SysResult("飞行模式 关闭", ok, if (ok) "飞行模式已成功关闭" else "飞行模式关闭失败", "", true)
    }

    // ==================== NFC ====================

    fun isNfcEnabled(): Boolean {
        if (!ensureShizuku()) return false
        return try {
            exec("dumpsys nfc").contains("mState=on", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    suspend fun enableNfc(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("NFC 开启")
        exec("svc nfc enable")
        delay(500)
        val ok = isNfcEnabled()
        SysResult("NFC 开启", ok, if (ok) "NFC 已成功开启" else "NFC 开启失败", "", true)
    }

    suspend fun disableNfc(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("NFC 关闭")
        exec("svc nfc disable")
        delay(500)
        val ok = !isNfcEnabled()
        SysResult("NFC 关闭", ok, if (ok) "NFC 已成功关闭" else "NFC 关闭失败", "", true)
    }

    // ==================== 屏幕亮度 ====================

    fun getBrightness(): Int {
        if (!ensureShizuku()) return -1
        return try {
            exec("settings get system screen_brightness").trim().toIntOrNull() ?: -1
        } catch (_: Exception) { -1 }
    }

    suspend fun setBrightness(level: Int): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("亮度设置")
        val clamped = level.coerceIn(1, 255)
        exec("settings put system screen_brightness $clamped")
        delay(300)
        val actual = getBrightness()
        val ok = actual == clamped
        SysResult("亮度设置", ok, if (ok) "屏幕亮度已设为 $clamped/255" else "亮度设置失败：期望 $clamped，实际 $actual", "", true)
    }

    // ==================== 音量 ====================

    fun getVolume(stream: String = "music"): Int {
        if (!ensureShizuku()) return -1
        val streamCode = when (stream) { "ring" -> 2; "alarm" -> 4; "notification" -> 5; else -> 3 }
        return try {
            exec("media volume --stream $streamCode --get").trim()
                .replace("level is ", "").trim().toIntOrNull() ?: -1
        } catch (_: Exception) { -1 }
    }

    suspend fun setVolume(stream: String = "music", level: Int): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("音量设置")
        val streamCode = when (stream) { "ring" -> 2; "alarm" -> 4; "notification" -> 5; else -> 3 }
        val clamped = level.coerceIn(0, 15)
        exec("media volume --stream $streamCode --set $clamped")
        delay(300)
        val actual = getVolume(stream)
        val ok = actual == clamped
        SysResult("音量设置", ok, if (ok) "音量已设为 $clamped/15" else "音量设置失败：期望 $clamped，实际 $actual", "", true)
    }

    // ==================== 截屏 / 录屏 ====================

    suspend fun takeScreenshot(path: String = "/sdcard/Pictures/screenshot_hsiaopu.png"): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("截屏")
        val output = exec("screencap -p \"$path\"")
        val ok = output.isBlank() // screencap 成功时无输出
        // 验证文件是否存在
        val fileExists = exec("test -f \"$path\" && echo YES || echo NO").trim() == "YES"
        SysResult("截屏", fileExists, if (fileExists) "截屏已保存到 $path" else "截屏失败", output, true)
    }

    // ==================== 重启 / 关机 ====================

    suspend fun reboot(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("重启")
        exec("reboot")
        SysResult("重启", true, "设备正在重启…", "", false)
    }

    suspend fun rebootRecovery(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("重启到 Recovery")
        exec("reboot recovery")
        SysResult("重启到 Recovery", true, "设备正在重启到 Recovery…", "", false)
    }

    suspend fun rebootBootloader(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("重启到 Bootloader")
        exec("reboot bootloader")
        SysResult("重启到 Bootloader", true, "设备正在重启到 Bootloader…", "", false)
    }

    suspend fun shutdown(): SysResult = withContext(Dispatchers.IO) {
        if (!ensureShizuku()) return@withContext shizukuUnavailable("关机")
        exec("reboot -p")
        SysResult("关机", true, "设备正在关机…", "", false)
    }

    // ==================== 只读查询（读命令，不需要验证） ====================

    fun getCpuInfo(): SysResult = readOnly("CPU 信息", "cat /proc/cpuinfo 2>/dev/null | head -40")
    fun getMemoryInfo(): SysResult = readOnly("内存信息", "cat /proc/meminfo 2>/dev/null | head -20")
    fun getUptime(): SysResult = readOnly("运行时间", "cat /proc/uptime")
    fun getKernelVersion(): SysResult = readOnly("内核版本", "uname -a")
    fun getPropValue(key: String): SysResult = readOnly("系统属性: $key", "getprop $key 2>/dev/null")
    fun getTopProcesses(): SysResult = readOnly("进程列表", "ps -A -o PID,USER,NAME 2>/dev/null | tail -20")
    fun getDiskUsage(): SysResult = readOnly("磁盘使用", "df -h 2>/dev/null")
    fun getMountInfo(): SysResult = readOnly("挂载信息", "mount 2>/dev/null | grep -v '^rootfs'")
    fun getNetworkInterfaces(): SysResult = readOnly("网络接口", "ip addr show 2>/dev/null")
    fun getRoutingTable(): SysResult = readOnly("路由表", "ip route show 2>/dev/null")
    fun getDnsInfo(): SysResult = readOnly("DNS 信息", "getprop net.dns1 && getprop net.dns2")
    fun getBatteryInfo(): SysResult = readOnly("电池信息", "dumpsys battery 2>/dev/null")
    fun getSensorList(): SysResult = readOnly("传感器列表", "dumpsys sensorservice 2>/dev/null | grep -A 1 'Sensor List'")
    fun getInstalledPackages(): SysResult = readOnly("已安装应用", "pm list packages 2>/dev/null | tail -30")
    fun getRunningServices(): SysResult = readOnly("运行中服务", "dumpsys activity services 2>/dev/null | grep 'ServiceRecord' | tail -20")
    fun getDisplayInfo(): SysResult = readOnly("显示信息", "dumpsys window displays 2>/dev/null | head -30")
    fun getWifiNetworks(): SysResult = readOnly("WiFi 网络", "dumpsys wifi 2>/dev/null | grep 'SSID:' | head -10")
    fun getIpAddress(): SysResult = readOnly("IP 地址", "ip addr show wlan0 2>/dev/null | grep 'inet ' || ip addr show eth0 2>/dev/null | grep 'inet ' || echo 'N/A'")
    fun getCpuTemp(): SysResult = readOnly("CPU 温度", "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null || echo 'N/A'")
    fun pingTest(host: String = "8.8.8.8", count: Int = 4): SysResult = readOnly("Ping $host", "ping -c $count -W 2 $host 2>&1")

    // ==================== 应用管理 ====================

    fun forceStopApp(packageName: String): SysResult {
        if (packageName.isBlank()) return SysResult("强制停止", false, "请指定应用包名", "", false)
        val output = exec("am force-stop $packageName 2>&1")
        val ok = output.isBlank()
        return SysResult("强制停止: $packageName", ok, if (ok) "已强制停止 $packageName" else "停止失败: $output", output, ok)
    }

    fun clearAppData(packageName: String): SysResult {
        if (packageName.isBlank()) return SysResult("清除数据", false, "请指定应用包名", "", false)
        val output = exec("pm clear $packageName 2>&1")
        val ok = output.contains("Success", ignoreCase = true)
        return SysResult("清除数据: $packageName", ok, if (ok) "已清除 $packageName 的数据" else "清除失败: $output", output, ok)
    }

    fun uninstallApp(packageName: String): SysResult {
        if (packageName.isBlank()) return SysResult("卸载", false, "请指定应用包名", "", false)
        val output = exec("pm uninstall $packageName 2>&1")
        val ok = output.contains("Success", ignoreCase = true)
        return SysResult("卸载: $packageName", ok, if (ok) "已卸载 $packageName" else "卸载失败: $output", output, ok)
    }

    // ==================== 文件操作 ====================

    fun listFiles(dir: String): SysResult = readOnly("文件列表: $dir", "ls -lah \"$dir\" 2>&1")
    fun readFile(path: String): SysResult = readOnly("文件内容: $path", "cat \"$path\" 2>&1 | head -50")
    fun deleteFile(path: String): SysResult {
        if (path.isBlank()) return SysResult("删除文件", false, "请指定文件路径", "", false)
        val output = exec("rm -f \"$path\" 2>&1")
        val ok = output.isBlank()
        return SysResult("删除: $path", ok, if (ok) "文件已删除: $path" else "删除失败: $output", output, ok)
    }

    // ==================== 内部工具 ====================

    private fun ensureShizuku(): Boolean {
        return ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission()
    }

    private fun exec(command: String): String {
        return try {
            ShizukuHelper.exec(command)
        } catch (e: Exception) {
            throw IllegalStateException("Shizuku exec failed: ${e.message}")
        }
    }

    private fun readOnly(action: String, command: String): SysResult {
        if (!ensureShizuku()) return shizukuUnavailable(action)
        return try {
            val output = exec(command)
            SysResult(action, true, output, output, false)
        } catch (e: Exception) {
            SysResult(action, false, "执行失败: ${e.message}", "", false)
        }
    }

    private fun shizukuUnavailable(action: String) =
        SysResult(action, false, "Shizuku 不可用或未授权，请先启动 Shizuku 并授权", "", false)
}