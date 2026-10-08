package com.huginmunin.core.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.signature.SignatureConfig
import java.io.IOException
import java.security.GeneralSecurityException

class TinkCryptoManager(context: Context) : CryptoManager {

    private val aead: Aead
    private val signer: PublicKeySign
    private val verifier: PublicKeyVerify

    companion object {
        private const val MASTER_KEY_URI = "android-keystore://master_key"
        private const val ENCRYPTION_KEYSET_NAME = "encryption_keyset"
        private const val SIGNATURE_KEYSET_NAME = "signature_keyset"
        private const val PREF_FILE_NAME = "hugin_munin_crypto_prefs"
    }

    init {
        SignatureConfig.register()
        // AeadConfig.register() is called by SignatureConfig.register() or similar if needed, 
        // but for standard Aead we might need AeadConfig.register().
        // However, Tink 1.7+ auto-registers standard primitives.
        // Let's explicitly register just in case or rely on standard config.
        // For Aead:
        com.google.crypto.tink.aead.AeadConfig.register()

        aead = getOrGenerateAead(context)
        val signaturePrimitives = getOrGenerateSignaturePrimitives(context)
        signer = signaturePrimitives.first
        verifier = signaturePrimitives.second
    }

    private fun getOrGenerateAead(context: Context): Aead {
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, ENCRYPTION_KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    private fun getOrGenerateSignaturePrimitives(context: Context): Pair<PublicKeySign, PublicKeyVerify> {
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, SIGNATURE_KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("ED25519"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        return Pair(
            handle.getPrimitive(PublicKeySign::class.java),
            handle.publicKeysetHandle.getPrimitive(PublicKeyVerify::class.java)
        )
    }

    override fun encrypt(data: ByteArray): ByteArray {
        // Empty associated data for now
        return aead.encrypt(data, null)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        return aead.decrypt(data, null)
    }

    override fun sign(data: ByteArray): ByteArray {
        return signer.sign(data)
    }

    override fun verify(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            verifier.verify(signature, data)
            true
        } catch (e: GeneralSecurityException) {
            false
        }
    }
}
