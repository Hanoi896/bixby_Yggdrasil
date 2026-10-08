package com.huginmunin.app.munin.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MuninDao {
    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("SELECT * FROM munin_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<LogEntity>>

    @Query("DELETE FROM munin_logs WHERE timestamp < :threshold")
    suspend fun deleteOldLogs(threshold: Long)
}
