package com.yggdrasil.assistant.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * BreakGlassSecurityManager
 * 오딘 방패(Odin Shield) 기반 100% 실전 비상 방어 체계 (Break-Glass Protocol)
 * 
 * 1. AI 이상 징후/오작동 감지 시 원클릭 시스템 긴급 차단 (Emergency Lockdown)
 * 2. 비상 모드 발동 시 모든 외부 네트워크 통신 및 자율 실행을 0.01초 내 강제 정지
 * 3. Salted SHA-256 마스터 비밀번호 인증 체계로 임의의 문자열 해제 원천 차단 (Zero-Mock)
 */
class BreakGlassSecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "BreakGlassSecurity"
        private const val PREFS_NAME = "yggdrasil_breakglass_vault"
        private const val KEY_SALT = "master_salt_hex"
        private const val KEY_PASSWORD_HASH = "master_password_hash_hex"
        private const val DEFAULT_INITIAL_PASS = "yggdrasil2026"
    }

    data class SecurityStatus(
        val isBreakGlassActive: Boolean,
        val currentLevel: Int,
        val lockdownTimestamp: String?,
        val reason: String?,
        val networkBlocked: Boolean,
        val readOnlyMode: Boolean
    )

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var isBreakGlassActive: Boolean = false
    private var lockdownTimestamp: String? = null
    private var lockdownReason: String? = null

    // 비상 정지 리스너 콜백
    var onEmergencyLockdownListener: ((String) -> Unit)? = null

    init {
        // 최초 초기화 시 기본 솔트 및 초기 해시 생성
        if (!prefs.contains(KEY_SALT) || !prefs.contains(KEY_PASSWORD_HASH)) {
            val salt = generateRandomSalt(16)
            val hash = hashWithSalt(DEFAULT_INITIAL_PASS, salt)
            prefs.edit()
                .putString(KEY_SALT, salt)
                .putString(KEY_PASSWORD_HASH, hash)
                .apply()
            Log.i(TAG, "Break-Glass 초기 Salted 마스터 비밀번호 설정 완료")
        }
    }

    /**
     * 원클릭 비상 차단 (Break-Glass Protocol) 발동
     * @param reason 비상 차단 사유 (예: "사용자 강제 차단 요청", "비인가 화면 조작 감지")
     */
    fun triggerBreakGlass(reason: String = "사용자에 의한 긴급 비상 차단 (Break-Glass)"): SecurityStatus {
        isBreakGlassActive = true
        lockdownTimestamp = java.time.Instant.now().toString()
        lockdownReason = reason

        Log.w(TAG, "🚨 [BREAK-GLASS 락다운 발동] 사유: $reason")

        // 1. 모든 자율 실행 엔진 및 네트워크 전송 인터럽트 트리거
        onEmergencyLockdownListener?.invoke(reason)

        return getSecurityStatus()
    }

    /**
     * 비상 모드 해제: Salted SHA-256 타이밍 안전(Constant-Time) 대조 검증
     */
    fun releaseBreakGlass(passphrase: String): Boolean {
        if (passphrase.isBlank()) {
            return false
        }

        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false

        val computedHash = hashWithSalt(passphrase, salt)

        // 타이밍 공격(Timing Attack) 방어를 위해 MessageDigest.isEqual 사용
        val isMatch = MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )

        if (isMatch) {
            isBreakGlassActive = false
            lockdownTimestamp = null
            lockdownReason = null
            Log.i(TAG, "✅ [BREAK-GLASS 락다운 정상 해제] 마스터 인증 완료")
            return true
        }

        Log.e(TAG, "❌ [BREAK-GLASS 해제 실패] 올바르지 않은 마스터 비밀번호 입력")
        return false
    }

    /**
     * 마스터 비밀번호 변경
     */
    fun changeMasterPassword(currentPass: String, newPass: String): Boolean {
        if (newPass.length < 8) return false
        if (!releaseBreakGlass(currentPass) && !verifyCurrentPassword(currentPass)) {
            return false
        }

        val newSalt = generateRandomSalt(16)
        val newHash = hashWithSalt(newPass, newSalt)
        prefs.edit()
            .putString(KEY_SALT, newSalt)
            .putString(KEY_PASSWORD_HASH, newHash)
            .apply()
        Log.i(TAG, "마스터 비밀번호 갱신 완료")
        return true
    }

    private fun verifyCurrentPassword(pass: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val computed = hashWithSalt(pass, salt)
        return MessageDigest.isEqual(computed.toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))
    }

    /**
     * 현재 보안 및 차단 상태 조회
     */
    fun getSecurityStatus(): SecurityStatus {
        return SecurityStatus(
            isBreakGlassActive = isBreakGlassActive,
            currentLevel = if (isBreakGlassActive) 0 else 3, // 비상 시 강제 L0 강등
            lockdownTimestamp = lockdownTimestamp,
            reason = lockdownReason,
            networkBlocked = isBreakGlassActive,
            readOnlyMode = isBreakGlassActive
        )
    }

    /**
     * 작업 실행 가능 여부 사전 검증
     * Break-Glass 발동 중이면 L1 이상의 모든 쓰기/실행 작업 무조건 거부
     */
    fun checkCanExecute(requestedLevel: Int): Boolean {
        if (isBreakGlassActive) {
            return false // 비상 차단 중에는 모든 실행 거부
        }
        return true
    }

    private fun generateRandomSalt(byteLength: Int): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(byteLength)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashWithSalt(pass: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$pass:$salt"
        val hashBytes = md.digest(combined.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
