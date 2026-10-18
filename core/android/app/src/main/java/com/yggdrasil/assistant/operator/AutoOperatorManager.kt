package com.yggdrasil.assistant.operator

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [AutoOperatorManager]
 * 실제 오토마우스처럼 화면 제어권을 장악하여 사람처럼 클릭, 스와이프, 앱 실행을 자율 수행하는 엔진.
 * 
 * 1. 앱 이름 기반 자율 실행 (유튜브, 카카오톡, 인스타, 네이버, 쿠팡 등 스마트폰의 모든 앱)
 * 2. 오토마우스 가상 터치 포인터(Virtual Touch Pointer): 터치 지점에 황금빛 리플 링을 시각적으로 띄움
 * 3. 인간형 베지어 곡선 스와이프 & 지능형 다단계 연속 동작 수행
 */
class AutoOperatorManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AutoOperatorManager"

        @Volatile
        private var instance: AutoOperatorManager? = null

        fun getInstance(context: Context): AutoOperatorManager {
            return instance ?: synchronized(this) {
                instance ?: AutoOperatorManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchIndicatorView: View? = null

    // 🛡️ 금융 및 결제 보안 앱 블랙리스트 (FLAG_SECURE 및 오송금 사고 방지)
    private val SECURE_FINANCE_PACKAGES = setOf(
        "viva.republica.toss",
        "com.kakaobank.channel",
        "com.kakaopay.app",
        "com.kbstar.kbbank",
        "com.shinhan.sbanking",
        "nh.smart.banking",
        "com.ibk.neobanking",
        "com.wooribank.smart.npib",
        "com.hanabank.ebk.channel.android.hananbank",
        "com.samsung.android.spay",
        "com.samsung.android.rajaampat"
    )

    private fun isSecureScreen(service: YggdrasilAccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: ""
        return SECURE_FINANCE_PACKAGES.any { pkg.contains(it) }
    }

    /**
     * 사용자 자연어 지시에 따른 자율 화면 조작 수행
     * 예: "유튜브 켜줘", "스크롤 내려줘", "인스타그램 켜줘", "뒤로가", "홈으로"
     */
    suspend fun executeNaturalCommand(command: String): ExecutionReport = withContext(Dispatchers.Default) {
        val report = executeNaturalCommandInternal(command)
        if (report.success) {
            try {
                com.yggdrasil.assistant.growth.WorldTreeGrowthManager.getInstance(context).recordAction()
            } catch (_: Exception) {}
        }
        report
    }

    private suspend fun executeNaturalCommandInternal(command: String): ExecutionReport = withContext(Dispatchers.Default) {
        val cmd = command.trim()
        val lower = cmd.lowercase()
        Log.i(TAG, "🤖 [자율 조작 명령 수신]: '$cmd'")

        val service = YggdrasilAccessibilityService.getInstance()
        if (service == null) {
            return@withContext ExecutionReport(false, "접근성 서비스가 비활성화되어 있습니다. 설정에서 활성화해주세요.")
        }

        // 🛡️ 금융/결제 화면 진입 감지 시 사용자 안전 보호 (P0 가드레일)
        if (isSecureScreen(service)) {
            Log.w(TAG, "🛡️ [보안 가드레일] 금융/결제 앱 화면 감지 -> 자율 조작 즉시 차단")
            return@withContext ExecutionReport(false, "금융 및 결제 화면에서는 안전을 위해 자율 화면 조작이 제한됩니다. 직접 조작해 주세요.")
        }

        // 1. 시스템 내비게이션 명령 ("홈", "홈으로", "홈 화면", "홈 화면으로 가줘", "바탕화면", "메인 화면", "뒤로", "최근 앱")
        if (lower.contains("홈") || lower.contains("바탕화면") || lower.contains("메인화면")) {
            val ok = service.pressHome()
            return@withContext ExecutionReport(ok, "홈 화면으로 이동했습니다.")
        }
        if (lower.contains("뒤로") || lower.contains("이전")) {
            val ok = service.pressBack()
            return@withContext ExecutionReport(ok, "이전 화면으로 돌아갔습니다.")
        }
        if (lower.contains("최근 앱") || lower.contains("멀티태스킹")) {
            val ok = service.pressRecents()
            return@withContext ExecutionReport(ok, "최근 실행 앱 목록을 열었습니다.")
        }

        // 2. 복합 명령 A: 메시지 전송 및 카톡/문자 남기기 ("카카오톡 실행해서 OO에게 안녕이라고 카톡 남겨줘", "카톡 방에 안녕하세요 보내")
        val isMessageIntent = lower.contains("보내") || lower.contains("전송") || lower.contains("남겨") ||
                lower.contains("써줘") || lower.contains("말해줘") || lower.contains("작성") ||
                lower.contains("카톡") || lower.contains("메시지")
        val isMessageTarget = lower.contains("방에") || lower.contains("한테") || lower.contains("에게") ||
                lower.contains("카톡") || lower.contains("카카오톡") || lower.contains("문자") ||
                lower.contains("라인") || lower.contains("텔레그램")

        if (isMessageIntent && isMessageTarget) {
            val chainedReport = executeChainedMessageCommand(cmd, service)
            if (chainedReport != null) {
                return@withContext chainedReport
            }
        }

        // 3. 복합 명령 B: 앱 실행 후 검색/설치/재생 ("플레이스토어 실행해서 브롤스타즈 깔아줘", "유튜브 켜서 아이유 틀어줘", "네이버 열어서 오늘 주가 검색")
        if (lower.contains("실행해서") || lower.contains("켜서") || lower.contains("열어서") || lower.contains("들어가서") || lower.contains("열고")) {
            val appActionReport = executeChainedAppAction(cmd, service)
            if (appActionReport != null) {
                return@withContext appActionReport
            }
        }

        // 4. 스크롤 / 스와이프 명령
        if (lower.contains("내려") || lower.contains("아래로") || lower.contains("스크롤 다운")) {
            val bounds = getScreenMetrics()
            val startY = bounds.second * 0.75f
            val endY = bounds.second * 0.25f
            val midX = bounds.first * 0.5f

            showVirtualPointerAt(midX, startY)
            delay(150)
            performSmoothSwipe(service, midX, startY, midX, endY, 400)
            return@withContext ExecutionReport(true, "화면을 아래로 스크롤했습니다.")
        }
        if (lower.contains("올려") || lower.contains("위로") || lower.contains("스크롤 업")) {
            val bounds = getScreenMetrics()
            val startY = bounds.second * 0.25f
            val endY = bounds.second * 0.75f
            val midX = bounds.first * 0.5f

            showVirtualPointerAt(midX, startY)
            delay(150)
            performSmoothSwipe(service, midX, startY, midX, endY, 400)
            return@withContext ExecutionReport(true, "화면을 위로 스크롤했습니다.")
        }

        // 5. 단일 앱 실행 명령 ("유튜브 켜줘", "카카오톡 열어줘", "인스타 켜", "설정 열어줘")
        val appName = extractTargetAppName(cmd)
        if (appName.isNotBlank()) {
            val launched = launchAppByName(appName)
            if (launched) {
                delay(1200) // 앱 로딩 대기
                return@withContext ExecutionReport(true, "'$appName' 앱을 실행했습니다.")
            }
        }

        // 6. 의미론적(Semantic) UI 노드 클릭 ("'검색' 눌러줘", "'재생' 클릭해줘", "'확인' 눌러")
        val clickTarget = extractClickTarget(cmd)
        if (clickTarget.isNotBlank()) {
            if (service.isUserInterrupted.get()) {
                return@withContext ExecutionReport(false, "사용자 터치로 인해 조작이 중단되었습니다.")
            }
            val targetNode = waitForNode(service, clickTarget, timeoutMs = 1500L)
            if (targetNode != null) {
                val rect = android.graphics.Rect()
                targetNode.getBoundsInScreen(rect)
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()

                // 오토마우스 시각화 포인터 표시
                showVirtualPointerAt(cx, cy)
                delay(150)

                if (service.isUserInterrupted.get()) {
                    return@withContext ExecutionReport(false, "사용자 터치로 인해 클릭이 중단되었습니다.")
                }

                // 1순위: 노드 직접 클릭 액션 수행
                val directOk = service.performClickOnNode(targetNode)
                if (!directOk) {
                    // 2순위: 노드 중심점 제스처 탭 (디스플레이 오토마우스 터치)
                    service.tap(cx, cy)
                }
                Log.i(TAG, "🎯 [시맨틱 클릭 성공]: '$clickTarget' 노드 좌표($cx, $cy)")
                return@withContext ExecutionReport(true, "'$clickTarget' 요소를 클릭했습니다.")
            } else {
                // 비표준 캔버스/웹뷰 앱 대응 (배달의민족, 토스 등): 1-Shot 캡처 후 Google ML Kit Text Recognition 비전 OCR
                Log.w(TAG, "⚠️ [노드 미발견]: 캔버스/웹뷰 앱 대비 Google ML Kit 비전 OCR 좌표 역산 파이프라인 가동")
                val captureManager = ScreenCaptureManager.getInstance(context)
                var ocrTargetPoint: android.graphics.Point? = null
                var isSecureBlackout = false
                val latch = java.util.concurrent.CountDownLatch(1)

                captureManager.findElementWithSecurity(clickTarget) { matched: ScreenCaptureManager.RecognizedElement?, blackout: Boolean ->
                    isSecureBlackout = blackout
                    ocrTargetPoint = matched?.centerPoint
                    latch.countDown()
                }
                latch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS)

                if (service.isUserInterrupted.get()) {
                    return@withContext ExecutionReport(false, "사용자 터치로 인해 OCR 인식이 중단되었습니다.")
                }

                if (isSecureBlackout) {
                    return@withContext ExecutionReport(false, "보안 화면(FLAG_SECURE) 감지로 인해 자동 조작이 차단되었습니다. 직접 터치해주세요.")
                }

                if (ocrTargetPoint != null) {
                    val pt = ocrTargetPoint!!
                    showVirtualPointerAt(pt.x.toFloat(), pt.y.toFloat())
                    delay(150)
                    service.tap(pt.x.toFloat(), pt.y.toFloat())
                    Log.i(TAG, "🎯 [비전 OCR 좌표 역산 탭 성공]: '$clickTarget' (${pt.x}, ${pt.y})")
                    return@withContext ExecutionReport(true, "화면 비전 인식으로 '$clickTarget' 요소를 찾아 탭했습니다.")
                } else {
                    Log.w(TAG, "⚠️ [비전 OCR 미발견]: 화면에 '$clickTarget' 텍스트를 찾을 수 없습니다.")
                }
            }
        }

        // 7. 일반 ReAct 자율 계획기로 위임
        val controller = ScreenOperatorController.getInstance(context)
        val result = controller.executeAutonomousActionPlan(cmd)
        ExecutionReport(result.success, result.message)
    }

    /**
     * 복합 메시지 전송 파이프라인 (앱 실행 -> 방/사람 탐색 -> 클릭 -> 텍스트 입력 -> 전송 버튼 클릭)
     */
    private suspend fun executeChainedMessageCommand(
        cmd: String,
        service: YggdrasilAccessibilityService
    ): ExecutionReport? {
        Log.i(TAG, "⛓️ [복합 메시지 파이프라인 시작]: $cmd")

        // 1. 앱 추출 (카카오톡, 카톡, 메시지, 텔레그램, 라인)
        val appName = when {
            cmd.contains("카톡") || cmd.contains("카카오톡") -> "카카오톡"
            cmd.contains("메시지") || cmd.contains("문자") -> "메시지"
            cmd.contains("텔레그램") -> "텔레그램"
            cmd.contains("라인") -> "라인"
            else -> "카카오톡"
        }

        // 2. 메시지 본문 추출 (따옴표, "~~라고", "방에/한테/에게" 이후 발화 정밀 분리)
        val quoteRegex = Regex("['\"](.*?)['\"]")
        val quoteMatch = quoteRegex.find(cmd)
        val messageText = if (quoteMatch != null) {
            quoteMatch.groupValues[1].trim()
        } else if (cmd.contains("라고")) {
            // "박XX에게 안녕이라고 카톡 남겨줘" -> "안녕"
            val beforeRago = cmd.substringBefore("라고")
            beforeRago.substringAfterLast(" ").trim().ifBlank { "안녕하세요" }
        } else {
            val afterTarget = when {
                cmd.contains("방에") -> cmd.substringAfter("방에")
                cmd.contains("한테") -> cmd.substringAfter("한테")
                cmd.contains("에게") -> cmd.substringAfter("에게")
                else -> cmd
            }
            afterTarget
                .replace(Regex("(카톡\\s*남겨줘|카톡\\s*보내줘|카톡|보내줘|보내|전송해줘|전송|입력해줘|입력|남겨줘|남겨|써줘)$"), "")
                .trim()
                .ifBlank { "안녕하세요" }
        }

        // 3. 대상 방/사람 추출 (실행해서, 들어가서, 켜서, 열어서 등 완벽 분리)
        val targetPerson = when {
            cmd.contains("방에") -> {
                val pre = cmd.substringBefore("방에")
                pre.replace(Regex(".*(실행해서|실행해|실행|들어가서|들어가|켜서|열어서|열고|에서|에서\\s*|앱에\\s*|앱\\s*)"), "")
                    .replace("카톡", "").replace("카카오톡", "").replace("메시지", "").replace("앱", "").trim()
            }
            cmd.contains("한테") -> {
                val pre = cmd.substringBefore("한테")
                pre.replace(Regex(".*(실행해서|실행해|실행|들어가서|들어가|켜서|열어서|열고|에서|에서\\s*|앱에\\s*|앱\\s*)"), "")
                    .replace("카톡", "").replace("카카오톡", "").replace("메시지", "").replace("앱", "").trim()
            }
            cmd.contains("에게") -> {
                val pre = cmd.substringBefore("에게")
                pre.replace(Regex(".*(실행해서|실행해|실행|들어가서|들어가|켜서|열어서|열고|에서|에서\\s*|앱에\\s*|앱\\s*)"), "")
                    .replace("카톡", "").replace("카카오톡", "").replace("메시지", "").replace("앱", "").trim()
            }
            else -> ""
        }.trim()

        Log.i(TAG, "⛓️ [복합 메시지 파싱 완료]: 앱=$appName, 대상=$targetPerson, 내용=\"$messageText\"")
        return executeSendMessageDirect(appName, targetPerson, messageText, service)
    }

    /**
     * 특정 앱, 대상, 메시지를 화면 자동화(Accessibility)로 즉시 다이렉트 전송
     */
    suspend fun executeSendMessageDirect(
        appName: String,
        targetPerson: String,
        messageText: String,
        serviceParam: YggdrasilAccessibilityService? = null
    ): ExecutionReport = withContext(Dispatchers.Default) {
        val service = serviceParam ?: YggdrasilAccessibilityService.getInstance()
        if (service == null) {
            return@withContext ExecutionReport(false, "접근성 서비스가 활성화되지 않았습니다. 설정에서 이그드라실 접근성 서비스를 켜주세요.")
        }
        if (service.isUserInterrupted.get()) {
            return@withContext ExecutionReport(false, "사용자 터치로 인해 메시지 발송이 중단되었습니다.")
        }

        // 1단계: 앱 실행
        launchAppByName(appName)
        waitForCondition(service, timeoutMs = 2000L) { service.rootInActiveWindow != null }

        if (service.isUserInterrupted.get()) {
            return@withContext ExecutionReport(false, "사용자 터치로 인해 조작이 중단되었습니다.")
        }

        // 2단계: 대상 방/사람 탐색 및 진입
        if (targetPerson.isNotBlank()) {
            var personNode = waitForNode(service, targetPerson, timeoutMs = 1500L)
            if (personNode == null) {
                // 화면에 즉시 보이지 않을 경우 앱 내 '검색' 버튼 클릭하여 직접 검색 진입
                val searchBtn = waitForNode(service, "검색", timeoutMs = 1000L)
                if (searchBtn != null) {
                    service.performClickOnNode(searchBtn)
                    waitForCondition(service, timeoutMs = 1500L) { findFirstEditableNode(service.rootInActiveWindow) != null }
                    val searchEdit = findFirstEditableNode(service.rootInActiveWindow)
                    if (searchEdit != null) {
                        val sArgs = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetPerson)
                        }
                        searchEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, sArgs)
                        personNode = waitForNode(service, targetPerson, timeoutMs = 2000L)
                    }
                }
            }

            if (service.isUserInterrupted.get()) {
                return@withContext ExecutionReport(false, "사용자 터치로 인해 방 진입이 중단되었습니다.")
            }

            if (personNode != null) {
                val rect = android.graphics.Rect()
                personNode.getBoundsInScreen(rect)
                showVirtualPointerAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                delay(150)
                service.performClickOnNode(personNode)
                waitForCondition(service, timeoutMs = 2000L) { findFirstEditableNode(service.rootInActiveWindow) != null }
            }
        }

        if (service.isUserInterrupted.get()) {
            return@withContext ExecutionReport(false, "사용자 터치로 인해 텍스트 입력이 중단되었습니다.")
        }

        // 3단계: 채팅 입력창(EditText) 탐색 및 텍스트 주입
        val root = service.rootInActiveWindow
        val editNode = findFirstEditableNode(root)
        if (editNode != null) {
            val rect = android.graphics.Rect()
            editNode.getBoundsInScreen(rect)
            showVirtualPointerAt(rect.centerX().toFloat(), rect.centerY().toFloat())
            delay(100)

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    messageText
                )
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            waitForCondition(service, timeoutMs = 1000L) {
                findBestMatchingNode(service, "전송") != null || findBestMatchingNode(service, "보내기") != null
            }

            // 4단계: 전송 버튼 탐색 및 클릭 (다중 별칭 매칭 + 키보드 엔터 폴백)
            val sendNode = findBestMatchingNode(service, "전송")
                ?: findBestMatchingNode(service, "보내기")
                ?: findBestMatchingNode(service, "Send")
                ?: findBestMatchingNode(service, "send")
                ?: findBestMatchingNode(service, "btn_send")

            if (sendNode != null) {
                val sendRect = android.graphics.Rect()
                sendNode.getBoundsInScreen(sendRect)
                showVirtualPointerAt(sendRect.centerX().toFloat(), sendRect.centerY().toFloat())
                delay(150)
                service.performClickOnNode(sendNode)
                return@withContext ExecutionReport(true, "'$appName'에서 '$targetPerson'님께 \"$messageText\" 메시지를 전송했습니다.")
            } else {
                // 🛡️ 화면에 전송 버튼 텍스트가 없을 때: 소프트 키보드 우측 하단 엔터/전송 키(화면 90%, 94%) 가상 탭
                val metrics = getScreenMetrics()
                val enterX = metrics.first * 0.90f
                val enterY = metrics.second * 0.94f
                showVirtualPointerAt(enterX, enterY)
                delay(150)
                service.tap(enterX, enterY)
                return@withContext ExecutionReport(true, "'$appName'에서 '$targetPerson'님께 \"$messageText\" 메시지를 전송했습니다.")
            }
        }

        return@withContext ExecutionReport(true, "'$appName'을 실행하여 '$targetPerson' 대상을 탐색했습니다.")
    }

    /**
     * 복합 앱 실행 후 작업 파이프라인 ("플레이스토어 실행해서 브롤스타즈 깔아줘", "유튜브 켜서 아이유 틀어줘")
     */
    private suspend fun executeChainedAppAction(
        cmd: String,
        service: YggdrasilAccessibilityService
    ): ExecutionReport? {
        val splitKey = when {
            cmd.contains("실행해서") -> "실행해서"
            cmd.contains("켜서") -> "켜서"
            cmd.contains("열어서") -> "열어서"
            cmd.contains("들어가서") -> "들어가서"
            cmd.contains("열고") -> "열고"
            else -> return null
        }

        val appPart = cmd.substringBefore(splitKey).replace("앱", "").trim()
        val actionPart = cmd.substringAfter(splitKey).trim()
        val appName = appPart.ifBlank { extractTargetAppName(cmd) }
        if (appName.isBlank()) return null

        Log.i(TAG, "⛓️ [복합 앱 액션 시작]: 앱='$appName', 액션='$actionPart'")

        // 1단계: 대상 앱 실행 및 화면 정착 동적 대기
        val launched = launchAppByName(appName)
        if (!launched) return null
        waitForCondition(service, timeoutMs = 2500L) { service.rootInActiveWindow != null }

        // 2단계: 검색창 또는 검색 버튼 탐색
        val searchNode = waitForNode(service, "검색", timeoutMs = 1500L)
            ?: waitForNode(service, "Search", timeoutMs = 1000L)
        if (searchNode != null && !searchNode.isEditable) {
            val sRect = android.graphics.Rect()
            searchNode.getBoundsInScreen(sRect)
            showVirtualPointerAt(sRect.centerX().toFloat(), sRect.centerY().toFloat())
            delay(100)
            service.performClickOnNode(searchNode)
            waitForCondition(service, timeoutMs = 1500L) { findFirstEditableNode(service.rootInActiveWindow) != null }
        }

        // 검색 키워드 추출 (예: "브롤스타즈 깔아줘" -> "브롤스타즈", "아이유 틀어줘" -> "아이유")
        val keyword = actionPart
            .replace(Regex("(깔아줘|깔아|설치해줘|설치해|설치|다운로드해줘|다운해줘|다운|검색해줘|검색|찾아줘|찾아|틀어줘|틀어|재생해줘|재생|보여줘)$"), "")
            .trim()

        val editNode = findFirstEditableNode(service.rootInActiveWindow)
        if (editNode != null && keyword.isNotBlank()) {
            val eRect = android.graphics.Rect()
            editNode.getBoundsInScreen(eRect)
            showVirtualPointerAt(eRect.centerX().toFloat(), eRect.centerY().toFloat())
            delay(100)

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            waitForCondition(service, timeoutMs = 1000L) {
                findBestMatchingNode(service, "검색") != null || findBestMatchingNode(service, keyword) != null
            }

            // 검색 실행 (검색 버튼 또는 첫 번째 일치 항목 클릭)
            val execSearch = findBestMatchingNode(service, "검색")
                ?: findBestMatchingNode(service, keyword)
            if (execSearch != null && execSearch != editNode) {
                service.performClickOnNode(execSearch)
                waitForCondition(service, timeoutMs = 2000L) { service.rootInActiveWindow != null }
            }

            // '설치' / '다운로드' 명령인 경우 설치 버튼 자동 클릭
            if (actionPart.contains("깔아") || actionPart.contains("설치") || actionPart.contains("다운")) {
                val installBtn = waitForNode(service, "설치", timeoutMs = 2500L)
                    ?: waitForNode(service, "받기", timeoutMs = 1500L)
                if (installBtn != null) {
                    val iRect = android.graphics.Rect()
                    installBtn.getBoundsInScreen(iRect)
                    showVirtualPointerAt(iRect.centerX().toFloat(), iRect.centerY().toFloat())
                    delay(100)
                    service.performClickOnNode(installBtn)
                    return ExecutionReport(true, "'$appName'에서 '$keyword' 검색 후 '설치'를 시작했습니다.")
                }
            }

            return ExecutionReport(true, "'$appName'에서 '$keyword' 검색 작업을 수행했습니다.")
        }

        return ExecutionReport(true, "'$appName'을 실행하여 요청하신 작업을 진행했습니다.")
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable || node.className?.toString()?.contains("EditText") == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * 화면 전환 동적 대기 (Explicit Wait / Sync Barrier)
     * 고정 delay()를 제거하고 목표 노드가 화면에 렌더링되거나 로딩이 완료될 때까지 지능형 폴링
     */
    private suspend fun waitForNode(
        service: YggdrasilAccessibilityService,
        keyword: String,
        timeoutMs: Long = 2500L,
        pollIntervalMs: Long = 50L
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (service.isUserInterrupted.get()) {
                Log.w(TAG, "사용자 물리 터치 감지 -> 동적 대기(Sync Barrier) 즉시 인터럽트 취소")
                return null
            }
            val node = findBestMatchingNode(service, keyword)
            if (node != null) return node
            delay(pollIntervalMs)
        }
        return null
    }

    private suspend fun waitForCondition(
        service: YggdrasilAccessibilityService,
        timeoutMs: Long = 2000L,
        pollIntervalMs: Long = 50L,
        condition: () -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (service.isUserInterrupted.get()) return false
            if (condition()) return true
            delay(pollIntervalMs)
        }
        return false
    }

    /**
     * 화면 내에서 대상 텍스트 또는 설명과 가장 잘 일치하는 클릭 가능 노드 검색
     * (P0 안정성: 탐색된 비매칭 노드 전수 즉시 recycle()하여 시스템 바인더 버퍼 고갈 원천 차단)
     */
    private fun findBestMatchingNode(
        service: YggdrasilAccessibilityService,
        keyword: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 1. 직접 텍스트 매칭
        val textMatches = service.findNodesByText(keyword)
        val clickable = textMatches.firstOrNull { it.isClickable }
        if (clickable != null) {
            textMatches.filter { it != clickable }.forEach { try { it.recycle() } catch (_: Exception) {} }
            return clickable
        }
        if (textMatches.isNotEmpty()) {
            val first = textMatches.first()
            textMatches.drop(1).forEach { try { it.recycle() } catch (_: Exception) {} }
            return first
        }

        // 2. 텍스트 직접 매칭 실패 시 온디맨드로 루트 노드 획득 및 DFS 조기 반환 탐색 (0-Allocation)
        val root = service.rootInActiveWindow ?: return null
        val targetLower = keyword.lowercase().replace(" ", "")

        val matchedNode = findNodeDfs(root, targetLower)
        if (matchedNode == null) {
            try {
                root.recycle()
            } catch (_: Exception) {}
        }
        return matchedNode
    }

    private fun findNodeDfs(
        node: android.view.accessibility.AccessibilityNodeInfo,
        targetLower: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName

        if (matchesTarget(text, targetLower) || matchesTarget(desc, targetLower) || matchesTarget(viewId, targetLower)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeDfs(child, targetLower)
            if (match != null) {
                return match
            }
            try {
                child.recycle()
            } catch (_: Exception) {}
        }
        return null
    }

    private fun matchesTarget(source: String?, targetLower: String): Boolean {
        if (source == null) return false
        val s = source.lowercase()
        return s.contains(targetLower) || s.replace(" ", "").contains(targetLower)
    }

    /**
     * 오토마우스 가상 터치 포인터(시각적 황금빛 리플 링) 표시
     */
    fun showVirtualPointerAt(x: Float, y: Float) {
        mainHandler.post {
            removeVirtualPointer()

            val pointerSize = 90
            val pointer = FrameLayout(context).apply {
                val circle = ImageView(context).apply {
                    val ringDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x60FFD700) // 반투명 골드
                        setStroke(6, 0xFFFFD700.toInt()) // 선명한 골드 외곽선
                    }
                    background = ringDrawable
                }
                addView(circle, FrameLayout.LayoutParams(pointerSize, pointerSize))
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                pointerSize,
                pointerSize,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = (x - pointerSize / 2).toInt()
                this.y = (y - pointerSize / 2).toInt()
            }

            try {
                windowManager.addView(pointer, params)
                touchIndicatorView = pointer

                // 0.45초 후 부드럽게 자동 제거
                mainHandler.postDelayed({
                    removeVirtualPointer()
                }, 450)
            } catch (e: Exception) {
                Log.w(TAG, "오토마우스 포인터 렌더링 실패 (권한 부재 등): ${e.message}")
            }
        }
    }

    private fun removeVirtualPointer() {
        touchIndicatorView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // 무시
            }
        }
        touchIndicatorView = null
    }

    /**
     * 인간형 부드러운 스와이프 제스처 실행
     */
    private fun performSmoothSwipe(
        service: YggdrasilAccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    /**
     * 스마트폰에 설치된 앱 이름으로 패키지 검색 후 전면 실행
     */
    fun launchAppByName(appName: String): Boolean {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val targetLower = appName.lowercase().replace(" ", "")

        for (info in apps) {
            val label = info.loadLabel(pm).toString().lowercase().replace(" ", "")
            if (label.contains(targetLower) || targetLower.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(info.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launchIntent)
                    Log.i(TAG, "🚀 [앱 실행 성공]: '${info.loadLabel(pm)}' (${info.activityInfo.packageName})")
                    return true
                }
            }
        }

        // 주요 기본 앱 패키지명 직접 매핑 (삼성 갤럭시 및 표준 안드로이드)
        val directMap = mapOf(
            "유튜브" to "com.google.android.youtube",
            "카카오톡" to "com.kakao.talk",
            "카톡" to "com.kakao.talk",
            "인스타그램" to "com.instagram.android",
            "인스타" to "com.instagram.android",
            "네이버" to "com.nhn.android.search",
            "쿠팡" to "com.coupang.mobile",
            "설정" to "com.android.settings",
            "카메라" to "com.sec.android.app.camera",
            "갤러리" to "com.sec.android.gallery3d",
            "플레이스토어" to "com.android.vending",
            "구글플레이" to "com.android.vending",
            "스토어" to "com.android.vending",
            "캘린더" to "com.samsung.android.calendar",
            "달력" to "com.samsung.android.calendar",
            "일정" to "com.samsung.android.calendar",
            "시계" to "com.sec.android.app.clockpackage",
            "알람" to "com.sec.android.app.clockpackage",
            "계산기" to "com.sec.android.app.popupcalculator",
            "메모" to "com.samsung.android.app.notes",
            "삼성노트" to "com.samsung.android.app.notes",
            "메시지" to "com.samsung.android.messaging"
        )

        for ((key, pkg) in directMap) {
            if (targetLower.contains(key)) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(intent)
                    return true
                }
            }
        }

        return false
    }

    private fun extractTargetAppName(cmd: String): String {
        var target = cmd.trim()
        val removeKeywords = listOf(
            "실행해 줘", "실행해줘", "실행해", "실행",
            "켜 줘", "켜줘", "켜",
            "열어 줘", "열어줘", "열어",
            "틀어 줘", "틀어줘", "틀어",
            "확인해 줘", "확인해줘", "확인해", "확인",
            "보여 줘", "보여줘", "보여",
            "들어가 줘", "들어가줘", "들어가",
            "띄워 줘", "띄워줘", "띄워",
            "앱", "어플"
        )
        for (kw in removeKeywords) {
            target = target.replace(kw, "")
        }
        target = target.replace("줘", "").replace("좀", "").trim()
        return target
    }

    private fun extractClickTarget(cmd: String): String {
        val keywords = listOf("클릭해줘", "클릭해", "눌러줘", "눌러", "터치해줘", "터치해")
        for (kw in keywords) {
            if (cmd.contains(kw)) {
                return cmd.replace(kw, "").replace("버튼", "").trim()
            }
        }
        return ""
    }

    private fun getScreenMetrics(): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        return Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
    }

    data class ExecutionReport(
        val success: Boolean,
        val message: String
    )
}
