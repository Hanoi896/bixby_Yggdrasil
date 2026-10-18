package com.yggdrasil.assistant.mcp

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * [RobustJsonSanitizer]
 * 2.4B 소형 온디바이스 모델의 문법 파손 JSON을 실시간 자동 교정하는 복구 엔진.
 * 마크다운 백틱, 후행 쉼표, 따옴표 탈락, 줄바꿈 파손을 복원하여 파싱 크래시를 방지합니다.
 */
object RobustJsonSanitizer {

    private val JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}")
    private val TRAILING_COMMA_PATTERN = Pattern.compile(",\\s*([}\\]])")

    /**
     * NPU 원시 출력에서 JSON 객체를 추출하고 정제
     */
    fun sanitize(rawOutput: String): String? {
        var text = rawOutput.trim()

        // 1. 마크다운 ```json ... ``` 태그 제거
        if (text.contains("```")) {
            text = text.replace("```json", "").replace("```", "").trim()
        }

        // 2. 가장 외곽의 { ... } 블록 추출
        val matcher = JSON_BLOCK_PATTERN.matcher(text)
        if (!matcher.find()) {
            return null
        }
        var candidate = matcher.group(0) ?: return null

        // 3. 후행 쉼표 제거 (예: {"a": 1, })
        candidate = TRAILING_COMMA_PATTERN.matcher(candidate).replaceAll("$1")

        // 4. 작은따옴표를 큰따옴표로 교체
        if (!candidate.contains("\"") && candidate.contains("'")) {
            candidate = candidate.replace("'", "\"")
        }

        return candidate
    }

    /**
     * 도구 호출 JSON을 안전하게 파싱
     */
    fun parseToolCall(rawOutput: String): Pair<String, Map<String, Any?>>? {
        val sanitized = sanitize(rawOutput) ?: return null
        return try {
            val json = JSONObject(sanitized)
            val toolName = json.optString("tool", "")
            if (toolName.isBlank()) return null

            val paramsMap = mutableMapOf<String, Any?>()
            val paramsObj = json.optJSONObject("params")
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    paramsMap[key] = paramsObj.get(key)
                }
            }
            toolName to paramsMap
        } catch (_: Exception) {
            null
        }
    }
}
