package com.huginmunin.core.crypto

import android.util.Base64
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class SignatureKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
)

sealed class SignatureResult {
    data class Success(val signature: ByteArray) : SignatureResult()
    data class Error(val message: String) : SignatureResult()
}

@Singleton
class MultiSignatureManager @Inject constructor() {

    /**
     * Generate Ed25519 key pair for a trustee
     */
    fun generateKeyPair(): SignatureKeyPair {
        val keyPair = Ed25519Sign.KeyPair.newKeyPair()
        return SignatureKeyPair(
            publicKey = keyPair.publicKey,
            privateKey = keyPair.privateKey
        )
    }

    /**
     * Sign data with private key
     */
    fun signData(data: ByteArray, privateKey: ByteArray): SignatureResult {
        return try {
            val signer = Ed25519Sign(privateKey)
            val signature = signer.sign(data)
            SignatureResult.Success(signature)
        } catch (e: Exception) {
            SignatureResult.Error("Signature failed: ${e.message}")
        }
    }

    /**
     * Verify signature with public key
     */
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val verifier = Ed25519Verify(publicKey)
            verifier.verify(signature, data)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create hash of action data for signing
     */
    fun hashActionData(action: String, actionData: String, timestamp: Long): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val combined = "$action|$actionData|$timestamp"
        return messageDigest.digest(combined.toByteArray())
    }

    /**
     * Encode to Base64 for storage
     */
    fun encodeToBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * Decode from Base64
     */
    fun decodeFromBase64(encoded: String): ByteArray {
        return Base64.decode(encoded, Base64.NO_WRAP)
    }
}
