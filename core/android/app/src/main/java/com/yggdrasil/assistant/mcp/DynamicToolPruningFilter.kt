package com.yggdrasil.assistant.mcp

/**
 * [DynamicToolPruningFilter]
 * 4096 토큰 제한을 갖는 온디바이스 2.4B 모델을 위한 동적 도구 선별기 (Tool Pruning Filter).
 * 등록된 수십 개의 MCP 도구 스키마를 전부 주입하지 않고,
 * 사용자의 발화와 연관도가 높은 상위 2~3개 도구만 선별하여 300토큰 이내로 주입합니다.
 */
object DynamicToolPruningFilter {

    private const val MAX_TOOLS_TO_INJECT = 3

    /**
     * 사용자 발화와 연관된 최적의 도구 스키마 프롬프트 추출
     */
    fun pruneAndBuildToolPrompt(userQuery: String, registry: LocalMcpRegistry = LocalMcpRegistry.getInstance()): String {
        val queryLower = userQuery.lowercase()
        val allTools = registry.getAllTools()

        // 발화 내 키워드 매칭 스코어링
        val scored = allTools.map { tool ->
            var score = 0
            if (queryLower.contains(tool.name.lowercase())) score += 5
            for (kw in tool.keywords) {
                if (queryLower.contains(kw.lowercase())) {
                    score += 3
                }
            }
            tool to score
        }

        // 스코어가 1 이상인 도구 중 상위 3개 선별 (없으면 기본 도구 1개)
        val selected = scored
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(MAX_TOOLS_TO_INJECT)

        if (selected.isEmpty()) {
            return "" // 도구 호출이 필요 없는 일반 대화는 토큰 0개 소모
        }

        val sb = StringBuilder()
        sb.append("[사용 가능한 온디바이스 도구]\n")
        for (tool in selected) {
            sb.append("- ").append(tool.schemaJson).append("\n")
        }
        sb.append("도구가 필요할 경우 {\"tool\":\"<이름>\",\"params\":{...}} 형식으로만 답하십시오.\n")
        return sb.toString()
    }
}
