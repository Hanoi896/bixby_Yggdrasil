package com.huginmunin.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trustees")
data class TrusteeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val publicKey: String, // Base64 encoded public key of the trustee
    val relationship: String,
    val addedAt: Long = System.currentTimeMillis()
)
