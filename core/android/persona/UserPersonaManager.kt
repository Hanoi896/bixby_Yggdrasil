package com.yggdrasil.core.persona

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * [UserPersonaManager]
 * 사용자의 최초 온보딩(First-run Setup), 호칭, AI 명칭, 말투(Tone)를 관리하고
 * 로컬 NPU LLM(EXAONE 3.0) 추론 시 일관된 인격/페르소나 프롬프트를 주입하는 매니저
 */
class UserPersonaManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "yggdrasil_persona_vault"
        private const val KEY_IS_FIRST_RUN = "is_first_run"
        private const val KEY_USER_CALL_NAME = "user_call_name"
        private const val KEY_AI_NAME = "ai_name"
        private const val KEY_SPEECH_TONE = "speech_tone"
        private const val KEY_USER_INTERESTS = "user_interests"

        @Volatile
        private var instance: UserPersonaManager? = null

        fun getInstance(context: Context): UserPersonaManager {
            return instance ?: synchronized(this) {
                instance ?: UserPersonaManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * AI의 말투 스타일 정의
     */
    enum class SpeechTone(val id: String, val displayName: String, val description: String) {
        HONORIFIC_SECRETARY("secretary", "정중한 비서 모드", "품격 있고 정중한 격식체(하십시오체/해요체)로 보고 및 응답"),
        INTIMATE_FRIEND("friend", "친근한 친구 모드", "편안하고 다정한 반말/친한 어투(해체)로 대화"),
        WITTY_ASSISTANT("witty", "위트 있는 조수 모드", "스마트하면서도 적절한 유머와 재치가 섞인 친절한 말투")
    }

    /**
     * 사용자 페르소나 데이터 모델
     */
    data class PersonaProfile(
        val isFirstRun: Boolean,
        val userCallName: String,
        val aiName: String,
        val tone: SpeechTone,
        val userInterests: List<String>
    )

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 최초 실행 여부 확인
     * @return 최초 실행이면 true, 이미 온보딩을 마쳤으면 false
     */
    fun isFirstRun(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_RUN, true)
    }

    /**
     * 저장된 페르소나 프로필 로드
     */
    fun getProfile(): PersonaProfile {
        val isFirst = isFirstRun()
        val userCallName = prefs.getString(KEY_USER_CALL_NAME, "사용자님") ?: "사용자님"
        val aiName = prefs.getString(KEY_AI_NAME, "이그드라실") ?: "이그드라실"
        val toneId = prefs.getString(KEY_SPEECH_TONE, SpeechTone.HONORIFIC_SECRETARY.id)
        val tone = SpeechTone.values().find { it.id == toneId } ?: SpeechTone.HONORIFIC_SECRETARY
        val interestsJson = prefs.getString(KEY_USER_INTERESTS, "[]") ?: "[]"
        val interestsList = mutableListOf<String>()
        try {
            val jsonArray = org.json.JSONArray(interestsJson)
            for (i in 0 until jsonArray.length()) {
                interestsList.add(jsonArray.getString(i))
            }
        } catch (_: Exception) { }

        return PersonaProfile(isFirst, userCallName, aiName, tone, interestsList)
    }

    /**
     * 최초 온보딩 설정 완료 저장
     * @param userCallName 사용자가 불리고 싶은 호칭 (예: "지훈이 형", "대표님", "오빠")
     * @param aiName AI를 부를 이름 (예: "이그드라실", "자비스", "루나")
     * @param tone 말투 스타일
     * @param interests 관심사 키워드 목록
     */
    fun completeOnboarding(
        userCallName: String,
        aiName: String = "이그드라실",
        tone: SpeechTone = SpeechTone.HONORIFIC_SECRETARY,
        interests: List<String> = emptyList()
    ) {
        val safeUserCallName = userCallName.trim().ifEmpty { "사용자님" }
        val safeAiName = aiName.trim().ifEmpty { "이그드라실" }
        val interestsJson = org.json.JSONArray(interests).toString()

        prefs.edit()
            .putBoolean(KEY_IS_FIRST_RUN, false)
            .putString(KEY_USER_CALL_NAME, safeUserCallName)
            .putString(KEY_AI_NAME, safeAiName)
            .putString(KEY_SPEECH_TONE, tone.id)
            .putString(KEY_USER_INTERESTS, interestsJson)
            .apply()
    }

    /**
     * 로컬 NPU(EXAONE 3.0) 추론을 위한 개인화 System Prompt 생성
     * 사용자의 설정(호칭, AI 이름, 말투)을 프롬프트 선두에 절대 원칙으로 주입
     */
    fun buildPersonalizedSystemPrompt(baseSystemPrompt: String): String {
        val profile = getProfile()
        val toneInstruction = when (profile.tone) {
            SpeechTone.HONORIFIC_SECRETARY ->
                "- 말투 지침: 최고 수준의 예의를 갖춘 비서로서 정중하고 공손한 경어체('~하셨습니다', '~드리겠습니다', '~해요')를 일관되게 사용하십시오."
            SpeechTone.INTIMATE_FRIEND ->
                "- 말투 지침: 오랜 절친 또는 든든한 동생처럼 다정하고 편안한 반말('~했어?', '~알겠어!', '~해둘게')을 사용하십시오. 단, 무례하지 않고 신뢰감을 주어야 합니다."
            SpeechTone.WITTY_ASSISTANT ->
                "- 말투 지침: 명석하고 센스 있는 조수처럼 적절한 유머와 재치를 섞은 친절한 어투로 대화하십시오."
        }

        val promptBuilder = StringBuilder()
        promptBuilder.append("[SYSTEM IDENTITY & USER PERSONA]\n")
        promptBuilder.append("- 너의 이름: ${profile.aiName}\n")
        promptBuilder.append("- 대화 상대(사용자) 호칭: 반드시 '${profile.userCallName}'(으)로 부를 것.\n")
        promptBuilder.append(toneInstruction).append("\n")
        if (profile.userInterests.isNotEmpty()) {
            promptBuilder.append("- 사용자 관심 분야: ${profile.userInterests.joinToString(", ")}\n")
        }
        promptBuilder.append("- 기본 시스템 원칙: 사용자의 프라이버시를 절대 보호하며 기기 내 네이티브 리소스를 적극 활용할 것.\n\n")
        promptBuilder.append("[BASE INSTRUCTIONS]\n")
        promptBuilder.append(baseSystemPrompt)

        return promptBuilder.toString()
    }

    /**
     * 첫 실행 감지 시 온보딩 대화 안내 메시지 생성
     */
    fun getInitialGreetingMessage(): String {
        return "반갑습니다! 저는 당신의 갤럭시 전용 AI 파트너 '이그드라실'입니다.\n" +
               "원활한 맞춤형 보좌를 위해 몇 가지만 정해볼까요?\n" +
               "1. 제가 당신을 어떻게 불러드릴까요? (예: 지훈이 형, 대표님, 지훈 님)\n" +
               "2. 저의 이름을 다르게 부르고 싶으신가요? (기본: 이그드라실)\n" +
               "3. 어떤 말투가 편하신가요? (정중한 비서 모드 / 편안한 친구 모드 / 위트 모드)"
    }
}
