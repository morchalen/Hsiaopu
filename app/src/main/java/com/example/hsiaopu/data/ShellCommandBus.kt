package com.example.hsiaopu.data
//热流
import com.example.hsiaopu.system.ShellResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
//热流
@Singleton//只创建一次，全局只有一个实例。
//shell命令总线
class ShellCommandBus @Inject constructor() {   
    //定义一个_的私有的热流，用于发送命令
    private val _commands = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 20)
    // bug修复记录，因为这里之前的replay = 1，每次重新连接可以看到上一条历史指令记录发过去，所以会导致命令重复发送，所以这里改成了replay = 0，避免重复发送
    //定义一个公开的热流，使用asSharedFlow() 变成只读的流
    public val commands = _commands.asSharedFlow()//也就是把这个可读写的流变成只读的流暴露给外部使用
    
    //定义一个_的私有的热流，用于发送结果
    private val _results = MutableSharedFlow<ShellResult>(replay = 0, extraBufferCapacity = 10)
    //定义一个公开的热流，使用asSharedFlow() 变成只读的流
    public val results = _results.asSharedFlow()//也就是把这个可读写的流变成只读的流暴露给外部使用

    
    //因为前面私有化了，如果其他地方需要发送流，需要使用这个函数，为什么需要前面私有这里暴露写的函数呢？
    // 不要把内部实现暴露给别人，而是把操作内部实现的方法暴露给别人。如果直接暴露参数，那么其他人直接也可以emit了，只有通过这个sendCommand方法才能确保因为封装（Encapsulation）和控制权限（Access Control）。
    suspend fun sendCommand(command: String) {//自定义发送命令的方法,外部传递给他公有的commands流
        _commands.emit(command)// 发送命令到命令通道，然后私有的_commands流会收到这个命令
    }

    suspend fun sendResult(result: ShellResult) {//自定义发送结果的方法,外部传递给他公有的results流
        _results.emit(result)// 发送结果到结果通道，然后私有的_results流会收到这个结果
    }
}