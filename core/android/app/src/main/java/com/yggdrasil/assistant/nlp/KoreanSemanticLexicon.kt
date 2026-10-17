package com.yggdrasil.assistant.nlp

import android.content.Context

/**
 * [KoreanSemanticLexicon]
 * 한국어 고유 기초 문법(용언 원형 정규화, 조사 생략 복원) 및
 * 한국인 일상 구어체·축약어 온톨로지 엔진.
 * 0ms 온디바이스 지연으로 구어체 문장을 표준 의미 구조로 정규화한다.
 */
object KoreanSemanticLexicon {

    enum class SemanticAction {
        LAUNCH,     // 실행/켜기 (켜다, 열다, 실행하다, 들어가다, 띄우다)
        SEND,       // 전송 (보내다, 남기다, 쏘다, 전송하다, 쓰다)
        PLAY,       // 재생 (틀다, 재생하다, 듣다, 보다)
        SEARCH,     // 탐색 (찾다, 검색하다, 알아보다, 조사하다)
        CLOSE,      // 종료/끄기 (끄다, 닫다, 종료하다, 나가다)
        NAVIGATE,   // 길찾기 (가다, 안내하다, 이동하다)
        CONTROL_DEVICE, // 기기 제어 (소리, 밝기, 와이파이, 플래시 등)
        SCHEDULE,   // 일정/알람 관리
        GENERAL     // 일반 질의
    }

    data class NormalizedCommand(
        val original: String,
        val normalizedText: String,
        val action: SemanticAction,
        val targetEntity: String,    // 대상 인물, 장소, 목적어 ("엄마", "강남역", "뉴진스")
        val appOrMedium: String,     // 대상 앱/수단 ("카카오톡", "유튜브", "네이버지도")
        val messagePayload: String   // 전달할 메시지 내용 ("10분 뒤 도착")
    )

    // 기본 내장 폴백 사전 (DB 초기화 전이나 컨텍스트 부재 시)
    private val FALLBACK_APP_MAP = mapOf(
        "카톡" to "카카오톡", "톡" to "카카오톡", "페메" to "페이스북메신저",
        "디엠" to "인스타그램", "인스타" to "인스타그램", "유튭" to "유튜브",
        "튜브" to "유튜브", "짤" to "갤러리", "사진" to "갤러리",
        "캡처" to "갤러리", "스크린샷" to "갤러리", "노래" to "유튜브뮤직",
        "음악" to "유튜브뮤직", "택시" to "카카오T", "콜" to "카카오T",
        "네비" to "네이버지도", "지도" to "네이버지도", "길" to "네이버지도",
        "웹" to "크롬", "인터넷" to "크롬"
    )

    private val FALLBACK_VERB_PATTERNS = listOf(
        SemanticAction.LAUNCH to listOf("켜", "열", "실행", "띄워", "들어가", "시작", "구동", "오픈"),
        SemanticAction.SEND to listOf("보내", "남겨", "전송", "날려", "써줘", "말해줘", "답장", "톡해"),
        SemanticAction.PLAY to listOf("틀어", "재생", "들려줘", "보여줘", "듣고", "플레이"),
        SemanticAction.SEARCH to listOf("찾아", "검색", "알려줘", "어디", "얼마", "어때", "조사"),
        SemanticAction.CLOSE to listOf("꺼", "닫", "종료", "치워", "정지", "멈춰", "안볼래"),
        SemanticAction.NAVIGATE to listOf("가자", "안내해", "데려다줘", "길안내", "출발")
    )

    /**
     * 한국어 구어체 문장을 온톨로지 DB 기반으로 정규화
     */
    fun normalize(sentence: String, context: Context? = null): NormalizedCommand {
        var clean = sentence.trim()

        // 1. 온톨로지 DB 참조 (가능 시)
        val ontologyDb = context?.let { KoreanOntologyDatabase.getInstance(it) }
        val aliasMap = ontologyDb?.getAllAliases() ?: FALLBACK_APP_MAP

        // 구어체 축약어 정규화 (긴 단어부터 매칭하여 "유튜브뮤직" 등이 "유튜브"에 먼저 먹히지 않도록 정렬)
        val sortedAliases = aliasMap.keys.sortedByDescending { it.length }
        for (slang in sortedAliases) {
            val standard = aliasMap[slang] ?: continue
            val regex = Regex("(?<![가-힣a-zA-Z0-9])$slang(?![가-힣a-zA-Z0-9])")
            if (regex.containsMatchIn(clean)) {
                clean = clean.replace(regex, standard)
            }
        }

        // 2. 용언 액션 도출
        val detectedAction = detectAction(clean, ontologyDb)

        // 3. 목적어(앱/수단), 대상 인물, 메시지 본문 추출
        var detectedApp = ""
        for ((_, appName) in aliasMap) {
            if (clean.contains(appName)) {
                detectedApp = appName
                break
            }
        }

        // 대상 인물 추출 ("엄마한테", "철수에게", "홍길동방에", "아빠")
        val personRegex = Regex("([가-힣a-zA-Z0-9]+)(에게|한테|방에|께)")
        val personMatch = personRegex.find(clean)
        val targetEntity = personMatch?.groupValues?.get(1) ?: ""

        // 메시지 본문 추출
        var payload = ""
        if (detectedAction == SemanticAction.SEND) {
            val quoteRegex = Regex("['\"](.*?)['\"]")
            val quoteMatch = quoteRegex.find(clean)
            payload = if (quoteMatch != null) {
                quoteMatch.groupValues[1]
            } else if (clean.contains("라고")) {
                clean.substringBefore("라고").substringAfterLast(" ").trim()
            } else if (targetEntity.isNotBlank()) {
                val afterTarget = clean.substringAfter(targetEntity)
                    .replace(Regex("(에게|한테|방에|께|카카오톡|문자|메시지|보내줘|보내|남겨줘|남겨|전송해줘|전송)"), "")
                    .trim()
                afterTarget.ifBlank { "안녕하세요" }
            } else {
                "안녕하세요"
            }
        } else if (detectedAction == SemanticAction.PLAY || detectedAction == SemanticAction.SEARCH) {
            // 검색어/키워드 추출
            payload = clean.replace(Regex("(틀어줘|틀어|재생해줘|재생|검색해줘|검색|찾아줘|찾아|알려줘|유튜브|네이버)"), "").trim()
        }

        return NormalizedCommand(
            original = sentence,
            normalizedText = clean,
            action = detectedAction,
            targetEntity = targetEntity,
            appOrMedium = detectedApp,
            messagePayload = payload
        )
    }

    private fun detectAction(text: String, db: KoreanOntologyDatabase?): SemanticAction {
        if (db != null) {
            val verbStems = db.getAllVerbStems()
            for ((stem, action) in verbStems) {
                if (text.contains(stem)) {
                    return action
                }
            }
        } else {
            for ((action, stems) in FALLBACK_VERB_PATTERNS) {
                for (stem in stems) {
                    if (text.contains(stem)) {
                        return action
                    }
                }
            }
        }
        return SemanticAction.GENERAL
    }
}
