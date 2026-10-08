package com.huginmunin.data.repository

import com.huginmunin.core.model.Trustee
import com.huginmunin.core.repository.TrusteeRepository
import com.huginmunin.data.dao.TrusteeDao
import com.huginmunin.data.entity.TrusteeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrusteeRepositoryImpl @Inject constructor(
    private val trusteeDao: TrusteeDao
) : TrusteeRepository {
    override fun getTrustees(): Flow<List<Trustee>> = trusteeDao.getAllTrustees().map { entities ->
        entities.map { entity ->
            Trustee(
                id = entity.id,
                name = entity.name,
                publicKey = entity.publicKey,
                relationship = entity.relationship
            )
        }
    }

    override suspend fun addTrustee(name: String, publicKey: String, relationship: String) {
        val trustee = TrusteeEntity(name = name, publicKey = publicKey, relationship = relationship)
        trusteeDao.insertTrustee(trustee)
    }

    override suspend fun requestMultiSigApproval(action: String, requiredSignatures: Int): Boolean {
        return true // Mock implementation
    }

    override suspend fun removeTrustee(id: Long) {
        trusteeDao.deleteTrusteeById(id)
    }
}
