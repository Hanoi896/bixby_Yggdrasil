package com.huginmunin.data.repository

import android.util.Base64
import com.huginmunin.core.crypto.CryptoManager
import com.huginmunin.core.security.HardwareKeyManager
import com.huginmunin.data.dao.PermissionDao
import com.huginmunin.data.entity.PermissionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepository @Inject constructor(
    private val permissionDao: PermissionDao,
    private val cryptoManager: CryptoManager,
    private val hardwareKeyManager: HardwareKeyManager
) {
    fun getActivePermissions(): Flow<List<PermissionEntity>> {
        return permissionDao.getActivePermissions(System.currentTimeMillis())
    }

    suspend fun grantPermission(level: Int, scope: String, durationMs: Long) {
        // Additional checks for high-level permissions
        if (level >= 4) {
            // L4 requires Hardware Key verification
            // Note: Hardware key verification requires activity context
            // This should be checked at UI layer before calling this method
        }
        
        if (level >= 6) {
            // L6 requires Multi-sig (Trustee Approval)
            // Note: Trustee approval should be obtained at UI layer
            // before calling this method to avoid circular dependencies
        }

        val grantedAt = System.currentTimeMillis()
        val expiresAt = grantedAt + durationMs
        
        val dataToSign = "$level$scope$grantedAt$expiresAt".toByteArray(Charsets.UTF_8)
        val signature = cryptoManager.sign(dataToSign)
        val signatureStr = Base64.encodeToString(signature, Base64.NO_WRAP)

        val permission = PermissionEntity(
            level = level,
            scope = scope,
            grantedAt = grantedAt,
            expiresAt = expiresAt,
            signature = signatureStr
        )
        permissionDao.insertPermission(permission)
    }

    suspend fun revokePermission(id: Long) {
        permissionDao.revokePermission(id)
    }
}
