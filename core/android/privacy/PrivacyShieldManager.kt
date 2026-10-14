package com.yggdrasil.assistant.privacy

import android.content.Context
import java.util.regex.Pattern

/**
 * PrivacyShieldManager
 * 온디바이스 개인 프라이버시 보호 및 시크릿 모드(Incognito Mode) 관리자
 * 
 * 1. 원클릭 시크릿 모드 (학습 잠시 끄는 버튼):
 *    - 활성화 시 DB 저장 0%, 벡터화 0%, 자율 학습 0% (완전 휘발성 처리)
 * 2. 민감 카테고리 자동 차단 가드레일 (깜빡 잊었을 때 자동 안전망):
 *    - 성생활/19금 성인, 민감 의료/질병, 금융/주민번호를 자동 감지하여 영구 저장 원천 차단
 * 3. 타인 앞에서의 민감한 과거 이력 자동 브리핑 사고 방지
 */
class PrivacyShieldManager(private val context: Context) {

    @Volatile
    private var isIncognitoModeActive: Boolean = false

    // 민감 카테고리 자동 필터링 정규식 패턴 (성생활, 민감 의료, 개인 식별)
    private val SENSITIVE_PATTERNS = listOf(
        // 성생활 / 19금 성인 키워드
        Pattern.compile("(?i)(성인용품|성생활|피임|콘돔|성관계|자위|야동|포르노|adult|sex|porn|condom|contraception)"),
        // 민감 질병 / 의료 기록
        Pattern.compile("(?i)(비뇨기과|산부인과|정신과|우울증|조울증|성병|임신|낙태|에이즈|hiv|std|psychiatry|pregnancy)"),
        // 주민등록번호 / 보안카드
        Pattern.compile("(?i)(\\d{6}-[1-4]\\d{6}|주민번호|주민등록번호|보안카드|cvc)")
    )

    data class PrivacyEvaluation(
        val isAllowedToRecord: Boolean,
        val isIncognito: Boolean,
        val isSensitiveCategoryDetected: Boolean,
        val detectedReason: String?
    )

    /**
     * 시크릿 모드 (학습 끄기) 켜기
     */
    fun enableIncognitoMode() {
        isIncognitoModeActive = true
    }

    /**
     * 시크릿 모드 끄기 (정상 학습 모드 복귀)
     */
    fun disableIncognitoMode() {
        isIncognitoModeActive = false
    }

    /**
     * 현재 시크릿 모드 활성화 여부
     */
    fun isIncognitoActive(): Boolean = isIncognitoModeActive

    /**
     * 입력 텍스트가 학습/기록 가능한지 2중 프라이버시 검증
     * @param text 사용자 발화 또는 대화 내용
     */
    fun evaluatePrivacy(text: String): PrivacyEvaluation {
        // 1. 수동 토글 검사: 시크릿 모드가 켜져 있는 경우 무조건 기록 차단
        if (isIncognitoModeActive) {
            return PrivacyEvaluation(
                isAllowedToRecord = false,
                isIncognito = true,
                isSensitiveCategoryDetected = false,
                detectedReason = "시크릿 모드(학습 끄기) 활성화 중으로 휘발성 처리"
            )
        }

        // 2. 자동 안전망 검사: 민감 카테고리(성생활/의료/주민번호) 자동 탐지
        for (pattern in SENSITIVE_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val matchedKeyword = matcher.group()
                return PrivacyEvaluation(
                    isAllowedToRecord = false,
                    isIncognito = false,
                    isSensitiveCategoryDetected = true,
                    detectedReason = "민감 프라이버시 키워드 감지('$matchedKeyword')로 인한 자동 저장 차단"
                )
            }
        }

        // 3. 일반 안전 일상 대화 -> 기록 및 개인화 학습 허용
        return PrivacyEvaluation(
            isAllowedToRecord = true,
            isIncognito = false,
            isSensitiveCategoryDetected = false,
            detectedReason = null
        )
    }
}
