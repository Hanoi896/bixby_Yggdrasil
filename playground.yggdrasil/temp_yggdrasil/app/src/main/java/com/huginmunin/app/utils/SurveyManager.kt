package com.huginmunin.app.utils

import android.content.Context

/**
 * 설문조사 및 동의 관리 유틸리티
 * 사용자의 성향 설문 응답과 법적 동의 상태를 관리합니다.
 */
object SurveyManager {
    private const val PREFS_NAME = "survey_prefs"
    
    // ==================== 설문 응답 저장 ====================
    
    /**
     * 설문 응답 저장 (1-5점 척도)
     */
    fun saveSurveyResponse(context: Context, questionId: String, score: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("survey_$questionId", score).apply()
    }
    
    /**
     * 설문 응답 조회
     */
    fun getSurveyResponse(context: Context, questionId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("survey_$questionId", 0) // 0 = 미응답
    }
    
    /**
     * 모든 설문 응답 조회
     */
    fun getAllSurveyResponses(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.filterKeys { it.startsWith("survey_") }
            .mapKeys { it.key.removePrefix("survey_") }
            .mapValues { it.value as? Int ?: 0 }
    }
    
    // ==================== 동의 상태 관리 ====================
    
    /**
     * 동의 상태 저장
     */
    fun saveConsent(context: Context, consentId: String, agreed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("consent_$consentId", agreed).apply()
    }
    
    /**
     * 동의 상태 조회
     */
    fun getConsent(context: Context, consentId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("consent_$consentId", false)
    }
    
    /**
     * 필수 동의 항목 모두 완료 여부
     */
    fun areRequiredConsentsCompleted(context: Context): Boolean {
        return getConsent(context, "privacy_overseas") &&
               getConsent(context, "terms_of_service") &&
               getConsent(context, "personal_info_collection")
    }
    
    // ==================== 사용자 특성 분석 ====================
    
    /**
     * 성향 분석 결과 (카테고리별 평균 점수)
     */
    fun getPersonalityProfile(context: Context): PersonalityProfile {
        val responses = getAllSurveyResponses(context)
        
        return PersonalityProfile(
            extroversion = calculateCategoryAverage(responses, listOf("q1", "q6")),
            planning = calculateCategoryAverage(responses, listOf("q2", "q10")),
            analytical = calculateCategoryAverage(responses, listOf("q3", "q12")),
            emotional = calculateCategoryAverage(responses, listOf("q4", "q11")),
            adventurous = calculateCategoryAverage(responses, listOf("q5", "q15")),
            techSavvy = calculateCategoryAverage(responses, listOf("q7", "q13")),
            financial = calculateCategoryAverage(responses, listOf("q8", "q14")),
            healthConscious = calculateCategoryAverage(responses, listOf("q9", "q16"))
        )
    }
    
    private fun calculateCategoryAverage(responses: Map<String, Int>, questionIds: List<String>): Float {
        val scores = questionIds.mapNotNull { responses[it]?.takeIf { s -> s > 0 } }
        return if (scores.isNotEmpty()) scores.average().toFloat() else 0f
    }
    
    /**
     * 설문 완료 여부 (최소 16개 질문 중 12개 이상 응답)
     */
    fun isSurveyCompleted(context: Context): Boolean {
        val responses = getAllSurveyResponses(context)
        return responses.values.count { it > 0 } >= 12
    }
}

/**
 * 사용자 성향 프로필
 */
data class PersonalityProfile(
    val extroversion: Float,      // 외향성 (1-5)
    val planning: Float,          // 계획성 (1-5)
    val analytical: Float,        // 분석력 (1-5)
    val emotional: Float,         // 감성적 (1-5)
    val adventurous: Float,       // 모험성 (1-5)
    val techSavvy: Float,         // 기술 친화도 (1-5)
    val financial: Float,         // 재정 관리 (1-5)
    val healthConscious: Float    // 건강 관심도 (1-5)
)
