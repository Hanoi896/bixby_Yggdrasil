package com.huginmunin.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permissions")
data class PermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: Int, // 0 to 10
    val scope: String, // JSON defining scope
    val grantedAt: Long,
    val expiresAt: Long,
    val signature: String // Base64 encoded signature
)
