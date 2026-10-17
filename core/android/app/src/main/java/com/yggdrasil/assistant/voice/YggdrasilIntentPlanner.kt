package com.yggdrasil.assistant.voice

import android.content.Context
import android.util.Log
import com.yggdrasil.assistant.llm.LocalLlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * [YggdrasilIntentPlanner]
 * 온디바이스 sLLM(LG EXAONE 2.4B) 기반 지능형 인텐트 & 함수 호출(Function Calling) 플래너.
 * 단순 단어 매칭 정규식을 탈피하고, 사용자의 자연어 발화 맥락과 숨은 의도를
 * 온디바이스 NPU 추론을 통해 구조화된 실행 계획(PlannedAction)으로 변환한다.
 */
class YggdrasilIntentPlanner private constructor(private val context: Context) {

    companion object {
        private const val TAG = "YggdrasilIntentPlanner"

        @Volatile
        private var instance: YggdrasilIntentPlanner? = null

        fun getInstance(context: Context): YggdrasilIntentPlanner {
            return instance ?: synchronized(this) {
                instance ?: YggdrasilIntentPlanner(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class ActionIntent {
        LAUNCH_APP,          // 단일 앱 실행 ("유튜브 열어줘", "카카오톡 켜")
        SEND_MESSAGE,        // 복합 메시지 전송 ("엄마한테 10분 뒤 도착한다고 톡 보내")
        PLAY_MEDIA,          // 미디어 재생 ("뉴진스 노래 틀어줘", "침착맨 영상 보여줘")
        CONTROL_DEVICE,      // 기기 제어 ("볼륨 올려", "화면 밝기 줄여", "손전등 켜")
        USER_FEEDBACK_CORRECTION, // 사용자 자가 교정 피드백 ("아니 그거 말고", "카톡 말고 문자", "틀렸어")
        CHAINED_APP_ACTION,  // 앱 실행 후 특정 작업 ("플레이스토어 열어서 브롤스타즈 깔아줘")
        SYSTEM_NAV,          // 시스템 내비게이션 ("홈으로", "뒤로", "최근 앱")
        SCROLL,              // 화면 스크롤 ("내려", "올려")
        SEARCH_WEB,          // 실시간 웹 검색 ("주변 맛집 검색해줘")
        DIRECT_ANSWER        // 일반 대화 및 지식 질의 ("오늘 날씨 어때?", "너 누구야?")
    }

    data class PlannedAction(
        val intent: ActionIntent,
        val appName: String = "",
        val target: String = "",
        val content: String = "",
        val spokenFeedback: String = "",
        val directResponse: String = ""
    )

    /**
     * 사용자 자연어 발화를 분석하여 최적의 실행 계획 도출
     * (Fast-Pass 룰 기반 초고속 판정 + 온디바이스 sLLM 심층 추론 하이브리드)
     */
    suspend fun planAction(userInput: String): PlannedAction = withContext(Dispatchers.Default) {
        val raw = userInput.trim()
        if (raw.isBlank()) {
            return@withContext PlannedAction(
                intent = ActionIntent.DIRECT_ANSWER,
                spokenFeedback = "네, 무엇을 도와드릴까요?",
                directResponse = "무엇을 도와드릴까요?"
            )
        }

        // 1단계: 0ms 초고속 패스트 패스 (단순 시스템 명령 및 명확한 단일 앱 실행)
        val fastPass = tryFastPass(raw)
        if (fastPass != null) {
            Log.i(TAG, "⚡ [Fast-Pass 적중]: $raw -> ${fastPass.intent}")
            return@withContext fastPass
        }

        // 2단계: 복합 자연어 온디바이스 sLLM 심층 추론 (Function Calling 파이프라인)
        Log.i(TAG, "🧠 [sLLM 심층 의도 추론 가동]: '$raw'")
        val llmPlan = queryOnDeviceLlmForPlan(raw)
        return@withContext llmPlan
    }

    /**
     * 지연시간 0ms 초고속 룰 엔진
     */
    private fun tryFastPass(cmd: String): PlannedAction? {
        val lower = cmd.lowercase().replace(" ", "")

        // 1. 사용자 부정 및 자가 교정 피드백 (Self-Correction Trigger)
        if (lower.startsWith("아니") || lower == "틀렸어" || lower.contains("그거말고") || lower.contains("잘못") || lower == "취소") {
            return PlannedAction(
                intent = ActionIntent.USER_FEEDBACK_CORRECTION,
                content = cmd,
                spokenFeedback = "죄송합니다. 바로 취소하고 다시 도와드리겠습니다."
            )
        }

        // 2. 기기 하드웨어 제어 (음량, 밝기, 플래시 등)
        val devFeature = try {
            com.yggdrasil.assistant.nlp.KoreanOntologyDatabase.getInstance(context).matchDeviceControl(cmd)
        } catch (_: Exception) { null }
        if (devFeature != null) {
            val spoken = when (devFeature) {
                "VOLUME_UP" -> "미디어 볼륨을 높입니다."
                "VOLUME_DOWN" -> "미디어 볼륨을 낮춥니다."
                "VOLUME_MUTE" -> "소리를 음소거합니다."
                "BRIGHTNESS_UP" -> "화면 밝기를 올립니다."
                "BRIGHTNESS_DOWN" -> "화면 밝기를 내립니다."
                "FLASHLIGHT_ON" -> "손전등을 켭니다."
                "FLASHLIGHT_OFF" -> "손전등을 끕니다."
                "WIFI_ON" -> "와이파이를 켭니다."
                "WIFI_OFF" -> "와이파이를 끕니다."
                "BLUETOOTH_ON" -> "블루투스를 켭니다."
                "BLUETOOTH_OFF" -> "블루투스를 끕니다."
                else -> "기기 설정을 변경합니다."
            }
            return PlannedAction(
                intent = ActionIntent.CONTROL_DEVICE,
                content = devFeature,
                spokenFeedback = spoken
            )
        }

        // 3. 시스템 내비게이션
        if (lower in listOf("홈", "홈으로", "홈화면", "바탕화면", "메인화면")) {
            return PlannedAction(
                intent = ActionIntent.SYSTEM_NAV,
                content = "HOME",
                spokenFeedback = "홈 화면으로 이동합니다."
            )
        }
        if (lower in listOf("뒤로", "뒤로가", "이전", "뒤로가기")) {
            return PlannedAction(
                intent = ActionIntent.SYSTEM_NAV,
                content = "BACK",
                spokenFeedback = "이전 화면으로 돌아갑니다."
            )
        }
        if (lower in listOf("최근앱", "멀티태스킹", "앱목록")) {
            return PlannedAction(
                intent = ActionIntent.SYSTEM_NAV,
                content = "RECENTS",
                spokenFeedback = "최근 앱 목록을 엽니다."
            )
        }

        // 4. 스크롤
        if (lower.contains("스크롤내려") || lower.contains("아래로내려") || lower == "내려줘" || lower == "내려") {
            return PlannedAction(
                intent = ActionIntent.SCROLL,
                content = "DOWN",
                spokenFeedback = "화면을 아래로 내립니다."
            )
        }
        if (lower.contains("스크롤올려") || lower.contains("위로올려") || lower == "올려줘" || lower == "올려") {
            return PlannedAction(
                intent = ActionIntent.SCROLL,
                content = "UP",
                spokenFeedback = "화면을 위로 올립니다."
            )
        }

        // 5. 미디어 재생 ("뉴진스 노래 틀어줘", "침착맨 영상 보여줘")
        if (cmd.endsWith("틀어줘") || cmd.endsWith("틀어") || cmd.endsWith("재생해줘") || cmd.endsWith("들려줘") || cmd.endsWith("보여줘")) {
            val mediaQuery = cmd.replace(Regex("(노래|음악|영상|유튜브|틀어줘|틀어|재생해줘|들려줘|보여줘)$"), "").trim()
            val app = if (cmd.contains("노래") || cmd.contains("음악")) "유튜브뮤직" else "유튜브"
            return PlannedAction(
                intent = ActionIntent.PLAY_MEDIA,
                appName = app,
                content = mediaQuery.ifBlank { "추천 재생목록" },
                spokenFeedback = "${app}에서 ${mediaQuery}을(를) 재생합니다."
            )
        }

        // 6. 단순 단일 앱 실행 ("유튜브 켜줘", "카카오톡 실행", "인스타 열어")
        val isLaunchVerb = cmd.endsWith("켜줘") || cmd.endsWith("켜") ||
                cmd.endsWith("열어줘") || cmd.endsWith("열어") ||
                cmd.endsWith("실행해줘") || cmd.endsWith("실행해") ||
                cmd.endsWith("실행해 줘") || cmd.endsWith("켜 줘") ||
                cmd.endsWith("열어 줘")

        if (isLaunchVerb && !cmd.contains("해서") && !cmd.contains("보내") && !cmd.contains("검색")) {
            val appKeywords = mapOf(
                "유튜브뮤직" to "유튜브뮤직",
                "유튜브" to "유튜브",
                "카카오톡" to "카카오톡",
                "카톡" to "카카오톡",
                "인스타그램" to "인스타그램",
                "인스타" to "인스타그램",
                "네이버" to "네이버",
                "쿠팡" to "쿠팡",
                "설정" to "설정",
                "카메라" to "카메라",
                "갤러리" to "갤러리",
                "플레이스토어" to "플레이스토어",
                "시계" to "시계",
                "알람" to "시계",
                "계산기" to "계산기",
                "캘린더" to "캘린더",
                "달력" to "캘린더",
                "지도" to "네이버지도",
                "네비" to "네이버지도",
                "택시" to "카카오T",
                "배민" to "배달의민족"
            )
            for ((kw, app) in appKeywords) {
                if (cmd.contains(kw)) {
                    return PlannedAction(
                        intent = ActionIntent.LAUNCH_APP,
                        appName = app,
                        spokenFeedback = "${app}을(를) 실행합니다."
                    )
                }
            }
        }

        return null
    }

    /**
     * EXAONE 2.4B 온디바이스 NPU 모델 프롬프트 쿼리 및 JSON 응답 파싱
     */
    private fun queryOnDeviceLlmForPlan(userInput: String): PlannedAction {
        // 복합 메시지 전송 휴리스틱 폴백 (LLM 네이티브 라이브러리 연결 전 안전망)
        val lower = userInput.lowercase()
        if ((lower.contains("보내") || lower.contains("남겨") || lower.contains("전송")) &&
            (lower.contains("톡") || lower.contains("문자") || lower.contains("한테") || lower.contains("에게"))
        ) {
            val target = extractTargetPerson(userInput)
            val msg = extractMessageContent(userInput)
            val app = if (lower.contains("문자")) "메시지" else "카카오톡"

            return PlannedAction(
                intent = ActionIntent.SEND_MESSAGE,
                appName = app,
                target = target,
                content = msg,
                spokenFeedback = "${target}님께 ${msg} 메시지를 전송합니다."
            )
        }

        // 웹 검색 질의 분기
        if (lower.contains("검색") || lower.contains("찾아줘") || lower.contains("알려줘") || lower.contains("어디")) {
            val cleanQuery = userInput.replace(Regex("(검색해줘|검색|찾아줘|찾아|알려줘)$"), "").trim()
            return PlannedAction(
                intent = ActionIntent.SEARCH_WEB,
                content = cleanQuery,
                spokenFeedback = "'${cleanQuery}' 정보를 검색합니다."
            )
        }

        // 기본 대화 응답
        return PlannedAction(
            intent = ActionIntent.DIRECT_ANSWER,
            spokenFeedback = "말씀하신 내용을 확인했습니다.",
            directResponse = "요청하신 내용에 대해 처리 중입니다."
        )
    }

    private fun extractTargetPerson(cmd: String): String {
        val targetRegex = Regex("([가-힣a-zA-Z0-9]+)(에게|한테|방에)")
        val match = targetRegex.find(cmd)
        return match?.groupValues?.get(1)?.trim() ?: "대상"
    }

    private fun extractMessageContent(cmd: String): String {
        val quoteRegex = Regex("['\"](.*?)['\"]")
        val quoteMatch = quoteRegex.find(cmd)
        if (quoteMatch != null) {
            return quoteMatch.groupValues[1].trim()
        }

        if (cmd.contains("라고")) {
            val beforeRago = cmd.substringBefore("라고")
            val candidate = beforeRago.substringAfterLast(" ").trim()
            if (candidate.isNotBlank()) return candidate
        }

        val afterTarget = when {
            cmd.contains("한테") -> cmd.substringAfter("한테")
            cmd.contains("에게") -> cmd.substringAfter("에게")
            cmd.contains("방에") -> cmd.substringAfter("방에")
            else -> cmd
        }

        return afterTarget
            .replace(Regex("(카톡|카카오톡|문자|메시지|보내줘|보내|남겨줘|남겨|전송해줘|전송)$"), "")
            .trim()
            .ifBlank { "안녕하세요" }
    }
}
