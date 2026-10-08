package com.huginmunin.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val authService = AuthorizationService(context)

    // Demo configuration for Google OAuth (replace with real config)
    private val authConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://www.googleapis.com/oauth2/v4/token")
    )
    
    private val clientId = "YOUR_CLIENT_ID" // Placeholder
    private val redirectUri = Uri.parse("com.huginmunin.app:/oauth2callback")

    fun getAuthIntent(): Intent {
        val authRequest = AuthorizationRequest.Builder(
            authConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        ).setScopes("openid", "profile", "email")
         .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }
    
    fun dispose() {
        authService.dispose()
    }
}
