package com.example.hsiaopu.system

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 权限管理 + 统一命令执行
 *
 * 核心原理：
 * Shizuku Server 是一个以 shell 用户身份独立运行的高权限进程，
 * 通过 Binder 通信接收 App 请求，代执行 shell 命令。
 *
 * Shizuku 13+ 中 newProcess() 被标记为 @RestrictTo(LIBRARY_GROUP)，
 * Kotlin 编译器禁止直接调用，通过反射绕过此限制。
 */
object ShizukuHelper {

    /**
     * 反射获取 Shizuku 内部的 newProcess 方法
     *
     * 为什么用反射？
     * 因为 Shizuku 高版本把这个方法藏起来了（加了 @RestrictTo），
     * 直接调用编译会报错。反射可以强行调用。
     *
     * by lazy = 懒加载，只有第一次访问时才初始化，之后直接用缓存
     */
    private val newProcessMethod by lazy {//找到一个名为 newProcess 的方法，我们把他叫 newProcessMethod
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",                           // 方法名
            Array<String>::class.java,              // 第1个参数：String数组（命令+参数）
            Array<String>::class.java,              // 第2个参数：String数组（环境变量，可为null）
            String::class.java                      // 第3个参数：工作目录路径（可为null）
        ).also { it.isAccessible = true }           // 设置为可访问（强行突破 private/@RestrictTo）
        // .also 顺手把方法设为可访问，然后返回原对象
    }

    /**
     * 检查 Shizuku 服务是否可用
     *
     * Shizuku Server 是一个独立进程，容易被系统回收，
     * 执行命令前必须先检查，避免崩溃。
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()    // ping 一下，看服务是否存活
        } catch (_: Exception) {
            false
        }
    }

    // 检查 App 是否被 Shizuku 授权
    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            //这是 Shizuku SDK 提供的一个方法，返回一个 int 类型的权限状态码。
            //这是 Android 系统定义的一个常量，值是 0，表示"权限已被授予"。
            // 对应地，PackageManager.PERMISSION_DENIED 值是 -1，表示"权限被拒绝"。
        } catch (_: Exception) {
            false
        }
    }

    //请求 Shizuku 权限
    fun requestPermission(requestCode: Int) {
        // Android 11 以下不支持 Shizuku 的权限机制
        if (Shizuku.isPreV11()) return
        // 已有权限则直接返回
        if (hasPermission()) return
        // 弹出授权弹窗
        Shizuku.requestPermission(requestCode)// Shizuku 弹个系统对话框，问用户"你允许这个 App 用我的高权限能力吗？
    }

    /**
     * 检查是否应该显示权限说明
     *
     * 如果返回 true，建议弹个对话框告诉用户“为什么需要这个权限”
     */
    fun shouldShowRationale(): Boolean {
        return try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 通过 Shizuku 执行 shell 命令，返回完整执行结果（stdout / stderr / 退出码）
     *
     * 执行流程：
     * 1. 检查 Shizuku 是否可用且已授权
     * 2. 通过反射调用 Shizuku.newProcess()，启动一个 shell 进程执行命令
     * 3. 逐行读取命令输出（stdout），同时用后台线程读取 stderr
     * 4. 等待进程结束，获取真实退出码，释放资源
     *
     * @param command 要执行的 shell 命令，例如 "settings put global bluetooth_on 1"
     * @return ShellResult（含 stdout、stderr、exitCode；exitCode=0 表示成功，非 0 表示失败）
     */
    fun exec(command: String): ShellResult {
        // ===== 前置校验：服务必须可用且已授权 =====
        if (!isAvailable() || !hasPermission()) {
            return ShellResult(command, "", "Shizuku 不可用或未授予权限", 127)
        }

        // ===== 组装命令：sh -c 'command' =====
        // sh -c 的作用：启动一个新的 shell 解释器，去执行 -c 后面的命令字符串。
        // 套这一层是为了保证管道符（|）、重定向（>）、分号（;）等特殊符号能被正确解析。
        val args = arrayOf("sh", "-c", command)

        // ===== 通过反射调用 Shizuku.newProcess() =====
        val process = newProcessMethod.invoke(null, args, null, null) as Process

        val stdout = StringBuilder()    // 标准输出容器
        val stderr = StringBuilder()    // 标准错误容器

        // 后台线程读取 stderr，防止管道缓冲区写满导致主线程读 stdout 时死锁
        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stderr.appendLine(line)
                        line = reader.readLine()
                    }
                }
            } catch (_: Exception) {
                // 读取 stderr 失败不影响主流程
            }
        }.apply { start() }

        try {
            // 逐行读取标准输出
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stdout.appendLine(line)
                    line = reader.readLine()
                }
            }
            // 等 stderr 线程读完，再等进程结束拿真实退出码（0=成功，非0=失败）
            stderrThread.join()
            val exitCode = process.waitFor()
            return ShellResult(
                command = command,
                stdout = stdout.toString().trim(),
                stderr = stderr.toString().trim(),
                exitCode = exitCode
            )
        } finally {
            // 释放进程占用的资源，无论成功还是异常都会执行
            process.destroy()
        }
    }
}