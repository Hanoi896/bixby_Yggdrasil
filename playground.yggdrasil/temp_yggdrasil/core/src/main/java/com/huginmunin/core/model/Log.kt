package com.huginmunin.core.model

data class Log(
    val id: Long = 0,
    val timestamp: Long,
    val actor: String,
    val action: String,
    val details: String,
    val signature: String
)
