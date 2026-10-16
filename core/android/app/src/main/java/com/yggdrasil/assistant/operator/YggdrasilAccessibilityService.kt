package com.yggdrasil.assistant.operator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * YggdrasilAccessibilityService
 * Android 정식 AccessibilityService 기반 UI 자동 조작 엔진
 * 
 * 1. AccessibilityNodeInfo를 활용한 화면 요소 실시간 탐색 및 파싱
 * 2. dispatchGesture API를 활용한 정밀 탭, 더블 탭, 롱 클릭, 스와이프 제어
 * 3. ACTION_SET_TEXT 및 시스템 키 이벤트(Home, Back, Recents) 전송
 * 4. SafetyGuardrailManager와 연동하여 안전 가드레일 준수
 */
class YggdrasilAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: YggdrasilAccessibilityService? = null

        /**
         * 서비스 싱글톤 인스턴스 반환
         */
        fun getInstance(): YggdrasilAccessibilityService? = instance
    }

    data class TransientMessage(
        val text: String,
        val timestamp: Long,
        val packageName: String,
        val isToast: Boolean
    )

    private val transientMessageCache = java.util.concurrent.ConcurrentLinkedDeque<TransientMessage>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        // 1회성 토스트, 스낵바 및 알림 상태 변경 이벤트 캡처
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val textList = event.text
            if (!textList.isNullOrEmpty()) {
                val fullText = textList.joinToString(" ") { it.toString() }.trim()
                if (fullText.isNotBlank()) {
                    val isToast = event.parcelableData !is android.app.Notification
                    val msg = TransientMessage(
                        text = fullText,
                        timestamp = System.currentTimeMillis(),
                        packageName = event.packageName?.toString() ?: "",
                        isToast = isToast
                    )
                    transientMessageCache.addFirst(msg)
                    while (transientMessageCache.size > 30) {
                        transientMessageCache.pollLast()
                    }
                }
            }
        }
    }

    /**
     * 최근 N밀리초 이내에 수신된 1회성 토스트/스낵바 메시지 목록 조회
     */
    fun getRecentTransientMessages(maxAgeMs: Long = 5000): List<TransientMessage> {
        val now = System.currentTimeMillis()
        return transientMessageCache.filter { (now - it.timestamp) <= maxAgeMs }
    }

    /**
     * 최근 N밀리초 이내의 토스트/스낵바 중 특정 텍스트를 포함하는 메시지 검색
     */
    fun findRecentTransientMessage(query: String, maxAgeMs: Long = 5000): TransientMessage? {
        val now = System.currentTimeMillis()
        val lowerQuery = query.lowercase().trim()
        return transientMessageCache.firstOrNull { msg ->
            (now - msg.timestamp) <= maxAgeMs && msg.text.lowercase().contains(lowerQuery)
        }
    }

    override fun onInterrupt() {
        // 접근성 서비스 일시 중단 시 처리
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    // ==========================================
    // 1. 노드 탐색 및 UI 계층 파싱 API
    // ==========================================

    /**
     * 특정 텍스트를 포함하는 화면 노드 리스트 검색
     */
    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        return rootNode.findAccessibilityNodeInfosByText(text) ?: emptyList()
    }

    /**
     * 특정 View ID(리소스 ID)로 화면 노드 리스트 검색
     */
    fun findNodesById(viewId: String): List<AccessibilityNodeInfo> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        return rootNode.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
    }

    /**
     * 현재 화면의 모든 UI 요소를 계층형 데이터 모델로 덤프
     */
    fun dumpScreenHierarchy(): Map<String, Any> {
        val rootNode = rootInActiveWindow ?: return emptyMap()
        return parseNodeRecursive(rootNode)
    }

    private fun parseNodeRecursive(node: AccessibilityNodeInfo): Map<String, Any> {
        val nodeMap = mutableMapOf<String, Any>()
        nodeMap["className"] = node.className?.toString() ?: ""
        nodeMap["text"] = node.text?.toString() ?: ""
        nodeMap["contentDescription"] = node.contentDescription?.toString() ?: ""
        nodeMap["viewId"] = node.viewIdResourceName ?: ""
        nodeMap["isClickable"] = node.isClickable
        nodeMap["isEditable"] = node.isEditable
        nodeMap["isPassword"] = node.isPassword

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        nodeMap["bounds"] = mapOf(
            "left" to bounds.left,
            "top" to bounds.top,
            "right" to bounds.right,
            "bottom" to bounds.bottom,
            "centerX" to bounds.centerX(),
            "centerY" to bounds.centerY()
        )

        val children = mutableListOf<Map<String, Any>>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                children.add(parseNodeRecursive(child))
            }
        }
        if (children.isNotEmpty()) {
            nodeMap["children"] = children
        }

        return nodeMap
    }

    // ==========================================
    // 2. dispatchGesture 기반 터치/제스처 제어 API
    // ==========================================

    /**
     * 지정된 화면 좌표 (x, y) 단일 탭 실행
     */
    fun tap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 지정된 화면 좌표 (x, y) 더블 탭 실행
     */
    fun doubleTap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        tap(x, y) { successFirst ->
            if (!successFirst) {
                callback?.invoke(false)
                return@tap
            }
            // 100ms 지연 후 두 번째 탭
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tap(x, y, callback)
            }, 100)
        }
    }

    /**
     * 지정된 화면 좌표 (x, y) 롱 클릭 실행
     */
    fun longPress(x: Float, y: Float, durationMs: Long = 800, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 화면 스와이프 및 스크롤 실행
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    // ==========================================
    // 3. 텍스트 자동 주입(Text Injection) API
    // ==========================================

    /**
     * 특정 노드에 텍스트 주입 (ACTION_SET_TEXT)
     */
    fun injectText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) {
            // 편집 가능한 노드가 아니면 부모나 자식 노드 중 editable 노드 탐색
            val editableNode = findEditableNode(node) ?: return false
            return setNodeText(editableNode, text)
        }
        return setNodeText(node, text)
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    // ==========================================
    // 4. 시스템 내비게이션 키 이벤트
    // ==========================================

    /**
     * 뒤로가기 키 실행
     */
    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * 홈 버튼 키 실행
     */
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /**
     * 최근 앱(멀티태스킹) 화면 키 실행
     */
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * 알림창 열기 키 실행
     */
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
}
