package com.yggdrasil.assistant.privacy

import android.content.Context
import com.yggdrasil.assistant.rag.MuninRagManager

/**
 * DialogueArchiver
 * 대화 텍스트 자동 변환 아카이빙 및 프라이버시 필터링 브릿지
 * 
 * 1. 실시간 대화 쌍([사용자] 발화 ↔ [Yggdrasil] 답변)을 구조화 텍스트로 변환
 * 2. PrivacyShieldManager 검증을 거쳐 성생활/민감의료/시크릿 모드 시 아카이빙 즉시 배제
 * 3. 안전한 일상/업무 대화만 Munin SQLCipher AES-256 암호화 DB에 보관
 */
class DialogueArchiver(
    private val context: Context,
    private val privacyShield: PrivacyShieldManager,
    private val muninRagManager: MuninRagManager
) {

    data class ArchivingResult(
        val archived: Boolean,
        val memoryId: String?,
        val reason: String
    )

    /**
     * 대화 완료 시 텍스트 변환 및 조건부 암호화 아카이빙
     * @param userUtterance 사용자 발화
     * @param assistantReply AI 어시스턴트 답변
     */
    fun processAndArchiveDialogue(
        userUtterance: String,
        assistantReply: String
    ): ArchivingResult {
        // 1. 프라이버시 및 민감 카테고리 평가
        val fullDialogue = "$userUtterance $assistantReply"
        val evaluation = privacyShield.evaluatePrivacy(fullDialogue)

        if (!evaluation.isAllowedToRecord) {
            return ArchivingResult(
                archived = false,
                memoryId = null,
                reason = evaluation.detectedReason ?: "프라이버시 정책에 의해 저장 제외"
            )
        }

        // 2. 안전한 대화만 Munin SQLCipher 암호화 DB에 저장
        val memId = muninRagManager.archiveDialogue(userUtterance, assistantReply, importance = 2)

        return ArchivingResult(
            archived = true,
            memoryId = memId,
            reason = "대화 텍스트 변환 및 온디바이스 암호화 보관 완료"
        )
    }
}
