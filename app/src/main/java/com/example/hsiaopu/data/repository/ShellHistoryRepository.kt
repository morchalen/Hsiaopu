package com.example.hsiaopu.data.repository

import com.example.hsiaopu.data.local.ShellHistoryDao
import com.example.hsiaopu.data.local.ShellHistoryEntity
import com.example.hsiaopu.system.ShellResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellHistoryRepository @Inject constructor(
    private val shellHistoryDao: ShellHistoryDao
) {
    fun getAllHistory(): Flow<List<ShellResult>> =
        shellHistoryDao.getAllHistory().map { entities ->
            entities.map { it.toShellResult() }
        }

    suspend fun getAllHistorySync(): List<ShellResult> =
        shellHistoryDao.getAllHistorySync().map { it.toShellResult() }

    suspend fun insertHistory(result: ShellResult) {
        val entity = ShellHistoryEntity(
            command = result.command,
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
            timestamp = result.timestamp
        )
        shellHistoryDao.insertHistory(entity)
    }

    suspend fun clearAllHistory() {
        shellHistoryDao.clearAllHistory()
    }

    suspend fun getHistoryCount(): Int = shellHistoryDao.getHistoryCount()

    private fun ShellHistoryEntity.toShellResult(): ShellResult {
        return ShellResult(
            command = command,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            timestamp = timestamp
        )
    }
}