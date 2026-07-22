package com.example.hsiaopu.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext


data class ShellResult(
    val command: String,        // 执行的 Shell 命令，字符串格式，例如 "ls -la"
    val stdout: String,         // 标准输出流的内容（命令正常打印的信息）。stdout = 标准输出（Standard Output）
    val stderr: String,         // 标准错误流的内容（命令报错信息）
    val exitCode: Int = -1,     // 退出码：0=成功，非0=失败，默认 -1（未执行）
    val timestamp: Long = System.currentTimeMillis()  // 执行时间戳，单位毫秒
) {
    //判断命令是否执行成功；当 exitCode == 0 时表示执行成功，否则表示执行失败
    val isSuccess: Boolean get() = exitCode == 0
}

data class PredefinedCommand(//预定义命令
    val label: String,//命令标签
    val command: String,//命令字符串
    val description: String,//命令描述
    val category: String//命令分类
)
//单例类object
//命令执行器
object ShellExecutor {
    //预定义命令列表
    val predefinedCommands: List<PredefinedCommand> = listOf(
        // System Info
        PredefinedCommand("CPU 信息", "cat /proc/cpuinfo", "查看 CPU 详细信息", "系统信息"),
        PredefinedCommand("内存信息", "cat /proc/meminfo", "查看内存使用情况", "系统信息"),
        PredefinedCommand("运行时间", "cat /proc/uptime", "查看系统运行时长", "系统信息"),
        PredefinedCommand("内核版本", "uname -a", "查看内核版本信息", "系统信息"),
        PredefinedCommand("系统属性", "getprop", "查看系统属性列表", "系统信息"),

        // Process
        PredefinedCommand("CPU 占用", "top -b -n 1 -o %CPU | head -17", "CPU 占用前 10 进程", "进程管理"),
        PredefinedCommand("进程列表", "ps -A -o PID,USER,NAME,%CPU,%MEM", "查看所有进程", "进程管理"),
        PredefinedCommand("OOM 信息", "dumpsys activity oom", "OOM 调整信息", "进程管理"),

        // Network
        PredefinedCommand("DNS 服务器", "getprop net.dns1 && getprop net.dns2", "查看 DNS 服务器地址", "网络"),
        PredefinedCommand("路由表", "ip route show", "查看路由表", "网络"),
        PredefinedCommand("ARP 缓存", "cat /proc/net/arp", "查看 ARP 缓存表", "网络"),
        PredefinedCommand("监听端口", "ss -tlnp", "查看正在监听的端口", "网络"),

        // Storage
        PredefinedCommand("挂载点", "mount", "查看所有挂载点", "存储"),
        PredefinedCommand("磁盘使用", "df -h", "查看磁盘空间使用情况", "存储"),
        PredefinedCommand("分区列表", "ls -la /dev/block/by-name/", "查看存储分区", "存储"),

        // Package
        PredefinedCommand("已安装应用", "pm list packages", "查看已安装应用列表", "应用"),
        PredefinedCommand("包服务", "dumpsys package", "查看包服务信息", "应用"),
        PredefinedCommand("电池状态", "dumpsys battery", "查看电池状态", "应用"),

        // Sensors / Hardware
        PredefinedCommand("传感器", "dumpsys sensorservice", "查看传感器列表", "硬件"),
        PredefinedCommand("输入设备", "getevent -p", "查看输入设备", "硬件"),
        PredefinedCommand("显示信息", "dumpsys display", "查看显示信息", "硬件"),
        PredefinedCommand("音频信息", "dumpsys audio", "查看音频信息", "硬件")
    )

    //真正执行命令的方法【返回值是Flow<ShellResult>类型的，也就是一个ShellResult类型的流】
    fun execute(command: String): Flow<ShellResult> = flow {
        val result = runCommand(command)
        emit(result)// 发送执行结果到流
    }.flowOn(Dispatchers.IO)
    //flowOn(Dispatchers.IO) 指定它上游的所有代码，在 IO 线程池里执行；
    //它下游的代码（比如 collect）在调用 Flow 的那个线程里执行。

    //执行命令的真正真正的方法
    //:ShellResult 这个是返回值
    //在 suspend 函数后面加 =，然后写 withContext(Dispatchers.IO) { ... }，大括号里放耗时操作，最后一行就是返回值。 ✅
    private suspend fun runCommand(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            if (!ShizukuHelper.isAvailable()) {
                return@withContext ShellResult(
                    command = command,
                    stdout = "",
                    stderr = "Shizuku 不可用。请先启动 Shizuku。",
                    exitCode = 127
                )
            }

            if (!ShizukuHelper.hasPermission()) {
                return@withContext ShellResult(
                    command = command,
                    stdout = "",
                    stderr = "Shizuku 权限未授权。请在 Shizuku 应用中授权权限。",
                    exitCode = 126
                )
            }

            // 使用统一的 ShizukuHelper.exec()，绕过 Shizuku 13+ 的 @RestrictTo 限制
            val stdout = ShizukuHelper.exec(command)// shell 进程中执行 sh -c '命令'[shell -command ]：启动一个新的 shell 进程，并执行 -c 后面跟着的那条命令。
            return@withContext ShellResult(
                command = command,
                stdout = stdout,        // ✅ 动态获取的
                stderr = "",            // 没有错误，就是空
                exitCode = 0            // 0 表示成功
            )//我只退出 withContext 这个代码块，并把后面的值作为 withContext 的执行结果返回出去。
        } catch (e: Exception) {
            return@withContext ShellResult(
                command = command,
                stdout = "",
                stderr = "shell命令执行错误信息: ${e.message}",
                exitCode = 1
            )
        }
    }
}