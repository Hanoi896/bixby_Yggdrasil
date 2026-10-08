package com.huginmunin.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val actor: String,
    val action: String,
    val details: String, // JSON or encrypted content
    val signature: String // Base64 encoded signature
)
