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

        @Volatile
        private var instance: ScreenOperatorController? = null

        fun getInstance(context: Context): ScreenOperatorController {
            return instance ?: synchronized(this) {
                instance ?: ScreenOperatorController(
                    context = context.applicationContext,
                    safetyGuardrail = SafetyGuardrailManager(context.applicationContext),
                    captureManager = ScreenCaptureManager(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val isInterrupted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val activeLatches = java.util.Collections.synchronizedList(mutableListOf<java.util.concurrent.CountDownLatch>())

    init {
        // 긴급 정지 리스너 등록
        safetyGuardrail.onEmergencyStopListener = {
            onEmergencyStopTriggered()
        }
        YggdrasilAccessibilityService.getInstance()?.onUserPreemptionListener = {
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
        safetyGuardrail.reset()
        val service = YggdrasilAccessibilityService.getInstance()
        if (service == null) {
            ScreenCaptureManager.notifyAccessibilityRequired(context)
            return ExecutionResult(
                false,
                "화면 조작에 필요한 접근성 서비스가 중단되었습니다. 상단 알림창을 눌러 권한을 다시 켜주세요."
            )
        }
        service.setAiAutomating(true)

        // 1. Fail-Safe 인터럽트 상태 체크
        if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
            service.setAiAutomating(false)
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
            service.setAiAutomating(false)
            // 작업 완료 후 골든 보더 해제 (연속 작업이 아닐 경우)
            safetyGuardrail.hideSafetyGoldenBorder()
        }
    }

    private fun clampToSafeArea(x: Float, y: Float): android.graphics.PointF {
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        // 상단 상태바(약 48dp) 및 하단 내비게이션 제스처 바(약 48dp) 시스템 바 안전 마진
        val topInset = 48f * dm.density
        val bottomInset = screenH - (48f * dm.density)
        val horizontalMargin = 8f * dm.density

        val safeX = x.coerceIn(horizontalMargin, screenW - horizontalMargin)
        val safeY = y.coerceIn(topInset, bottomInset)
        return android.graphics.PointF(safeX, safeY)
    }

    private fun executeTap(
        service: YggdrasilAccessibilityService,
        targetText: String?,
        coordinates: Point?
    ): ExecutionResult {
        // 1. 명시적 좌표가 전달된 경우 (시스템 바 안전 영역 클램핑)
        if (coordinates != null) {
            val safePoint = clampToSafeArea(coordinates.x.toFloat(), coordinates.y.toFloat())
            val latch = java.util.concurrent.CountDownLatch(1)
            var isSuccess = false
            registerLatch(latch)
            try {
                service.tap(safePoint.x, safePoint.y) { success ->
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
            return ExecutionResult(isSuccess, "좌표 (${safePoint.x.toInt()}, ${safePoint.y.toInt()}) 탭 완료")
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

    // ==========================================
    // [과제 25]: 접근성 노드 기반 범용 화면 자율 조작 (Universal Operator) ReAct 5스텝 루프
    // ==========================================

    enum class ActionType {
        CLICK,
        TYPE,
        SCROLL,
        FINISH,
        FAIL
    }

    data class OperatorDecision(
        val actionType: ActionType,
        val targetNode: YggdrasilAccessibilityService.InteractiveNode? = null,
        val targetText: String? = null,
        val inputText: String? = null,
        val reason: String = ""
    )

    /**
     * [과제 25]: 접근성 노드 기반 범용 화면 자율 조작(Universal Operator) ReAct 5스텝 루프
     * 
     * 빅스비처럼 제휴된 특정 기능만 깨작거리는 것이 아니라, 스마트폰에 설치된 모든 앱을
     * 사람이 만지듯 자율적으로 조작하는 메커니즘.
     * 
     * 1. 관찰 (Observe): Accessibility 노드 트리 평탄화 수집 + FLAG_SECURE 보안 감지
     * 2. 추론 (Reason): 사용자 목표(userGoal)와 현재 화면 요소를 대조하여 다음 동작(CLICK, TYPE, SCROLL, FINISH) 결정
     * 3. 행동 (Act): Node.ACTION_CLICK (직접 주입 우선) -> dispatchGesture 탭 폴백 -> 텍스트 주입
     * 4. 검증 및 대기 (Wait & Verify): UI 렌더링 변경 대기 및 전이 상태 검증
     * 5. 반복/종료 (Loop / Complete): 목표 달성 시 성공 보고, 사용자 물리적 터치 시 즉각 중단(User Preemption)
     */
    fun executeAutonomousActionPlan(
        userGoal: String,
        initialScreenshot: android.graphics.Bitmap? = null,
        maxSteps: Int = 8
    ): ExecutionResult {
        isInterrupted.set(false)
        val service = YggdrasilAccessibilityService.getInstance()
        if (service == null) {
            ScreenCaptureManager.notifyAccessibilityRequired(context)
            return ExecutionResult(false, "접근성 서비스가 비활성화되어 화면 자율 조작을 수행할 수 없습니다.")
        }

        safetyGuardrail.showSafetyGoldenBorder()
        android.util.Log.i(TAG, "🤖 [Universal Operator] 자율 조작 루프 시작: '$userGoal' (최대 $maxSteps 스텝)")

        try {
            var step = 0
            while (step < maxSteps) {
                step++

                // Fail-Safe: 사용자 물리적 터치 감지 시 즉각 중단 (User Preemption)
                if (isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                    android.util.Log.w(TAG, "User Preemption: 사용자 물리 터치로 자율 조작 즉시 종료")
                    return ExecutionResult(false, "사용자가 화면을 직접 터치하여 자율 조작을 즉시 중단했습니다.")
                }

                // Step 1: 관찰 (Observe)
                val nodes = service.dumpInteractiveNodes()
                if (nodes.isEmpty()) {
                    // 화면 로딩 중일 가능성 있으므로 500ms 대기 후 재시도
                    Thread.sleep(500)
                    continue
                }

                // 보안 화면(비밀번호, PIN, 보안키패드) 감지 시 가드레일 가동
                val hasSecurityField = nodes.any { node ->
                    safetyGuardrail.isPasswordOrSensitiveField(node.node) ||
                    node.text.contains("보안키패드") ||
                    node.text.contains("비밀번호") ||
                    node.text.contains("인증서")
                }
                if (hasSecurityField) {
                    android.util.Log.w(TAG, "보안/비밀번호 화면 감지 -> 음성 핸드오프")
                    return ExecutionResult(
                        success = false,
                        message = "보안 및 금융 보호가 필요한 화면입니다. 해당 단계는 직접 입력해주세요.",
                        details = mapOf("handOffToVoice" to true, "step" to step)
                    )
                }

                // Step 2: 추론 (Reason)
                val decision = planNextAction(userGoal, nodes, step)
                android.util.Log.i(TAG, "스텝 $step 추론 결과: ${decision.actionType} -> ${decision.targetText ?: decision.inputText ?: ""}")

                if (decision.actionType == ActionType.FINISH) {
                    return ExecutionResult(true, "목표 '$userGoal' 달성 완료 ($step 단계 실행)")
                }

                if (decision.actionType == ActionType.FAIL) {
                    return ExecutionResult(false, "더 이상 진행할 수 없습니다: ${decision.reason}")
                }

                // Step 3: 행동 (Act)
                val stepSuccess = when (decision.actionType) {
                    ActionType.CLICK -> {
                        val targetNode = decision.targetNode
                        if (targetNode != null) {
                            val directClicked = service.performClickOnNode(targetNode.node)
                            if (!directClicked) {
                                val bounds = targetNode.bounds
                                val latch = java.util.concurrent.CountDownLatch(1)
                                var tapOk = false
                                registerLatch(latch)
                                try {
                                    service.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat()) { ok ->
                                        tapOk = ok
                                        latch.countDown()
                                    }
                                    latch.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                                } finally {
                                    unregisterLatch(latch)
                                }
                                tapOk
                            } else true
                        } else false
                    }
                    ActionType.TYPE -> {
                        val targetNode = decision.targetNode
                        val text = decision.inputText ?: ""
                        if (targetNode != null) {
                            service.injectText(targetNode.node, text)
                        } else false
                    }
                    ActionType.SCROLL -> {
                        val (w, h) = getRealDisplayBounds()
                        val swipeLatch = java.util.concurrent.CountDownLatch(1)
                        var swipeOk = false
                        registerLatch(swipeLatch)
                        try {
                            service.swipe(w * 0.5f, h * 0.75f, w * 0.5f, h * 0.25f, 350) { ok ->
                                swipeOk = ok
                                swipeLatch.countDown()
                            }
                            swipeLatch.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } finally {
                            unregisterLatch(swipeLatch)
                        }
                        swipeOk
                    }
                    else -> false
                }

                if (!stepSuccess) {
                    android.util.Log.w(TAG, "스텝 $step 액션 실행 실패 -> 다음 시도")
                }

                // Step 4: 검증 및 UI 정착 대기 (Wait & Verify)
                val settled = waitForUiSettled(service, 600)
                if (!settled || isInterrupted.get() || safetyGuardrail.isEmergencyStopped()) {
                    return ExecutionResult(false, "사용자 조작으로 인해 중단되었습니다.")
                }
            }

            return ExecutionResult(false, "최대 허용 스텝($maxSteps)을 초과하여 작업을 안전 종료했습니다.")
        } finally {
            safetyGuardrail.hideSafetyGoldenBorder()
        }
    }

    /**
     * 화면 내 노드 목록과 사용자 목표를 대조하여 다음 최적 액션을 도출하는 지능형 플래너
     */
    private fun planNextAction(
        userGoal: String,
        nodes: List<YggdrasilAccessibilityService.InteractiveNode>,
        step: Int
    ): OperatorDecision {
        val lowerGoal = userGoal.lowercase()

        // 1. 완료 상태 검사 (성공 팝업/텍스트 확인)
        val completionKeywords = listOf("완료되었습니다", "전송 완료", "주문 접수", "성공적으로", "예약 완료", "결제 완료")
        val isFinished = nodes.any { node ->
            completionKeywords.any { kw -> node.text.contains(kw) }
        }
        if (isFinished) {
            return OperatorDecision(ActionType.FINISH, reason = "완료 화면 도달")
        }

        // 2. 검색/입력 목표인 경우
        if (lowerGoal.contains("검색") || lowerGoal.contains("찾아") || lowerGoal.contains("입력")) {
            val queryText = extractSearchQuery(userGoal)
            val editNode = nodes.firstOrNull { it.isEditable }
            if (editNode != null && queryText.isNotBlank()) {
                if (editNode.text.isEmpty() || editNode.text != queryText) {
                    return OperatorDecision(
                        actionType = ActionType.TYPE,
                        targetNode = editNode,
                        inputText = queryText,
                        reason = "검색어 '$queryText' 주입"
                    )
                }
            }

            // 검색 버튼 또는 돋보기 클릭
            val searchBtn = nodes.firstOrNull { node ->
                node.isClickable && (node.text.contains("검색") || node.contentDesc.contains("검색") || node.contentDesc.contains("조회"))
            }
            if (searchBtn != null) {
                return OperatorDecision(
                    actionType = ActionType.CLICK,
                    targetNode = searchBtn,
                    targetText = searchBtn.text.ifEmpty { searchBtn.contentDesc },
                    reason = "검색 버튼 클릭"
                )
            }
        }

        // 3. 확인 / 다음 / 동의 / 보내기 / 주문 등 핵심 진행 버튼 우선 탐색
        val actionKeywords = listOf("동의하고 계속", "전체 동의", "다음", "확인", "보내기", "이체", "송금", "구매하기", "주문하기", "장바구니 담기")
        for (kw in actionKeywords) {
            val matchingNode = nodes.firstOrNull { node ->
                node.isClickable && (node.text.contains(kw) || node.contentDesc.contains(kw))
            }
            if (matchingNode != null) {
                return OperatorDecision(
                    actionType = ActionType.CLICK,
                    targetNode = matchingNode,
                    targetText = matchingNode.text.ifEmpty { matchingNode.contentDesc },
                    reason = "핵심 진행 버튼 '$kw' 클릭"
                )
            }
        }

        // 4. 사용자 목표 단어와의 직접 매칭 탐색
        val words = userGoal.split(" ").filter { it.length >= 2 }
        for (word in words) {
            val matched = nodes.firstOrNull { node ->
                node.isClickable && (node.text.contains(word) || node.contentDesc.contains(word))
            }
            if (matched != null) {
                return OperatorDecision(
                    actionType = ActionType.CLICK,
                    targetNode = matched,
                    targetText = matched.text.ifEmpty { matched.contentDesc },
                    reason = "목표 단어 '$word' 일치 항목 클릭"
                )
            }
        }

        // 5. 현재 뷰포트에 타깃이 없는 경우 아래로 스크롤
        if (step <= 3) {
            return OperatorDecision(
                actionType = ActionType.SCROLL,
                reason = "화면 하단 숨겨진 요소 탐색을 위한 스크롤"
            )
        }

        return OperatorDecision(ActionType.FAIL, reason = "목표와 일치하는 UI 요소를 찾을 수 없습니다.")
    }

    private fun extractSearchQuery(userGoal: String): String {
        return userGoal
            .replace("검색해줘", "")
            .replace("검색해", "")
            .replace("찾아줘", "")
            .replace("입력해줘", "")
            .trim()
    }
}

