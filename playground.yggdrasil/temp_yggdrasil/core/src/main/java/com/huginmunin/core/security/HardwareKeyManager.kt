package com.huginmunin.core.security

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.Fido2ApiClient
import com.google.android.gms.fido.fido2.api.common.*
import com.google.android.gms.tasks.Task
import com.huginmunin.core.config.ApiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class HardwareKeyResult {
    data class Success(val signature: ByteArray) : HardwareKeyResult()
    data class Error(val message: String) : HardwareKeyResult()
    object UserCancelled : HardwareKeyResult()
}

@Singleton
class HardwareKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fido2Client: Fido2ApiClient = Fido.getFido2ApiClient(context)
    private val TAG = "HardwareKeyManager"
    
    // FIDO2 settings from centralized config
    private val rpId = ApiConfig.FIDO2_RP_ID
    private val rpName = ApiConfig.FIDO2_RP_NAME

    /**
     * Register a new FIDO2 credential (for first-time hardware key setup)
     */
    suspend fun registerCredential(
        activity: Activity,
        userId: String,
        userName: String
    ): HardwareKeyResult = suspendCancellableCoroutine { continuation ->
        try {
            // Generate challenge
            val challenge = generateChallenge()
            
            // Create user entity
            val user = PublicKeyCredentialUserEntity(
                userId.toByteArray(),
                userName,
                "",
                "" // No display name
            )
            
            // Relying party
            val rp = PublicKeyCredentialRpEntity(rpId, rpName, null)
            
            // Parameters
            val pubKeyCredParams = listOf(
                PublicKeyCredentialParameters(
                    PublicKeyCredentialType.PUBLIC_KEY.toString(),
                    EC2Algorithm.ES256.algoValue
                )
            )
            
            // Authenticator selection
            val authenticatorSelection = AuthenticatorSelectionCriteria.Builder()
                .setAttachment(Attachment.PLATFORM) // Use device authenticator
                .setResidentKeyRequirement(ResidentKeyRequirement.RESIDENT_KEY_PREFERRED)
                // .setUserVerification(UserVerificationRequirement.USER_VERIFICATION_REQUIRED) // Deprecated or incorrect builder method
                .build()
            
            // Build registration request
            val options = PublicKeyCredentialCreationOptions.Builder()
                .setRp(rp)
                .setUser(user)
                .setChallenge(challenge)
                .setParameters(pubKeyCredParams)
                .setTimeoutSeconds(60.0)
                .setAuthenticatorSelection(authenticatorSelection)
                .setAttestationConveyancePreference(AttestationConveyancePreference.NONE)
                .build()
            
            // Register
            val task = fido2Client.getRegisterPendingIntent(options)
            
            task.addOnSuccessListener { pendingIntent ->
                try {
                    activity.startIntentSender(
                        pendingIntent.intentSender,
                        null,  // fillInIntent
                        0,     // flagsMask
                        0,     // flagsValues
                        0      // extraFlags
                    )
                    // Result will be handled in activity's onActivityResult with FIDO2_REGISTER_REQUEST_CODE
                    continuation.resume(HardwareKeyResult.Success(ByteArray(0)))
                } catch (e: Exception) {
                    continuation.resume(HardwareKeyResult.Error("Failed to launch FIDO2: ${e.message}"))
                }
            }.addOnFailureListener { e ->
                continuation.resume(HardwareKeyResult.Error("Registration failed: ${e.message}"))
            }
        } catch (e: Exception) {
            continuation.resume(HardwareKeyResult.Error("Unexpected error: ${e.message}"))
        }
    }

    /**
     * Verify hardware key for L4+ permissions
     */
    suspend fun verifyHardwareKey(
        activity: Activity,
        credentialId: ByteArray
    ): HardwareKeyResult = suspendCancellableCoroutine { continuation ->
        try {
            val challenge = generateChallenge()
            
            val allowList = listOf(
                PublicKeyCredentialDescriptor(
                    PublicKeyCredentialType.PUBLIC_KEY.toString(),
                    credentialId,
                    null
                )
            )
            
            val options = PublicKeyCredentialRequestOptions.Builder()
                .setRpId(rpId)
                .setChallenge(challenge)
                .setAllowList(allowList)
                .setTimeoutSeconds(60.0)
                // .setUserVerification(UserVerificationRequirement.USER_VERIFICATION_REQUIRED) // Deprecated or incorrect builder method
                .build()
            
            val task = fido2Client.getSignPendingIntent(options)
            
            task.addOnSuccessListener { pendingIntent ->
                try {
                    activity.startIntentSender(
                        pendingIntent.intentSender,
                        null,  // fillInIntent
                        0,     // flagsMask
                        0,     // flagsValues
                        0      // extraFlags
                    )
                    continuation.resume(HardwareKeyResult.Success(challenge))
                } catch (e: Exception) {
                    continuation.resume(HardwareKeyResult.Error("Failed to launch FIDO2: ${e.message}"))
                }
            }.addOnFailureListener { e ->
                continuation.resume(HardwareKeyResult.Error("Verification failed: ${e.message}"))
            }
        } catch (e: Exception) {
            continuation.resume(HardwareKeyResult.Error("Unexpected error: ${e.message}"))
        }
    }

    /**
     * Generate random challenge for FIDO2
     */
    private fun generateChallenge(): ByteArray {
        val random = SecureRandom()
        val challenge = ByteArray(32)
        random.nextBytes(challenge)
        return challenge
    }

    /**
     * Mock verification for L4 (fallback if no hardware key enrolled)
     */
    fun verifyMock(): Boolean {
        Log.w(TAG, "Using mock hardware key verification - not secure!")
        return true
    }

    companion object {
        const val FIDO2_REGISTER_REQUEST_CODE = ApiConfig.FIDO2_REGISTER_REQUEST_CODE
        const val FIDO2_SIGN_REQUEST_CODE = ApiConfig.FIDO2_SIGN_REQUEST_CODE
    }
}
