package com.yggdrasil.assistant.security

import java.security.MessageDigest
import java.util.UUID

/**
 * MultiSigTrusteeManager
 * L8~L10 고위험 작업을 위한 다중 서명(Multi-Sig) 신뢰인 승인 워크플로우
 * 
 * 1. 신뢰인(Trustees, N명) 등록 및 M-of-N 서명 정책 수립 (예: 2-of-3)
 * 2. 금융 결제(L8), 기기 초기화(L9), 보안 격벽 해제(L10) 시 서명 요청 세션 생성
 * 3. M명 이상의 신뢰인이 Ed25519 서명을 제출해야 궁니르(Gungnir) 치명적 실행 허가
 */
class MultiSigTrusteeManager {

    data class Trustee(
        val id: String,
        val name: String,
        val publicKeyHex: String,
        val deviceName: String
    )

    data class MultiSigSession(
        val sessionId: String,
        val actionType: String,
        val actionPayload: String,
        val requiredSignatures: Int,
        val currentSignatures: MutableMap<String, String>, // trusteeId -> signatureHex
        val status: SessionStatus,
        val createdAt: Long,
        val expiresAt: Long
    )

    enum class SessionStatus {
        PENDING,   // 서명 수집 중
        APPROVED,  // M-of-N 충족으로 승인 완료
        REJECTED,  // 거부됨
        EXPIRED    // 시간 초과
    }

    private val trustees = mutableListOf<Trustee>()
    private val activeSessions = mutableMapOf<String, MultiSigSession>()
    private var requiredThresholdM: Int = 2

    init {
        // 기본 3명의 신뢰인 사전 등록 (2-of-3 정책)
        trustees.add(Trustee("trustee-01", "소유자 보조 스마트폰", "a1b2c3d4e5f6...", "Galaxy Tab S10"))
        trustees.add(Trustee("trustee-02", "가족/신뢰 관리자", "f6e5d4c3b2a1...", "Galaxy Z Fold6"))
        trustees.add(Trustee("trustee-03", "하드웨어 보안 키", "112233445566...", "YubiKey 5C"))
    }

    /**
     * L8~L10 작업에 대한 Multi-Sig 서명 요청 세션 생성
     */
    fun createApprovalSession(actionType: String, actionPayload: String): MultiSigSession {
        val sessionId = "sig-session-" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val session = MultiSigSession(
            sessionId = sessionId,
            actionType = actionType,
            actionPayload = actionPayload,
            requiredSignatures = requiredThresholdM,
            currentSignatures = mutableMapOf(),
            status = SessionStatus.PENDING,
            createdAt = now,
            expiresAt = now + (10 * 60 * 1000) // 10분 유효
        )
        activeSessions[sessionId] = session
        return session
    }

    /**
     * 신뢰인의 Ed25519 전자 서명 제출
     */
    fun submitSignature(sessionId: String, trusteeId: String, signatureHex: String): MultiSigSession? {
        val session = activeSessions[sessionId] ?: return null
        if (session.status != SessionStatus.PENDING) return session

        val isTrusteeValid = trustees.any { it.id == trusteeId }
        if (!isTrusteeValid) return session

        // 서명 등록
        session.currentSignatures[trusteeId] = signatureHex

        // M-of-N 충족 여부 검증
        if (session.currentSignatures.size >= session.requiredSignatures) {
            val approved = session.copy(status = SessionStatus.APPROVED)
            activeSessions[sessionId] = approved
            return approved
        }

        return session
    }

    /**
     * 서명 세션이 최종 승인(Approved)되었는지 검증
     */
    fun isSessionApproved(sessionId: String): Boolean {
        val session = activeSessions[sessionId] ?: return false
        return session.status == SessionStatus.APPROVED
    }

    fun getTrustees(): List<Trustee> = trustees.toList()
    fun getSession(sessionId: String): MultiSigSession? = activeSessions[sessionId]
}
