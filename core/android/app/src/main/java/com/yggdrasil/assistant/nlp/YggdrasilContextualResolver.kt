package com.yggdrasil.assistant.nlp

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.yggdrasil.assistant.operator.YggdrasilAccessibilityService

/**
 * [YggdrasilContextualResolver]
 * 시각(화면 노드) + 장기기억(Munin RAG) 맥락 결합기.
 * "그거 보내줘", "거기 전화해줘", "이 사람한테 답장해" 등
 * 대명사(Anaphora) 및 생략어(Ellipsis)를 실시간 화면과 대화 기억에서 찾아 복원한다.
 */
class YggdrasilContextualResolver private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ContextualResolver"

        @Volatile
        private var instance: YggdrasilContextualResolver? = null

        fun getInstance(context: Context): YggdrasilContextualResolver {
            return instance ?: synchronized(this) {
                instance ?: YggdrasilContextualResolver(context.applicationContext).also { instance = it }
            }
        }

        // 지시 대명사 목록
        private val PRONOUNS = listOf(
            "그거", "이거", "저거", "그것", "이것", "저것",
            "거기", "여기", "저기",
            "이 사람", "그 사람", "이분", "그분", "걔",
            "방금 그거", "아까 그거", "그 앱"
        )
        // 오발화(Discourse Repair) 트리거 패턴 목록
        private val REPAIR_PATTERNS = listOf(
            "아니 그거 말고", "아니 그것 말고", "아니 말고", "그거 말고", "그것 말고",
            "아니 취소하고", "취소하고", "그게 아니라", "아니 그게 아니고", "말고 다시", "다시 해줘"
        )
    }

    // 최근 대화 맥락 메모리 (최대 5개 엔티티 유지)
    private val recentContextEntities = java.util.concurrent.ConcurrentLinkedDeque<String>()
    @Volatile
    private var lastExecutedCommand: String? = null

    fun recordLastCommand(command: String) {
        lastExecutedCommand = command.trim()
    }

    /**
     * 후속 대화 맥락 복원을 위해 추출된 주요 엔티티(대상, 앱 등)를 맥락 메모리에 기록
     */
    fun recordEntity(entity: String, type: String = "") {
        if (entity.isBlank()) return
        recentContextEntities.addFirst(entity.trim())
        while (recentContextEntities.size > 5) {
            recentContextEntities.pollLast()
        }
    }

    /**
     * 오발화 복원(Discourse Fallback)
     * 사용자가 말을 더듬거나 도중에 바꿀 때 ("아니 그거 말고 엄마한테 카톡으로")
     * 직전 명령의 엔티티(대상, 내용)를 유지하며 새로 정정한 부분만 정밀 치환 복원
     */
    fun resolveDiscourseRepair(sentence: String): String {
        val trimmed = sentence.trim()
        val repairTrigger = REPAIR_PATTERNS.firstOrNull { trimmed.startsWith(it) || trimmed.contains(it) }
            ?: return resolveContext(trimmed)

        val previous = lastExecutedCommand
        if (previous.isNullOrBlank()) {
            val stripped = trimmed.replace(repairTrigger, "").trim()
            return resolveContext(stripped)
        }

        Log.i(TAG, "🗣️ [오발화/말바꿈 담화 복원 감지]: '$trimmed' (직전 발화: '$previous')")

        // 수신인/앱/내용 분석
        val prevApp = when {
            previous.contains("카톡") || previous.contains("카카오톡") -> "카카오톡"
            previous.contains("문자") || previous.contains("메시지") -> "문자"
            previous.contains("텔레그램") -> "텔레그램"
            previous.contains("라인") -> "라인"
            previous.contains("유튜브") -> "유튜브"
            previous.contains("네이버") -> "네이버"
            else -> ""
        }

        // 대상 인물(방) 추출 ("엄마한테", "홍길동에게" 등)
        val targetRegex = Regex("([가-힣a-zA-Z0-9]+)(한테|에게|방에)")
        val prevTargetMatch = targetRegex.find(previous)
        val prevTarget = prevTargetMatch?.value ?: ""

        // 따옴표나 본문 내용 추출
        val prevContent = if (previous.contains("'") || previous.contains("\"")) {
            Regex("['\"](.*?)['\"]").find(previous)?.value ?: ""
        } else ""

        val newIntentPart = trimmed.replace(repairTrigger, "").trim()

        // 새 발화에 새로운 대상이 있으면 교체, 없으면 직전 대상 계승
        val newTargetMatch = targetRegex.find(newIntentPart)
        val effectiveTarget = newTargetMatch?.value ?: prevTarget

        // 새 발화에 새로운 앱이 있으면 교체, 없으면 직전 앱 계승
        val newApp = when {
            newIntentPart.contains("카톡") || newIntentPart.contains("카카오톡") -> "카카오톡"
            newIntentPart.contains("문자") || newIntentPart.contains("메시지") -> "문자"
            newIntentPart.contains("텔레그램") -> "텔레그램"
            newIntentPart.contains("라인") -> "라인"
            else -> prevApp
        }

        // 새 발화에 별도 메시지 내용이 없으면 직전 메시지 계승
        val resolved = StringBuilder()
        if (effectiveTarget.isNotBlank()) {
            resolved.append(effectiveTarget).append(" ")
        }
        if (newApp.isNotBlank()) {
            resolved.append(newApp).append(" ")
        }
        if (prevContent.isNotBlank() && !newIntentPart.contains("'") && !newIntentPart.contains("\"")) {
            resolved.append(prevContent).append(" ")
        }
        resolved.append(newIntentPart.replace(effectiveTarget, "").replace(newApp, "").trim())

        val resultText = resolved.toString().replace(Regex("\\s+"), " ").trim()
        Log.i(TAG, "🎯 [오발화 복원 완료]: '$trimmed' -> '$resultText'")
        return resultText.ifBlank { resolveContext(trimmed) }
    }

    /**
     * 문장에 대명사가 포함되어 있는지 확인하고, 화면 및 기억에서 복원
     */
    fun resolveContext(sentence: String): String {
        var resolved = sentence.trim()

        val hasPronoun = PRONOUNS.any { resolved.contains(it) }
        if (!hasPronoun) {
            return resolved
        }

        Log.i(TAG, "🔍 [지시 대명사 감지]: '$sentence' -> 맥락 복원 시작")

        // 1순위: 현재 활성 스마트폰 화면의 시각적 엔티티 추출
        val screenEntity = extractScreenSalientEntity()
        if (screenEntity.isNotBlank()) {
            for (p in PRONOUNS) {
                if (resolved.contains(p)) {
                    resolved = resolved.replace(p, screenEntity)
                    Log.i(TAG, "👁️ [화면 시각 맥락 복원 성공]: '$p' -> '$screenEntity'")
                    return resolved
                }
            }
        }

        // 2순위: 최근 대화 기억(Munin/세션 메모리)에서 직전 엔티티 복원
        val memoryEntity = recentContextEntities.peekFirst()
        if (!memoryEntity.isNullOrBlank()) {
            for (p in PRONOUNS) {
                if (resolved.contains(p)) {
                    resolved = resolved.replace(p, memoryEntity)
                    Log.i(TAG, "🧠 [대화 장기기억 맥락 복원 성공]: '$p' -> '$memoryEntity'")
                    return resolved
                }
            }
        }

        return resolved
    }

    /**
     * 현재 화면(포그라운드)에서 가장 의미 있는 콘텐츠 텍스트/제목 추출
     * (상태바/네비바/시스템 UI 텍스트 완전 배제 및 화면 중앙 콘텐츠 영역 우선 채택)
     */
    private fun extractScreenSalientEntity(): String {
        val service = YggdrasilAccessibilityService.getInstance() ?: return ""
        val root = service.rootInActiveWindow ?: return ""

        try {
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            collectVisibleNodes(root, allNodes)

            // 시스템 UI 및 무의미한 단어 블랙리스트
            val blacklist = setOf(
                "확인", "취소", "닫기", "검색", "뒤로", "전송", "메뉴", "설정", "더보기",
                "홈", "피드", "프로필", "채팅", "친구", "알림", "보관함", "스토어",
                "skt", "kt", "lgu+", "u+", "알뜰폰", "wifi", "lte", "5g", "배터리"
            )

            // 1순위: 현재 포커스되거나 선택된 콘텐츠 노드
            val focusedNode = allNodes.firstOrNull { it.isFocused || it.isSelected }
            if (focusedNode != null) {
                val fText = focusedNode.text?.toString()?.trim() ?: ""
                if (fText.length in 2..20 && !blacklist.contains(fText.lowercase())) {
                    allNodes.forEach { try { it.recycle() } catch (_: Exception) {} }
                    return fText
                }
            }

            // 2순위: 상태바(Y < 120px) 및 네비바를 회피한 실제 콘텐츠 영역의 주요 텍스트
            val rect = android.graphics.Rect()
            for (node in allNodes) {
                node.getBoundsInScreen(rect)
                val text = node.text?.toString()?.trim() ?: ""
                val lower = text.lowercase()

                // 상단 상태바(Y < 100) 및 하단 제스처바 배제
                if (rect.top < 100 || rect.bottom > 2200) continue

                if (text.length in 2..20 &&
                    !text.contains(":") &&
                    !text.contains("%") &&
                    !text.matches(Regex("^[0-9]+$")) &&
                    !blacklist.contains(lower)
                ) {
                    allNodes.forEach { try { it.recycle() } catch (_: Exception) {} }
                    return text
                }
            }
            allNodes.forEach { try { it.recycle() } catch (_: Exception) {} }
        } catch (e: Exception) {
            Log.w(TAG, "화면 엔티티 추출 예외", e)
        }
        return ""
    }

    private fun collectVisibleNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isVisibleToUser) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectVisibleNodes(child, list)
            }
        }
    }
}
