package com.huginmunin.app.munin

import com.huginmunin.app.draupnir.Draupnir
import com.huginmunin.app.munin.db.LogEntity
import com.huginmunin.app.munin.db.MuninDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Munin: The Memory (Repository)
 * 모든 데이터를 기록하고 관리합니다.
 */
@Singleton
class MuninRepository @Inject constructor(
    private val draupnir: Draupnir,
    private val muninDao: MuninDao
) {
    private val scope = CoroutineScope(SupervisorJob() + draupnir.memoryDispatcher)

    fun log(tag: String, message: String, level: String = "INFO") {
        scope.launch {
            val log = LogEntity(tag = tag, message = message, level = level)
            muninDao.insertLog(log)
            // Console output for debugging
            println("[Munin] $tag: $message")
        }
    }

    /**
     * Emergency synchronous logging (for CrashHandler)
     * Uses runBlocking to ensure log is written before app dies.
     */
    fun logBlocking(tag: String, message: String, level: String) {
        try {
            kotlinx.coroutines.runBlocking {
                val log = LogEntity(tag = tag, message = message, level = level)
                muninDao.insertLog(log)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecentMemories(): Flow<List<LogEntity>> {
        return muninDao.getRecentLogs()
    }

    fun rememberContext(key: String, value: Any) {
        scope.launch {
            // TODO: Key-Value 저장소에 컨텍스트 저장
            log("Context", "Remembered: $key = $value")
        }
    }
}
