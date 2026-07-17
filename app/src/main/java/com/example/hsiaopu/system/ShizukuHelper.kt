package com.example.hsiaopu.system

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 权限管理 + 统一命令执行
 *
 * Shizuku 13+ 中 newProcess() 被标记为 @RestrictTo(LIBRARY_GROUP)，
 * Kotlin 编译器禁止直接调用。通过反射绕过此限制。
 */
//单例
object ShizukuHelper {
    //by lazy 是 Kotlin 的懒加载委托：变量在第一次被访问时才初始化，之后一直用缓存的值。适合用来初始化耗时对象（数据库、网络客户端等），能提升启动速度。 ✅
    //记住：by lazy = “用到才创建，只创建一次”。 //委托给lazy
    private val newProcessMethod by lazy {
    //通过反射获取 Shizuku 内部的 newProcess 方法，用来创建新的进程执行 Shell 命令。
    //反射 = "偷看/强行调用别人不想让你调用的私有方法"。
        Shizuku::class.java.getDeclaredMethod(//“从 Shizuku 这个类里，找到一个名叫 newProcess 的方法，这个方法接收三个参数：两个字符串数组和一个字符串。”
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).also { it.isAccessible = true }//设置为可访问（仅仅是这个类里面可以调用(不能重载)，而不是public）
        //also是顺手执行后面的操作[使用的对象是.前面的返回值，然后also后面的执行结果的返回值跟前面的是一样的]
        //类似的，还有let是做转换，apply是修改对象内部，run是执行一段逻辑，然后with是对同一个对象多次操作
    }

    /** 检查 Shizuku 是否可用 */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()//ping一下看看Shizuku 服务现在在跑吗
        } catch (_: Exception) {
            false
        }
    }

    /** 检查权限是否授予 */
    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED//检查你的 App 有没有被 Shizuku 授予“超级权限”。
        } catch (_: Exception) {
            false
        }
    }

    /** 请求权限 */
    fun requestPermission(requestCode: Int) {
        if (Shizuku.isPreV11()) return//如果是 Android 11 以下，Shizuku版本不支持这样授权，直接返回
        if (hasPermission()) return//如果已经授予了权限，直接返回
        //如果用户拒绝了权限，Shizuku 会自动提示用户请求权限。
        Shizuku.requestPermission(requestCode)//请求权限
        //如果用户拒绝了权限，Shizuku 会自动提示用户请求权限。
    }

    /** 是否应该显示权限说明 */
    fun shouldShowRationale(): Boolean {
        return try {
            Shizuku.shouldShowRequestPermissionRationale()//检查是否应该显示权限说明
        } catch (_: Exception) {
            false
        }
    }

    /**通过 Shizuku 执行 shell 命令，返回 stdout
     * 使用反射调用 Shizuku.newProcess() 绕过 @RestrictTo 限制。
     * 反射调用的是同一个底层方法，功能完全一致。
     */
    fun exec(command: String): String {
        if (!isAvailable() || !hasPermission()) {
            throw IllegalStateException("Shizuku 不可用或未授予权限")
        }
        //sh：调用 Android 系统的 Shell 解释器（/system/bin/sh） 
        //-c即-command；告诉 sh 解释器，后面跟着的是你要执行的命令字符串
        val args = arrayOf("sh", "-c", command)//命令行参数
        //.invoke(...)	调用这个方法（让它真正执行起来）【之前反射的newProcessMethod】
        //这里的反射出来的函数，反射的时候查找的是3个参数的函数，但是这里有四个参数，是因为调用的时候第一个参数需要填写实例对象，但是这里是单例，所以不需要写，单例就是全局只有一个对象通用的
        val process = newProcessMethod.invoke(null, args, null, null) as java.lang.Process
        //as = “把这个东西当作那个类型来用”。；；由于反射的函数，invoke执行出来，我们不知道是什么类型的，编译器也不知道，那么就是顶级父类any?类型的[可以为零的任意类型]；所以我们需要使用as把他转换为Process类型
        val output = StringBuilder()//StringBuilder = 一个“可变的字符串容器”，你可以往里面不断追加内容，类型是StringBuilder而不是string，想要string自己转换哈使用toString()

        try {
            // 1. 执行命令，读取输出
            //process.inputStream是一个“活”的流，数据是逐步到达的。
            //BufferedReader(字符流)=【队列】 “缓冲区输入流”，可以存放多行，每次使用里面的.readLine()方法就取一行然后指针移动到下一个\n。
            //InputStreamReader(字节流) =  把字节流inputStream（看不懂的 0101）转成 字符流（能看懂的文字）。
            //.use{} = “使用”，用来确保缓冲区占用的内存区域资源被正确关闭。
            //reader是一个形参，用来读取进程的输出流（stdout）。
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                //readLine()读取一行之后，会删除刚才的一行，然后新的一行，BufferedReader的大小是有限的，
                //阻塞的readLine(),如果当前行是完整的，也就是末尾有换行符，那么立刻给出这一行的内容，否则就阻塞的等待哈；
                var line: String? = reader.readLine()//阻塞的去读取第一行的reader内容，然后赋值给line，然后移动到下一行
                while (line != null) {
                    output.appendLine(line)//追加line这一行给output
                    line = reader.readLine()//阻塞的去读取当前行赋值给line，然后移动到下一行
                }
            }
            
            // 2. 输入流写入完成，等待进程的彻底收尾执行完成
            process.waitFor()//阻塞当前线程，直到进程执行完毕（终止），然后返回进程的退出码（exit code）。
        } finally {
            process.destroy()//销毁进程，释放资源
        }

        return output.toString().trim()//trim()是去掉字符串 首尾 的空格/换行
    }

    /** 通过 Shizuku 获取 WiFi 信息 */
    fun getWifiInfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!isAvailable() || !hasPermission()) return mapOf("Error" to "Shizuku not available")

        try {
            val output = exec("dumpsys wifi")
            output.lines().forEach { line ->
                when {
                    line.contains("SSID:") && !line.contains("unknown") ->
                        result["SSID"] = line.substringAfter("SSID:").trim().removeSurrounding("\"")
                    line.contains("BSSID:") ->
                        result["BSSID"] = line.substringAfter("BSSID:").trim().removeSurrounding("\"")
                    line.contains("IP:") ->
                        result["IP"] = line.substringAfter("IP:").trim().removeSurrounding("\"")
                    line.contains("Link speed:") ->
                        result["Speed"] = line.substringAfter("Link speed:").trim()
                    line.contains("RSSI:") ->
                        result["RSSI"] = line.substringAfter("RSSI:").trim()
                    line.contains("freq:") ->
                        result["Frequency"] = line.substringAfter("freq:").trim()
                }
            }
        } catch (_: Exception) {
            result["Error"] = "Failed to read WiFi info"
        }
        return result.ifEmpty { mapOf("Info" to "No WiFi data available") }
    }
}