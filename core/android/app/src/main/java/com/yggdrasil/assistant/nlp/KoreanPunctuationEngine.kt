package com.yggdrasil.assistant.nlp

import java.util.regex.Pattern

/**
 * [KoreanPunctuationEngine]
 * 사용자 발화 및 시스템 발화에 큰따옴표(""), 쉼표(,), 물음표(?), 마침표(.) 등
 * 한국어 문맥에 최적화된 문장 부호를 자연스럽게 자동 삽입/정규화하는 엔진.
 */
object KoreanPunctuationEngine {

    // 호칭 및 부름말 목록
    private val CALLING_TITLES = listOf(
        "엄마", "아빠", "형", "누나", "오빠", "언니", "동생",
        "선생님", "사장님", "팀장님", "과장님", "부장님", "교수님",
        "여보", "자기야", "친구야", "아들", "딸", "어머니", "아버지"
    )

    // 접속 부사 목록
    private val CONJUNCTIVE_ADVERBS = listOf(
        "그리고", "그런데", "하지만", "그러나", "그러므로", "따라서", "즉", "또한", "더불어", "한편"
    )

    // 의문형 종결 패턴
    private val QUESTION_ENDINGS = listOf(
        "어디야", "어디에 있어", "어디있어", "어디니", "몇 시야", "몇시야", "몇 분이야",
        "누구야", "누구니", "무엇이야", "뭐야", "뭐니", "어때", "어떠니",
        "있을까", "할까", "될까", "맞아", "맞니", "줄래", "해줄래", "알려줄래",
        "있나요", "인가요", "할까요", "되나요", "어딨어"
    )

    /**
     * 사용자의 입력 텍스트(음성 STT 또는 텍스트 입력)를 종합 정규화:
     * 1. 영타/외래어 변환 (KoreanKeyboardAutomata)
     * 2. 인용구 큰따옴표("") 자동 부착
     * 3. 호칭/접속부사 쉼표(,) 자연스러운 삽입
     * 4. 의문/종결 부호(?, .) 자동 보완
     */
    fun enrichUserUtterance(rawText: String): String {
        if (rawText.isBlank()) return rawText

        // 1. 영타 오타 및 영문 외래어 한국어 변환
        var processed = KoreanKeyboardAutomata.convertEnglishToKorean(rawText.trim())

        // 2. 인용 조사("이라고", "라고", "하고", "이라며") 앞 인용구에 큰따옴표("") 자동 삽입
        processed = enrichQuoteMarks(processed)

        // 3. 쉼표(,) 자연스러운 부착 (호칭, 접속부사)
        processed = enrichCommas(processed)

        // 4. 문장 끝 종결 부호(?, .) 자동 보완
        processed = enrichSentenceTerminators(processed)

        // 5. 연속 기호 및 중복 공백 정돈
        processed = cleanupFormatting(processed)

        return processed
    }

    /**
     * 인용 조사("이라고/라고/하고") 앞의 메시지 본문에 큰따옴표("") 자연스러운 삽입
     * 예: "카톡에서 나에게 안녕이라고 문자 보내줘" -> "카톡에서 나에게 \"안녕\"이라고 문자 보내줘"
     * 예: "엄마한테 10분 뒤 도착이라고 카톡해줘" -> "엄마한테 \"10분 뒤 도착\"이라고 카톡해줘"
     */
    fun enrichQuoteMarks(text: String): String {
        // 이미 따옴표(" 또는 ')가 있으면 중복 삽입하지 않음
        if (text.contains("\"") || text.contains("'")) {
            return text
        }

        val quoteMarkers = listOf("이라고", "라고", "하고", "이라며")
        for (marker in quoteMarkers) {
            if (text.contains(marker)) {
                val beforeMarker = text.substringBefore(marker)
                val afterMarker = text.substringAfter(marker)

                // 인용문 앞부분 분리 ("카톡에서 나에게" / "안녕")
                val splitRegex = Regex("^(.*?(?:에게|한테|방에|께|에서|카카오톡|카톡|문자|메시지|앱|전화|LINE|라인)\\s+)(.+)$")
                val match = splitRegex.find(beforeMarker)

                return if (match != null) {
                    val prefix = match.groupValues[1]
                    val quoteBody = match.groupValues[2].trim()
                    if (quoteBody.isNotBlank()) {
                        "$prefix\"$quoteBody\"$marker$afterMarker"
                    } else {
                        text
                    }
                } else {
                    // prefix가 명확치 않은 경우 마지막 단어/구절을 인용문으로 처리
                    val trimmed = beforeMarker.trim()
                    if (trimmed.contains(" ")) {
                        val prefix = trimmed.substringBeforeLast(" ") + " "
                        val quoteBody = trimmed.substringAfterLast(" ").trim()
                        "$prefix\"$quoteBody\"$marker$afterMarker"
                    } else if (trimmed.isNotBlank()) {
                        "\"$trimmed\"$marker$afterMarker"
                    } else {
                        text
                    }
                }
            }
        }
        return text
    }

    /**
     * 호칭 및 접속 부사 뒤에 쉼표(,) 자연스럽게 삽입
     * 예: "엄마 안녕" -> "엄마, 안녕"
     * 예: "그리고 내가 말이야" -> "그리고, 내가 말이야"
     */
    fun enrichCommas(text: String): String {
        var res = text

        // 1. 호칭 뒤 쉼표
        for (title in CALLING_TITLES) {
            val titleRegex = Regex("(?<![가-힣a-zA-Z0-9])$title\\s+([가-힣a-zA-Z0-9])")
            if (titleRegex.containsMatchIn(res)) {
                res = res.replace(titleRegex, "$title, $1")
            }
        }

        // 2. 접속 부사 뒤 쉼표
        for (adverb in CONJUNCTIVE_ADVERBS) {
            val advRegex = Regex("(?<![가-힣a-zA-Z0-9])$adverb\\s+([가-힣a-zA-Z0-9])")
            if (advRegex.containsMatchIn(res)) {
                res = res.replace(advRegex, "$adverb, $1")
            }
        }

        return res
    }

    /**
     * 문장 끝 종결 부호(?, .) 자동 보완
     * 예: "지금 몇 시야" -> "지금 몇 시야?"
     * 예: "내 위치 어디야" -> "내 위치 어디야?"
     */
    fun enrichSentenceTerminators(text: String): String {
        val trimmed = text.trim()
        if (trimmed.endsWith("?") || trimmed.endsWith("!") || trimmed.endsWith(".")) {
            return trimmed
        }

        // 의문문 패턴 검사
        for (q in QUESTION_ENDINGS) {
            if (trimmed.endsWith(q)) {
                return "$trimmed?"
            }
        }

        return trimmed
    }

    /**
     * 기호 중복 정리 (",," -> ",", "  " -> " ")
     */
    private fun cleanupFormatting(text: String): String {
        return text
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\?\\s*\\?"), "?")
            .replace(Regex("!\\s*!"), "!")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * AI 답변 또는 출력 메시지에 따옴표와 쉼표를 정돈하여 표시
     */
    fun formatDisplayMessage(message: String): String {
        return cleanupFormatting(enrichCommas(sanitizeAiResponse(message)))
    }

    /**
     * NPU sLLM 모델이 Function Calling용 JSON(예: {"action": "...", "spokenFeedback": "..."})으로
     * 응답을 생성했을 때, 기계식 JSON 메타데이터를 완전히 벗겨내고 순수 한국어 대화 본문만 추출하여 사용자에게 표시.
     */
    fun sanitizeAiResponse(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return trimmed

        // 1. JSON 구조체 감지 및 내부 텍스트 추출 시도
        val jsonCandidate = if (trimmed.contains("{") && trimmed.contains("}")) {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            trimmed.substring(start, end + 1)
        } else null

        if (jsonCandidate != null) {
            try {
                val obj = org.json.JSONObject(jsonCandidate)
                // 도구 호출(Tool Call) JSON 구조체 감지 시 자연어로 변환
                if (obj.has("tool")) {
                    val toolName = obj.optString("tool", "")
                    return "⚙️ '$toolName' 도구를 실행합니다."
                }

                val spoken = obj.optString("spokenFeedback", "").ifBlank {
                    obj.optString("content", "").ifBlank {
                        obj.optString("message", "").ifBlank {
                            obj.optString("answer", "").ifBlank {
                                obj.optString("response", "").ifBlank {
                                    obj.optString("text", "")
                                }
                            }
                        }
                    }
                }
                if (spoken.isNotBlank()) {
                    return spoken.trim()
                }
            } catch (_: Exception) {
                // 파싱 실패 시 정규식 폴백 진행
            }
        }

        // 2. 정규식 기반 "spokenFeedback" 또는 "content" 추출
        val spokenRegex = Regex("\"(?:spokenFeedback|content|message|answer|response)\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
        val match = spokenRegex.find(trimmed)
        if (match != null) {
            val content = match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", " ")
                .trim()
            if (content.isNotBlank()) return content
        }

        // 3. 마크다운 코드 블록(```json, ```) 껍데기 제거
        var cleaned = trimmed
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```$"), "")
            .trim()

        // 4. 만약 앞뒤에 남아있는 { ... } 껍데기가 있다면 벗겨냄
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }

        return cleaned
    }
}
