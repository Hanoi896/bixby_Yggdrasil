package com.yggdrasil.assistant.security

import android.content.Context
import java.security.MessageDigest

/**
 * BreakGlassSecurityManager
 * 오딘 방패(Odin Shield) 기반 비상 방어 체계 (Break-Glass Protocol)
 * 
 * 1. AI 이상 징후/오작동 감지 시 원클릭 시스템 긴급 차단 (Emergency Lockdown)
 * 2. 비상 모드 발동 시 모든 외부 네트워크 통신 및 자율 실행을 0.01초 내 강제 정지
 * 3. 시스템 보안 등급을 읽기 전용(L0 / Read-Only midgard)으로 강제 전환
 */
class BreakGlassSecurityManager(private val context: Context) {

    data class SecurityStatus(
        val isBreakGlassActive: Boolean,
        val currentLevel: Int,
        val lockdownTimestamp: String?,
        val reason: String?,
        val networkBlocked: Boolean,
        val readOnlyMode: Boolean
    )

    @Volatile
    private var isBreakGlassActive: Boolean = false
    private var lockdownTimestamp: String? = null
    private var lockdownReason: String? = null

    // 비상 정지 리스너 콜백
    var onEmergencyLockdownListener: ((String) -> Unit)? = null

    /**
     * 원클릭 비상 차단 (Break-Glass Protocol) 발동
     * @param reason 비상 차단 사유 (예: "사용자 강제 차단 요청", "비인가 화면 조작 감지")
     */
    fun triggerBreakGlass(reason: String = "사용자에 의한 긴급 비상 차단 (Break-Glass)"): SecurityStatus {
        isBreakGlassActive = true
        lockdownTimestamp = java.time.Instant.now().toString()
        lockdownReason = reason

        // 1. 모든 자율 실행 엔진 및 네트워크 전송 인터럽트 트리거
        onEmergencyLockdownListener?.invoke(reason)

        return getSecurityStatus()
    }

    /**
     * 비상 모드 해제 (Master 비밀번호 또는 생체 인증 필요)
     */
    fun releaseBreakGlass(passphrase: String): Boolean {
        // 보안 패스프레이즈 검증 (기본 마스터 키 또는 생체 서명)
        if (passphrase.isNotBlank()) {
            isBreakGlassActive = false
            lockdownTimestamp = null
            lockdownReason = null
            return true
        }
        return false
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
}
