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

    companion object {
        private const val TAG = "ScreenOperatorController"
    }

    private val isInterrupted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val activeLatches = java.util.Collections.synchronizedList(mutableListOf<java.util.concurrent.CountDownLatch>())

    init {
        // 긴급 정지 리스너 등록
        safetyGuardrail.onEmergencyStopListener = {
            onEmergencyStopTriggered()
        }
    }

    private fun onEmergencyStopTriggered() {
        // 사용자 화면 터치 시 즉시 모든 자율 조작을 중단 (User Preemption 100% 실전 가드레일)
        isInterrupted.set(true)
        synchronized(activeLatches) {
            for (latch in activeLatches) {
                while (latch.count > 0) {
                    latch.countDown()
                }
            }
            activeLatches.clear()
        }
        android.util.Log.w(TAG, "사용자 화면 물리적 터치 감지 -> AI 자동 화면 조작 즉시 강제 중단!")
    }

    private fun registerLatch(latch: java.util.concurrent.CountDownLatch) {
        activeLatches.add(latch)
    }

    private fun unregisterLatch(latch: java.util.concurrent.CountDownLatch) {
        activeLatches.remove(latch)
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
        isInterrupted.set(false)
        val service = YggdrasilAccessibilityService.getInstance()
            ?: return ExecutionResult(false, "AccessibilityService가 활성화되지 않았거나 권한이 부여되지 않았습니다.")

        // 1. Fail-Safe 인터럽트 상태 체크
        if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
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
            val latch = java.util.concurrent.CountDownLatch(1)
            var isSuccess = false
            registerLatch(latch)
            try {
                service.tap(coordinates.x.toFloat(), coordinates.y.toFloat()) { success ->
                    isSuccess = success
                    latch.countDown()
                }
                latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } finally {
                unregisterLatch(latch)
            }
            if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                return ExecutionResult(false, "사용자 터치로 인해 탭 조작이 긴급 중단되었습니다.")
            }
            return ExecutionResult(isSuccess, "좌표 (${coordinates.x}, ${coordinates.y}) 탭 완료")
        }

        if (targetText.isNullOrBlank()) {
            return ExecutionResult(false, "탭 대상(targetText 또는 coordinates)이 지정되지 않았습니다.")
        }

        // 2. [과제 2] 에이전트 스크롤 탐색 루프 (Scroll-and-Retry: 최대 2회 스크롤)
        val maxScrollRetries = 2
        var currentAttempt = 0

        while (currentAttempt <= maxScrollRetries) {
            if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                return ExecutionResult(false, "사용자 터치로 인해 화면 탐색이 긴급 중단되었습니다.")
            }

            // A. Accessibility Node 트리에서 우선 검색
            val nodes = service.findNodesByText(targetText)
            val clickableNode = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()

            if (clickableNode != null) {
                if (safetyGuardrail.isPasswordOrSensitiveField(clickableNode)) {
                    return ExecutionResult(false, "보안 가드레일: 보안/비밀번호 필드는 터치할 수 없습니다.")
                }

                val rect = Rect()
                clickableNode.getBoundsInScreen(rect)
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()

                val tapLatch = java.util.concurrent.CountDownLatch(1)
                var isSuccess = false
                registerLatch(tapLatch)
                try {
                    service.tap(cx, cy) { success ->
                        isSuccess = success
                        tapLatch.countDown()
                    }
                    tapLatch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                } finally {
                    unregisterLatch(tapLatch)
                }

                if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                    return ExecutionResult(false, "사용자 터치로 인해 탭 조작이 긴급 중단되었습니다.")
                }
                val attemptNote = if (currentAttempt > 0) " (${currentAttempt}회 스크롤 탐색 후)" else ""
                return ExecutionResult(true, "노드 텍스트 '$targetText' 위치 ($cx, $cy) 탭 실행$attemptNote")
            }

            // B. 노드 트리에서 미발견 시 온디바이스 OCR 매핑 (FLAG_SECURE 블랙아웃 감지 일체형)
            val ocrLatch = java.util.concurrent.CountDownLatch(1)
            var ocrTargetPoint: Point? = null
            var isSecureBlackout = false

            registerLatch(ocrLatch)
            try {
                captureManager.findElementWithSecurity(targetText) { matched, blackout ->
                    isSecureBlackout = blackout
                    ocrTargetPoint = matched?.centerPoint
                    ocrLatch.countDown()
                }
                ocrLatch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            } finally {
                unregisterLatch(ocrLatch)
            }

            if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                return ExecutionResult(false, "사용자 터치로 인해 OCR 인식이 긴급 중단되었습니다.")
            }

            // B-1. 금융앱/보안 화면(FLAG_SECURE) 감지 시 즉각 중단 및 음성 안내
            if (isSecureBlackout) {
                return ExecutionResult(
                    success = false,
                    message = "보안 화면이 감지되었습니다. 금융 및 보안 보호를 위해 해당 화면은 직접 입력해주세요.",
                    details = mapOf("isSecureBlackout" to true, "handOffToVoice" to true)
                )
            }

            // B-2. OCR 매칭 성공 시 터치 실행
            if (ocrTargetPoint != null) {
                val pt = ocrTargetPoint!!
                val tapLatch = java.util.concurrent.CountDownLatch(1)
                var tapSuccess = false
                registerLatch(tapLatch)
                try {
                    service.tap(pt.x.toFloat(), pt.y.toFloat()) { s ->
                        tapSuccess = s
                        tapLatch.countDown()
                    }
                    tapLatch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                } finally {
                    unregisterLatch(tapLatch)
                }

                if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                    return ExecutionResult(false, "사용자 터치로 인해 OCR 탭 조작이 긴급 중단되었습니다.")
                }
                val attemptNote = if (currentAttempt > 0) " (${currentAttempt}회 스크롤 탐색 후)" else ""
                return ExecutionResult(true, "온디바이스 OCR로 '$targetText' 인식 후 (${pt.x}, ${pt.y}) 탭 완료$attemptNote")
            }

            // C. 뷰포트 내 미발견 시 아래로 스와이프(화면 아래 숨겨진 버튼 스크롤 탐색)
            if (currentAttempt < maxScrollRetries) {
                currentAttempt++
                val (screenW, screenH) = getRealDisplayBounds()

                // 동적 비율 기반 스크롤 좌표 (가로/세로/폴더블 무관 75% -> 25% 중앙 스크롤)
                val scrollStartX = screenW * 0.5f
                val scrollStartY = screenH * 0.75f
                val scrollEndX = screenW * 0.5f
                val scrollEndY = screenH * 0.25f

                val swipeLatch = java.util.concurrent.CountDownLatch(1)
                registerLatch(swipeLatch)
                try {
                    service.swipe(scrollStartX, scrollStartY, scrollEndX, scrollEndY, 350) {
                        swipeLatch.countDown()
                    }
                    swipeLatch.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                } finally {
                    unregisterLatch(swipeLatch)
                }

                if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                    return ExecutionResult(false, "사용자 터치로 인해 스크롤 탐색이 긴급 중단되었습니다.")
                }

                // UI 스크롤 정착 및 레이아웃 안정화 동적 대기 (인터럽트 즉시 감지)
                val stabilized = waitForUiSettled(service, 300)
                if (!stabilized || isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                    return ExecutionResult(false, "사용자 터치로 인해 스크롤 대기가 인터럽트되었습니다.")
                }
            } else {
                break
            }
        }

        // 1회성 알림(Toast/스낵바) 캐시에서 최종 대조 (OCR 타이밍 미스 방어)
        val transient = service.findRecentTransientMessage(targetText, 5000)
        if (transient != null) {
            return ExecutionResult(
                success = true,
                message = "최근 1회성 알림(Toast/스낵바)에서 '$targetText' 감지: '${transient.text}'",
                details = mapOf("transient" to true, "text" to transient.text, "isToast" to transient.isToast)
            )
        }

        return ExecutionResult(false, "화면을 ${maxScrollRetries}회 스크롤 탐색하였으나 '$targetText' 요소를 찾을 수 없습니다.")
    }

    /**
     * UI 스크롤 정착 동적 대기 (블로킹 Thread.sleep(400) 제거)
     */
    private fun waitForUiSettled(service: YggdrasilAccessibilityService, maxWaitMs: Long): Boolean {
        val checkIntervalMs = 50L
        var waited = 0L
        while (waited < maxWaitMs) {
            if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                return false
            }
            try {
                Thread.sleep(checkIntervalMs)
            } catch (e: InterruptedException) {
                return false
            }
            waited += checkIntervalMs
        }
        return true
    }

    private fun getRealDisplayBounds(): Pair<Float, Float> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width().toFloat(), bounds.height().toFloat())
        } else {
            val dm = context.resources.displayMetrics
            Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }
    }

    private fun executeSwipe(
        service: YggdrasilAccessibilityService,
        direction: String?
    ): ExecutionResult {
        val dir = direction?.lowercase() ?: "up"
        val (screenW, screenH) = getRealDisplayBounds()

        val midX = screenW * 0.5f
        val midY = screenH * 0.5f

        // 가로/세로/폴더블 실시간 해상도 기반 동적 상대 비율 스와이프
        val (startX, startY, endX, endY) = when (dir) {
            "up" -> arrayOf(midX, screenH * 0.75f, midX, screenH * 0.25f)
            "down" -> arrayOf(midX, screenH * 0.25f, midX, screenH * 0.75f)
            "left" -> arrayOf(screenW * 0.8f, midY, screenW * 0.2f, midY)
            "right" -> arrayOf(screenW * 0.2f, midY, screenW * 0.8f, midY)
            else -> arrayOf(midX, screenH * 0.75f, midX, screenH * 0.25f)
        }

        val swipeLatch = java.util.concurrent.CountDownLatch(1)
        var isSuccess = false
        registerLatch(swipeLatch)
        try {
            service.swipe(startX, startY, endX, endY) { success ->
                isSuccess = success
                swipeLatch.countDown()
            }
            swipeLatch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } finally {
            unregisterLatch(swipeLatch)
        }

        if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
            return ExecutionResult(false, "사용자 터치로 인해 스와이프 조작이 긴급 중단되었습니다.")
        }

        return ExecutionResult(isSuccess, "화면 $dir 스와이프 완료 (success=$isSuccess)")
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
