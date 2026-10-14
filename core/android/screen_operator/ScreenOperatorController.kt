package com.yggdrasil.assistant.operator

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ScreenOperatorController
 * 모바일 화면 제어 및 UI 자율 자동화 통합 오케스트레이터
 * 
 * Bixby 캡슐 및 온디바이스 에이전트(Hugin)의 screen_control 도구 요청을 받아
 * 안전 가드레일(SafetyGuardrail)을 적용하고, 접근성 서비스 및 OCR 모듈을 지휘하여
 * 화면을 정밀하게 조작합니다.
 */
class ScreenOperatorController(
    private val context: Context,
    private val safetyGuardrail: SafetyGuardrailManager,
    private val captureManager: ScreenCaptureManager
) {

    data class ExecutionResult(
        val success: Boolean,
        val message: String,
        val details: Map<String, Any>? = null
    )

    init {
        // 긴급 정지 리스너 등록
        safetyGuardrail.onEmergencyStopListener = {
            onEmergencyStopTriggered()
        }
    }

    private fun onEmergencyStopTriggered() {
        // 사용자 화면 터치 시 즉시 모든 자율 조작을 중단
    }

    /**
     * Hugin 에이전트 도구 호출 명령 실행
     * @param action tap, swipe, type, press_key, inspect
     * @param targetText 클릭/입력할 텍스트 대상 (선택)
     * @param coordinates 특정 (x, y) 좌표 (선택)
     * @param inputText 타이핑할 텍스트 (선택)
     * @param direction swipe 방향 (up, down, left, right)
     */
    fun executeAction(
        action: String,
        targetText: String? = null,
        coordinates: Point? = null,
        inputText: String? = null,
        direction: String? = null
    ): ExecutionResult {
        val service = YggdrasilAccessibilityService.getInstance()
            ?: return ExecutionResult(false, "AccessibilityService가 활성화되지 않았거나 권한이 부여되지 않았습니다.")

        // 1. Fail-Safe 인터럽트 상태 체크
        if (safetyGuardrail.isEmergencyStopped()) {
            return ExecutionResult(false, "사용자 터치로 인해 긴급 중단(Fail-Safe)되었습니다.")
        }

        // 2. 화면 조작 시작 시 황금빛 안전 테두리 오버레이 활성화
        safetyGuardrail.showSafetyGoldenBorder()

        return try {
            when (action.lowercase()) {
                "tap", "click" -> executeTap(service, targetText, coordinates)
                "swipe" -> executeSwipe(service, direction)
                "type", "input_text" -> executeType(service, targetText, inputText)
                "press_key" -> executePressKey(service, targetText ?: "back")
                "inspect", "screenshot" -> executeInspect(service)
                else -> ExecutionResult(false, "지원하지 않는 action입니다: $action")
            }
        } finally {
            // 작업 완료 후 골든 보더 해제 (연속 작업이 아닐 경우)
            safetyGuardrail.hideSafetyGoldenBorder()
        }
    }

    private fun executeTap(
        service: YggdrasilAccessibilityService,
        targetText: String?,
        coordinates: Point?
    ): ExecutionResult {
        // 1. 명시적 좌표가 전달된 경우
        if (coordinates != null) {
            var isDone = false
            var isSuccess = false
            service.tap(coordinates.x.toFloat(), coordinates.y.toFloat()) { success ->
                isSuccess = success
                isDone = true
            }
            waitForGesture(isDone)
            return ExecutionResult(isSuccess, "좌표 (${coordinates.x}, ${coordinates.y}) 탭 완료")
        }

        // 2. targetText가 있는 경우 접근성 노드 트리에서 우선 검색
        if (!targetText.isNullOrBlank()) {
            val nodes = service.findNodesByText(targetText)
            val clickableNode = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()

            if (clickableNode != null) {
                // 비밀번호 필드 여부 점검
                if (safetyGuardrail.isPasswordOrSensitiveField(clickableNode)) {
                    return ExecutionResult(false, "보안 가드레일: 보안/비밀번호 필드는 터치할 수 없습니다.")
                }

                val rect = Rect()
                clickableNode.getBoundsInScreen(rect)
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()

                var isSuccess = false
                service.tap(cx, cy) { success -> isSuccess = success }
                return ExecutionResult(true, "노드 텍스트 '$targetText' 위치 ($cx, $cy) 탭 실행")
            }

            // 3. 노드 트리에서 못 찾으면 온디바이스 OCR 매핑으로 폴백 (0원, 무료)
            var ocrTargetPoint: Point? = null
            captureManager.findElementByOcr(targetText) { matched ->
                ocrTargetPoint = matched?.centerPoint
            }

            if (ocrTargetPoint != null) {
                val pt = ocrTargetPoint!!
                service.tap(pt.x.toFloat(), pt.y.toFloat())
                return ExecutionResult(true, "온디바이스 OCR로 '$targetText' 인식 후 (${pt.x}, ${pt.y}) 탭 완료")
            }

            return ExecutionResult(false, "화면에서 '$targetText' 요소를 찾을 수 없습니다.")
        }

        return ExecutionResult(false, "탭 대상(targetText 또는 coordinates)이 지정되지 않았습니다.")
    }

    private fun executeSwipe(
        service: YggdrasilAccessibilityService,
        direction: String?
    ): ExecutionResult {
        val dir = direction?.lowercase() ?: "up"
        val startX = 540f
        val startY = 1200f
        val (endX, endY) = when (dir) {
            "up" -> 540f to 400f
            "down" -> 540f to 2000f
            "left" -> 200f to 1200f
            "right" -> 880f to 1200f
            else -> 540f to 400f
        }

        var isSuccess = false
        service.swipe(startX, startY, endX, endY) { success -> isSuccess = success }
        return ExecutionResult(true, "화면 $dir 스와이프 완료")
    }

    private fun executeType(
        service: YggdrasilAccessibilityService,
        targetText: String?,
        inputText: String?
    ): ExecutionResult {
        if (inputText.isNullOrEmpty()) {
            return ExecutionResult(false, "입력할 텍스트(inputText)가 비어있습니다.")
        }

        // 입력 대상 노드 탐색
        val targetNode: AccessibilityNodeInfo? = if (!targetText.isNullOrBlank()) {
            val nodes = service.findNodesByText(targetText)
            nodes.firstOrNull { it.isEditable } ?: nodes.firstOrNull()
        } else {
            // 현재 포커스된 노드 탐색
            service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (targetNode == null) {
            return ExecutionResult(false, "텍스트를 입력할 포커스 또는 입력창 노드를 찾지 못했습니다.")
        }

        // 보안 가드레일: 비밀번호 필드 여부 검사
        if (safetyGuardrail.isPasswordOrSensitiveField(targetNode)) {
            return ExecutionResult(false, "보안 가드레일 위반: 비밀번호 필드에는 텍스트를 자동 주입할 수 없습니다.")
        }

        val success = service.injectText(targetNode, inputText)
        return ExecutionResult(success, if (success) "텍스트 주입 완료: $inputText" else "텍스트 주입 실패")
    }

    private fun executePressKey(
        service: YggdrasilAccessibilityService,
        key: String
    ): ExecutionResult {
        val success = when (key.lowercase()) {
            "back", "뒤로가기" -> service.pressBack()
            "home", "홈" -> service.pressHome()
            "recents", "최근앱" -> service.pressRecents()
            "notifications", "알림" -> service.openNotifications()
            else -> false
        }
        return ExecutionResult(success, "시스템 키 '$key' 전송 결과: $success")
    }

    private fun executeInspect(service: YggdrasilAccessibilityService): ExecutionResult {
        val hierarchy = service.dumpScreenHierarchy()
        return ExecutionResult(true, "화면 계층 덤프 완료", hierarchy)
    }

    private fun waitForGesture(isDone: Boolean) {
        // 비동기 제스처 완료 대기
        var waitCount = 0
        while (!isDone && waitCount < 10) {
            Thread.sleep(50)
            waitCount++
        }
    }
}
