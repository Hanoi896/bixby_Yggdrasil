package com.huginmunin.core.model

data class Trustee(
    val id: Long = 0,
    val name: String,
    val publicKey: String,
    val relationship: String
)
