package com.huginmunin.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.huginmunin.core.crypto.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class OAuthResult {
    data class Success(val accessToken: String, val refreshToken: String?, val provider: String) : OAuthResult()
    data class Error(val message: String) : OAuthResult()
    object UserCancelled : OAuthResult()
}

data class OAuthProvider(
    val name: String,
    val authEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>
)

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private val TAG = "AuthManager"
    
    // OAuth providers configuration
    val providers = mapOf(
        "google" to OAuthProvider(
            name = "Google",
            authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            clientId = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com",
            redirectUri = "com.huginmunin.app:/oauth2redirect",
            scopes = listOf("openid", "profile", "email", "https://www.googleapis.com/auth/calendar.readonly")
        ),
        "microsoft" to OAuthProvider(
            name = "Microsoft",
            authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            clientId = "YOUR_MICROSOFT_CLIENT_ID",
            redirectUri = "com.huginmunin.app:/oauth2redirect",
            scopes = listOf("openid", "profile", "email", "Calendars.Read")
        ),
        "github" to OAuthProvider(
            name = "GitHub",
            authEndpoint = "https://github.com/login/oauth/authorize",
            tokenEndpoint = "https://github.com/login/oauth/access_token",
            clientId = "YOUR_GITHUB_CLIENT_ID",
            redirectUri = "com.huginmunin.app:/oauth2redirect",
            scopes = listOf("read:user", "user:email")
        )
    )

    /**
     * Start OAuth flow for a provider
     */
    suspend fun startOAuthFlow(
        activity: Activity,
        providerKey: String
    ): Intent? {
        val provider = providers[providerKey] ?: return null
        
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(provider.authEndpoint),
            Uri.parse(provider.tokenEndpoint)
        )
        
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            provider.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(provider.redirectUri)
        ).setScopes(provider.scopes)
            .build()
        
        val authService = AuthorizationService(context)
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle OAuth callback
     */
    suspend fun handleOAuthCallback(
        intent: Intent,
        providerKey: String
    ): OAuthResult = suspendCancellableCoroutine { continuation ->
        val provider = providers[providerKey]
        if (provider == null) {
            continuation.resume(OAuthResult.Error("Unknown provider"))
            return@suspendCancellableCoroutine
        }
        
        val authResponse = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)
        
        if (authException != null) {
            Log.e(TAG, "OAuth error: ${authException.message}")
            continuation.resume(OAuthResult.Error(authException.message ?: "Unknown error"))
            return@suspendCancellableCoroutine
        }
        
        if (authResponse == null) {
            continuation.resume(OAuthResult.UserCancelled)
            return@suspendCancellableCoroutine
        }
        
        // Exchange authorization code for tokens
        val authService = AuthorizationService(context)
        authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse, tokenException ->
            if (tokenException != null) {
                continuation.resume(OAuthResult.Error(tokenException.message ?: "Token exchange failed"))
            } else if (tokenResponse != null) {
                val accessToken = tokenResponse.accessToken ?: ""
                val refreshToken = tokenResponse.refreshToken
                
                // Encrypt and store tokens
                try {
                    val encryptedAccess = cryptoManager.encrypt(accessToken.toByteArray())
                    val encryptedRefresh = refreshToken?.let { cryptoManager.encrypt(it.toByteArray()) }
                    
                    // Store in SharedPreferences or database
                    storeTokens(providerKey, encryptedAccess, encryptedRefresh)
                    
                    continuation.resume(OAuthResult.Success(accessToken, refreshToken, provider.name))
                } catch (e: Exception) {
                    continuation.resume(OAuthResult.Error("Failed to store tokens: ${e.message}"))
                }
            } else {
                continuation.resume(OAuthResult.Error("No token response"))
            }
        }
    }

    /**
     * Store encrypted tokens
     */
    private fun storeTokens(provider: String, accessToken: ByteArray, refreshToken: ByteArray?) {
        val prefs = context.getSharedPreferences("oauth_tokens", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("${provider}_access", android.util.Base64.encodeToString(accessToken, android.util.Base64.NO_WRAP))
            refreshToken?.let {
                putString("${provider}_refresh", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP))
            }
            apply()
        }
    }

    /**
     * Get stored access token
     */
    fun getAccessToken(provider: String): String? {
        val prefs = context.getSharedPreferences("oauth_tokens", Context.MODE_PRIVATE)
        val encrypted = prefs.getString("${provider}_access", null) ?: return null
        
        return try {
            val encryptedBytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
            val decrypted = cryptoManager.decrypt(encryptedBytes)
            String(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt token: ${e.message}")
            null
        }
    }

    /**
     * Refresh access token
     */
    suspend fun refreshAccessToken(providerKey: String): OAuthResult {
        val provider = providers[providerKey] ?: return OAuthResult.Error("Unknown provider")
        val prefs = context.getSharedPreferences("oauth_tokens", Context.MODE_PRIVATE)
        val encryptedRefresh = prefs.getString("${providerKey}_refresh", null)
            ?: return OAuthResult.Error("No refresh token")
        
        return try {
            val refreshTokenBytes = android.util.Base64.decode(encryptedRefresh, android.util.Base64.NO_WRAP)
            val refreshToken = String(cryptoManager.decrypt(refreshTokenBytes))
            
            // In real implementation, use AuthorizationService to refresh
            // For now, return mock
            OAuthResult.Success("new_access_token", refreshToken, provider.name)
        } catch (e: Exception) {
            OAuthResult.Error("Failed to refresh: ${e.message}")
        }
    }

    /**
     * Revoke tokens and clear storage
     */
    fun logout(provider: String) {
        val prefs = context.getSharedPreferences("oauth_tokens", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("${provider}_access")
            remove("${provider}_refresh")
            apply()
        }
    }
}
