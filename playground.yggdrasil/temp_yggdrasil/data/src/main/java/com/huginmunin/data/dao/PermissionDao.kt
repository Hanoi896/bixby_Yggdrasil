package com.huginmunin.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.huginmunin.data.entity.PermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionDao {
    @Insert
    suspend fun insertPermission(permission: PermissionEntity)

    @Query("SELECT * FROM permissions WHERE expiresAt > :currentTime ORDER BY level DESC")
    fun getActivePermissions(currentTime: Long): Flow<List<PermissionEntity>>

    @Query("DELETE FROM permissions WHERE id = :id")
    suspend fun revokePermission(id: Long)
}
