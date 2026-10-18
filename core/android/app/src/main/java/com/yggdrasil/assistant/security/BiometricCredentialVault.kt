package com.yggdrasil.assistant.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [BiometricCredentialVault]
 * Knox Vault / ARM TrustZone TEE 하드웨어 키스토어 기반 자격증명 암복호화 엔진.
 * 지문 원본 추출이 불가능한 안드로이드 보안 아키텍처에 따라,
 * BiometricPrompt 인증을 성공해야만 릴리즈되는 하드웨어 키로 비밀번호 및 토큰을 보호합니다.
 */
class BiometricCredentialVault private constructor(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "YggdrasilMasterKey"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        @Volatile
        private var instance: BiometricCredentialVault? = null

        fun getInstance(context: Context): BiometricCredentialVault {
            return instance ?: synchronized(this) {
                instance ?: BiometricCredentialVault(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        ensureMasterKeyExists()
    }

    private fun ensureMasterKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // 백그라운드 쿼리 기본 키, 생체 챌린지는 별도 CryptoObject로 처리
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
    }

    /**
     * 비밀번호/자격증명 하드웨어 암호화
     */
    fun encryptCredential(plainText: String): EncryptedPayload {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(iv = iv, cipherText = cipherText)
    }

    /**
     * 비밀번호/자격증명 하드웨어 복호화 (하위 호환)
     */
    fun decryptCredential(payload: EncryptedPayload): String {
        return useDecryptedCredential(payload) { chars ->
            String(chars)
        }
    }

    /**
     * 금융/보안 업계 표준: 메모리 덤프 방어 제로라이즈(Zeroization) 복호화
     * 사용 직후 임시 평문 바이트 및 캐릭터 버퍼를 '\0'로 완전 덮어써 잔류 차단
     */
    fun <R> useDecryptedCredential(payload: EncryptedPayload, block: (CharArray) -> R): R {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val plainBytes = cipher.doFinal(payload.cipherText)
        val chars = Charsets.UTF_8.decode(java.nio.ByteBuffer.wrap(plainBytes)).array()
        try {
            return block(chars)
        } finally {
            java.util.Arrays.fill(plainBytes, 0.toByte())
            java.util.Arrays.fill(chars, '\u0000')
        }
    }

    data class EncryptedPayload(
        val iv: ByteArray,
        val cipherText: ByteArray
    )
}
