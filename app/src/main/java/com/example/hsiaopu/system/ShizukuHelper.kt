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
     * 通过 Shizuku 执行 shell 命令，返回标准输出（stdout）
     *
     * 执行流程：
     * 1. 检查 Shizuku 是否可用且已授权
     * 2. 通过反射调用 Shizuku.newProcess()，启动一个 shell 进程执行命令
     * 3. 逐行读取命令输出
     * 4. 等待进程结束，释放资源
     *
     * @param command 要执行的 shell 命令，例如 "settings put global bluetooth_on 1"
     * @return 命令的标准输出字符串
     * @throws IllegalStateException 当 Shizuku 不可用或未授权时抛出
     */
    fun exec(command: String): String {
        // ===== 前置校验：服务必须可用且已授权 =====
        if (!isAvailable() || !hasPermission()) {
            throw IllegalStateException("Shizuku 不可用或未授予权限")
        }

        // ===== 组装命令：sh -c 'command' =====
        // sh -c 的作用：启动一个新的 shell 解释器，去执行 -c 后面的命令字符串。
        // 套这一层是为了保证管道符（|）、重定向（>）、分号（;）等特殊符号能被正确解析。
        val args = arrayOf("sh", "-c", command)

        // ===== 通过反射调用 Shizuku.newProcess() =====
        // .invoke(null, args, null, null) 参数说明：
        //   - 第1个 null：静态方法不需要实例
        //   - 第2个 args：命令数组
        //   - 第3个 null：环境变量（不需要）
        //   - 第4个 null：工作目录（不需要）
        // as Process：把反射返回的 Any? 强转为 Process 对象
        val process = newProcessMethod.invoke(null, args, null, null) as Process
        //调用之前反射获取到的 newProcessMethod 这个方法，传给它 4 个参数，然后把返回值强制转换成 Process 类型。

        // ===== 读取命令输出 =====
        val output = StringBuilder()    // 可变字符串容器，追加、插入、删除、替换字符【比string性能好，因为string是不断的拷贝，这个是直接原地操作】

        try {//逐行读取输出内容
            // process.inputStream          → 进程的标准输出流（stdout）
            // InputStreamReader           → 字节流 → 字符流
            // BufferedReader              → 加缓冲区，支持按行读取【在内存里开一个“缓冲区”，批量读取数据，而不是一个字一个字地去读。】
            // .use { }                    → 自动关闭流，防止内存泄漏【无论执行成功还是抛出异常，都会在最后自动调用 .close() 关闭资源】
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String? = reader.readLine()   // 读第一行
                while (line != null) {                  // 读到末尾（null）时退出
                    output.appendLine(line)             // 把这一行追加到 output
                    line = reader.readLine()            // 读下一行
                }
            }
            // 等待进程完全执行完毕，有可能进程会有结束之后自动干一些事情的情况，获取退出码（这里不关心退出码，但必须 waitFor）
            // waitFor() 会阻塞当前线程，直到进程终止
            process.waitFor()
        } finally {
            // 释放进程占用的资源，无论成功还是异常都会执行
            process.destroy()
        }
        // trim() 去掉首尾多余的空白/换行，返回干净的字符串
        return output.toString().trim()
    }
}