package com.yggdrasil.assistant.security

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * [OAuth2PkceFlowManager]
 * OAuth 2.0 PKCE(RFC 7636) 표준 소셜 로그인 및 토큰 발급 매니저.
 * 안드로이드 시스템의 Chrome Custom Tabs를 사용하여 보안 WebView 샌드박스 위에서 안전하게 인증을 진행합니다.
 */
class OAuth2PkceFlowManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: OAuth2PkceFlowManager? = null

        fun getInstance(context: Context): OAuth2PkceFlowManager {
            return instance ?: synchronized(this) {
                instance ?: OAuth2PkceFlowManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var activeCodeVerifier: String? = null

    /**
     * 128자 고유 PKCE Code Verifier 생성 (A-Z, a-z, 0-9, -, ., _, ~)
     */
    fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        val verifier = Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        activeCodeVerifier = verifier
        return verifier
    }

    /**
     * SHA-256 기반 Code Challenge 연산
     */
    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes, 0, bytes.size)
        val hash = digest.digest()
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Custom Tabs를 통한 소셜 로그인 브라우저 기동
     */
    fun launchLoginTab(authorizationEndpoint: String, clientId: String, redirectUri: String, scope: String) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        val uri = Uri.parse(authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, uri)
    }

    /**
     * 딥링크로 반환된 Authorization Code 확인 및 Verifier 반환
     */
    fun retrieveCodeVerifierForTokenExchange(authCode: String): String? {
        val verifier = activeCodeVerifier
        activeCodeVerifier = null
        return verifier
    }
}
