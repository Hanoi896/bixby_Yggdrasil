package com.huginmunin.core.crypto

interface CryptoManager {
    fun encrypt(data: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray): Boolean
}
