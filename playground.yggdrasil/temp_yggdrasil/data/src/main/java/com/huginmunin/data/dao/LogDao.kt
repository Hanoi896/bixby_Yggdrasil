package com.huginmunin.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.huginmunin.data.entity.LogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>
}
