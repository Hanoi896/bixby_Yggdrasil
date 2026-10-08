package com.huginmunin.core.repository

import com.huginmunin.core.model.Trustee
import kotlinx.coroutines.flow.Flow

interface TrusteeRepository {
    fun getTrustees(): Flow<List<Trustee>>
    suspend fun addTrustee(name: String, publicKey: String, relationship: String)
    suspend fun requestMultiSigApproval(action: String, requiredSignatures: Int): Boolean
    suspend fun removeTrustee(id: Long)
}
