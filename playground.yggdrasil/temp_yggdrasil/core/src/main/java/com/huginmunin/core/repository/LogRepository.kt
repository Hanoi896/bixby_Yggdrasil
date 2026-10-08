package com.huginmunin.core.repository

import com.huginmunin.core.model.Log
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getLogs(): Flow<List<Log>>
    suspend fun logEvent(actor: String, action: String, details: String, category: String = "general")
}
