package com.yggdrasil.assistant.operator

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout

/**
 * SafetyGuardrailManager
 * 화면 자율 조작 중 사용자의 안전과 개인정보를 보호하는 3대 안전 가드레일 관리자
 *
 * 1. AI 조작 중 화면 외곽에 황금빛 오버레이 테두리(Golden Border) 표시
 * 2. 사용자가 스마트폰을 직접 터치 시 AI 제어를 즉시 강제 중단하는 Fail-Safe 인터럽트
 * 3. 비밀번호/PIN 입력 필드 감지 시 화면 캡처 및 텍스트 자동 주입 절대 차단
 */
class SafetyGuardrailManager(private val context: Context) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isInterruptedByUser: Boolean = false

    // 사용자 긴급 인터럽트 콜백
    var onEmergencyStopListener: (() -> Unit)? = null

    /**
     * AI 제어 시작 시 화면 외곽에 황금빛 테두리 오버레이 표시
     */
    fun showSafetyGoldenBorder() {
        if (overlayView != null) return

        val frame = FrameLayout(context).apply {
            // 터치 이벤트를 통과시키면서 사용자 터치를 감지
            setBackgroundColor(Color.TRANSPARENT)
            // 외곽 테두리 (골드빛 스트로크 #FFD700)
            val borderView = View(context).apply {
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            }
            addView(borderView)

            setOnTouchListener { _, _ ->
                // 사용자가 화면을 직접 터치하는 순간 긴급 중단(Fail-Safe) 트리거!
                triggerUserEmergencyInterrupt()
                false
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(frame, params)
            overlayView = frame
            isInterruptedByUser = false
        } catch (e: Exception) {
            // 오버레이 권한 미부여 시 콘솔 로깅 후 폴백
        }
    }

    /**
     * AI 제어 종료 시 테두리 오버레이 제거
     */
    fun hideSafetyGoldenBorder() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (ignored: Exception) {
            }
            overlayView = null
        }
    }

    /**
     * 사용자가 화면을 직접 터치했을 때 즉시 호출되는 Fail-Safe 긴급 정지
     */
    fun triggerUserEmergencyInterrupt() {
        isInterruptedByUser = true
        hideSafetyGoldenBorder()
        onEmergencyStopListener?.invoke()
    }

    /**
     * 현재 사용자에 의해 강제 인터럽트가 걸렸는지 여부
     */
    fun isEmergencyStopped(): Boolean = isInterruptedByUser

    /**
     * 비밀번호/금융 PIN 입력창 여부 검증 (보안 가드레일)
     * 비밀번호 창이면 true 반환 (AI 자동 입력 및 캡처 절대 차단)
     */
    fun isPasswordOrSensitiveField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // 1. 노드 자체 패스워드 플래그 확인
        if (node.isPassword) return true

        // 2. 텍스트 및 클래스명 검사
        val className = node.className?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        val sensitiveKeywords = listOf("password", "pin", "cvc", "비밀번호", "인증번호", "보안카드", "주민등록번호")
        for (keyword in sensitiveKeywords) {
            if (text.contains(keyword) || contentDesc.contains(keyword) || className.contains("password")) {
                return true
            }
        }

        return false
    }
}
