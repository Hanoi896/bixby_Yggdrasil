package com.huginmunin.data.repository

import com.huginmunin.core.crypto.CryptoManager
import com.huginmunin.core.model.Log
import com.huginmunin.core.privacy.LearningControlManager
import com.huginmunin.core.repository.LogRepository
import com.huginmunin.data.dao.LogDao
import com.huginmunin.data.entity.LogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import android.util.Base64

class LogRepositoryImpl @Inject constructor(
    private val logDao: LogDao,
    private val cryptoManager: CryptoManager,
    private val learningControlManager: LearningControlManager
) : LogRepository {
    override fun getLogs(): Flow<List<Log>> = logDao.getAllLogs().map { entities ->
        entities.map { entity ->
            Log(
                id = entity.id,
                timestamp = entity.timestamp,
                actor = entity.actor,
                action = entity.action,
                details = entity.details,
                signature = entity.signature
            )
        }
    }

    override suspend fun logEvent(actor: String, action: String, details: String, category: String) {
        val isLearnable = learningControlManager.isCategoryAllowed(category)
        val finalDetails = if (isLearnable) details else "[LEARNING_PAUSED] $details"

        val timestamp = System.currentTimeMillis()
        val dataToSign = "$timestamp$actor$action$finalDetails".toByteArray(Charsets.UTF_8)
        val signature = cryptoManager.sign(dataToSign)
        val signatureStr = Base64.encodeToString(signature, Base64.NO_WRAP)

        val log = LogEntity(
            timestamp = timestamp,
            actor = actor,
            action = action,
            details = finalDetails,
            signature = signatureStr
        )
        logDao.insertLog(log)
    }
}
