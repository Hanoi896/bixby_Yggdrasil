package com.yggdrasil.assistant

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.yggdrasil.assistant.integration.CalendarAndContactsManager
import com.yggdrasil.assistant.integration.SleipnirMessengerClient
import com.yggdrasil.assistant.llm.LocalLlmService
import com.yggdrasil.assistant.persona.UserPersonaManager
import com.yggdrasil.assistant.proactive.ProactiveRoutineManager
import com.yggdrasil.assistant.security.BreakGlassSecurityManager
import com.yggdrasil.assistant.storage.AppStorageVaultManager
import com.yggdrasil.assistant.storage.ModelAutoSyncManager
import com.yggdrasil.assistant.ui.MainAppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [YggdrasilApp] 앱 진입점
 */
class YggdrasilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 앱 구동 시 내부 격리 스토리지 볼트 구조 자동 마운트
        AppStorageVaultManager.getInstance(this)
    }
}

/**
 * [MainActivity] 모바일 네이티브 엔트리포인트 액티비티
 * 빅스비 캡슐 app-launch 딥링크(yggdrasil://chat?query=...)를 수신하여
 * 온디바이스 NPU(EXAONE 3.0)와 즉각적인 1:1 대화를 연결합니다.
 */
class MainActivity : ComponentActivity() {

    private var llmService: LocalLlmService? = null
    private var isBound = false
    private var otpSniffer: com.yggdrasil.assistant.security.OtpSmsAutoSniffer? = null

    // 빅스비 app-launch 딥링크로부터 전달받은 사용자 발화 쿼리 및 목표 탭 상태
    private val pendingDeepLinkQuery = mutableStateOf<String?>(null)
    private val pendingTargetTab = mutableStateOf<Int?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? LocalLlmService.LocalBinder
            llmService = binder?.getService()
            isBound = true

            // 백그라운드에서 모델 감지 후 데몬 기동
            lifecycleScope.launch(Dispatchers.IO) {
                val modelSyncManager = ModelAutoSyncManager.getInstance(this@MainActivity)
                val activeModel = modelSyncManager.getActiveModelFile()
                if (activeModel != null && activeModel.exists()) {
                    llmService?.startLocalDaemon(activeModel.absolutePath)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            llmService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val personaManager = UserPersonaManager.getInstance(this)
        val proactiveManager = ProactiveRoutineManager.getInstance(this)
        val vaultManager = AppStorageVaultManager.getInstance(this)
        val breakGlassManager = BreakGlassSecurityManager.getInstance(this)
        val modelSyncManager = ModelAutoSyncManager.getInstance(this)
        com.yggdrasil.assistant.mcp.LocalMcpRegistry.getInstance(this)

        // 화면 꺼짐(Screen-off) 및 잠금화면 상태에서 빅스비 딥링크 호출 시 안정적 화면 표시 및 잠금 해제 요청
        configureShowOnLockScreen()

        // 온디바이스 음성 피드백을 위한 TTS 초기화
        initTextToSpeech()

        // 콜드 스타트 시 인텐트 딥링크 파싱 (yggdrasil://chat?query=...)
        handleDeepLink(intent)

        // LocalLlmService 바인딩 (온디바이스 NPU 엔진 연동)
        val serviceIntent = Intent(this, LocalLlmService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 앱 기동 시:
        // 1) APK assets 내장 모델이 있다면 내부 볼트로 자동 전개 (초기 시딩)
        // 2) 앱 전용 격리 폴더에 넣어둔 새 GGUF 모델 자동 감지 & 내부 볼트 승격
        lifecycleScope.launch(Dispatchers.IO) {
            modelSyncManager.seedModelFromAssetsIfPresent()
            modelSyncManager.autoImportAvailableModels()
            val activeModel = modelSyncManager.getActiveModelFile()
            if (activeModel != null && activeModel.exists() && isBound) {
                llmService?.startLocalDaemon(activeModel.absolutePath)
            }
        }

        setContent {
            MainAppScreen(
                personaManager = personaManager,
                proactiveManager = proactiveManager,
                vaultManager = vaultManager,
                pendingQuery = pendingDeepLinkQuery.value,
                onQueryConsumed = {
                    pendingDeepLinkQuery.value = null
                },
                targetTab = pendingTargetTab.value,
                onTabConsumed = {
                    pendingTargetTab.value = null
                },
                onSendMessageToNpu = { userQuery ->
                    // 영타 오타 및 문장 부호("", ,, ?) 실시간 자동 보정
                    val enrichedUserQuery = com.yggdrasil.assistant.nlp.KoreanPunctuationEngine.enrichUserUtterance(userQuery)
                    val contextualResolver = com.yggdrasil.assistant.nlp.YggdrasilContextualResolver.getInstance(this@MainActivity)
                    val query = contextualResolver.resolveDiscourseRepair(enrichedUserQuery)
                    contextualResolver.recordLastCommand(query)

                    val planner = com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.getInstance(this@MainActivity)
                    val plan = planner.planAction(query)

                    when (plan.intent) {
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.LAUNCH_APP,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SEND_MESSAGE,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.PLAY_MEDIA,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.CONTROL_DEVICE,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.CHAINED_APP_ACTION,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SYSTEM_NAV,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SCROLL -> {
                            val operator = com.yggdrasil.assistant.operator.AutoOperatorManager.getInstance(this@MainActivity)
                            val report = operator.executeNaturalCommand(query)
                            speakFeedback(report.message)
                            "🤖 [자율 조작 집행 완료]\n\n${report.message}"
                        }

                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.USER_FEEDBACK_CORRECTION -> {
                            val learner = com.yggdrasil.assistant.nlp.UserFeedbackLearner.getInstance(this@MainActivity)
                            val correction = learner.learnFromCorrection(query)
                            speakFeedback(correction.message)
                            if (correction.shouldReexecute && !correction.newAppName.isNullOrBlank()) {
                                val op = com.yggdrasil.assistant.operator.AutoOperatorManager.getInstance(this@MainActivity)
                                op.executeSendMessageDirect(correction.newAppName, correction.target ?: "", correction.content ?: "")
                            }
                            "🧠 [자가 교정 학습 완료]\n\n${correction.message}"
                        }

                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SEARCH_WEB -> {
                            val searchEngine = com.yggdrasil.assistant.search.OnDeviceSearchGroundingEngine.getInstance()
                            val searchRes = searchEngine.searchFact(query)
                            if (searchRes.success) {
                                speakFeedback(searchRes.snippet)
                                "🌐 [실시간 웹 정보: ${searchRes.source}]\n\n${searchRes.snippet}"
                            } else {
                                "🌐 '${query}' 관련 검색 결과를 찾지 못했습니다."
                            }
                        }

                        else -> {
                            // 1. [과제 22 실전 구현] 팩트 컨텍스트 주입 & 2.4B sLLM 환각 방지 파이프라인
                            val factBuilder = com.yggdrasil.assistant.rag.FactContextBuilder.getInstance(this@MainActivity)
                            val factResult = factBuilder.buildEnrichedContext(query)

                            // 100% 팩트 데이터 직결 반환이 가능한 경우 (일정, 세계수 성장, 판테온 상태 등)
                            if (factResult.directAnswer != null) {
                                speakFeedback(factResult.directAnswer)
                                factResult.directAnswer
                            }
                            // 2. 캘린더/일정 질의 처리 (Zero-Mock 실제 쿼리 직결)
                            else if (query.contains("일정") || query.contains("캘린더") || query.contains("스케줄")) {
                                val calendarManager = CalendarAndContactsManager.getInstance(this@MainActivity)
                                val todayEvents = calendarManager.getTodayEvents()
                                val msg = if (todayEvents.isNotEmpty()) {
                                    "📅 오늘 일정 (${todayEvents.size}건):\n" + todayEvents.joinToString("\n") { "• ${it.title} (${it.timeString})" }
                                } else {
                                    "📅 오늘 등록된 일정이 없습니다."
                                }
                                speakFeedback(msg)
                                msg
                            }
                            // 3. 텔레그램 메신저 전송/테스트 질의 처리
                            else if (query.contains("텔레그램") && (query.contains("테스트") || query.contains("전송") || query.contains("보내"))) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val messengerClient = SleipnirMessengerClient.getInstance(this@MainActivity)
                                    val res = messengerClient.testTelegramConnection()
                                    if (res.success) {
                                        "✈️ [텔레그램 전송 성공]\n등록된 Telegram Bot API를 통해 테스트 메시지를 성공적으로 발송했습니다."
                                    } else {
                                        "🚨 [텔레그램 전송 실패]\n사유: ${res.detailMessage}\n[설정] 탭의 [텔레그램 봇 메신저 연동]에서 Token과 Chat ID를 확인해주세요."
                                    }
                                }
                            }
                            // 4. 온디바이스 NPU 추론 (팩트 주입 + 환각 방지 절대 가드레일 강제) - Dispatchers.Default (Zero-ANR)
                            else {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val model = modelSyncManager.getActiveModelFile()
                                    val svc = llmService
                                    if (model == null || !model.exists()) {
                                        "⚠️ 온디바이스 EXAONE 모델이 아직 탑재되지 않았습니다.\n상단 [설정] 탭의 [온디바이스 AI 모델]에서 .gguf 파일을 가져와주세요."
                                    } else if (svc != null) {
                                        if (!svc.isRunning()) {
                                            svc.startLocalDaemon(model.absolutePath)
                                        }
                                        val reply = svc.generateWithPersona(factResult.systemPrompt, factResult.enrichedUserPrompt).ifBlank {
                                            svc.generate(userQuery)
                                        }
                                        speakFeedback(reply)
                                        reply
                                    } else {
                                        "온디바이스 NPU 추론 엔진을 준비 중입니다. 잠시 후 다시 시도해 주세요."
                                    }
                                }
                            }
                        }
                    }
                },
                onSendMessageToNpuStream = { userQuery, onToken ->
                    val contextualResolver = com.yggdrasil.assistant.nlp.YggdrasilContextualResolver.getInstance(this@MainActivity)
                    val query = contextualResolver.resolveDiscourseRepair(userQuery)
                    contextualResolver.recordLastCommand(query)

                    val planner = com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.getInstance(this@MainActivity)
                    val plan = planner.planAction(query)

                    when (plan.intent) {
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.LAUNCH_APP,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SEND_MESSAGE,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.PLAY_MEDIA,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.CONTROL_DEVICE,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.CHAINED_APP_ACTION,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SYSTEM_NAV,
                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SCROLL -> {
                            val operator = com.yggdrasil.assistant.operator.AutoOperatorManager.getInstance(this@MainActivity)
                            val report = operator.executeNaturalCommand(query)
                            speakFeedback(report.message)
                            val msg = "🤖 [자율 조작 집행 완료]\n\n${report.message}"
                            onToken(msg)
                            msg
                        }

                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.USER_FEEDBACK_CORRECTION -> {
                            val learner = com.yggdrasil.assistant.nlp.UserFeedbackLearner.getInstance(this@MainActivity)
                            val correction = learner.learnFromCorrection(query)
                            speakFeedback(correction.message)
                            if (correction.shouldReexecute && !correction.newAppName.isNullOrBlank()) {
                                val op = com.yggdrasil.assistant.operator.AutoOperatorManager.getInstance(this@MainActivity)
                                op.executeSendMessageDirect(correction.newAppName, correction.target ?: "", correction.content ?: "")
                            }
                            val msg = "🧠 [자가 교정 학습 완료]\n\n${correction.message}"
                            onToken(msg)
                            msg
                        }

                        com.yggdrasil.assistant.voice.YggdrasilIntentPlanner.ActionIntent.SEARCH_WEB -> {
                            val searchEngine = com.yggdrasil.assistant.search.OnDeviceSearchGroundingEngine.getInstance()
                            val searchRes = searchEngine.searchFact(query)
                            val msg = if (searchRes.success) {
                                speakFeedback(searchRes.snippet)
                                "🌐 [실시간 웹 정보: ${searchRes.source}]\n\n${searchRes.snippet}"
                            } else {
                                "🌐 '${query}' 관련 검색 결과를 찾지 못했습니다."
                            }
                            onToken(msg)
                            msg
                        }

                        else -> {
                            val factBuilder = com.yggdrasil.assistant.rag.FactContextBuilder.getInstance(this@MainActivity)
                            val factResult = factBuilder.buildEnrichedContext(query)

                            if (factResult.directAnswer != null) {
                                speakFeedback(factResult.directAnswer)
                                onToken(factResult.directAnswer)
                                factResult.directAnswer
                            } else if (query.contains("텔레그램") && (query.contains("테스트") || query.contains("전송") || query.contains("보내"))) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val messengerClient = SleipnirMessengerClient.getInstance(this@MainActivity)
                                    val res = messengerClient.testTelegramConnection()
                                    val msg = if (res.success) {
                                        "✈️ [텔레그램 전송 성공]\n등록된 Telegram Bot API를 통해 테스트 메시지를 성공적으로 발송했습니다."
                                    } else {
                                        "🚨 [텔레그램 전송 실패]\n사유: ${res.detailMessage}\n[설정] 탭의 [텔레그램 봇 메신저 연동]에서 Token과 Chat ID를 확인해주세요."
                                    }
                                    onToken(msg)
                                    msg
                                }
                            } else {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val model = modelSyncManager.getActiveModelFile()
                                    val svc = llmService
                                    if (model == null || !model.exists()) {
                                        val msg = "⚠️ 온디바이스 EXAONE 모델이 아직 탑재되지 않았습니다.\n상단 [설정] 탭의 [온디바이스 AI 모델]에서 .gguf 파일을 가져와주세요."
                                        onToken(msg)
                                        msg
                                    } else if (svc != null) {
                                        if (!svc.isRunning()) {
                                            svc.startLocalDaemon(model.absolutePath)
                                        }
                                        val reply = svc.generateWithPersonaStream(factResult.systemPrompt, factResult.enrichedUserPrompt, onToken)
                                        speakFeedback(reply)
                                        reply
                                    } else {
                                        val msg = "온디바이스 NPU 추론 엔진을 준비 중입니다. 잠시 후 다시 시도해 주세요."
                                        onToken(msg)
                                        msg
                                    }
                                }
                            }
                        }
                    }
                },
                onEmergencyLockdown = {
                    breakGlassManager.triggerEmergencyLockdown("사용자 UI 원클릭 긴급 차단")
                }
            )
        }
    }

    private var tts: android.speech.tts.TextToSpeech? = null
    private var isTtsReady = false

    private fun configureShowOnLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun initTextToSpeech() {
        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(java.util.Locale.KOREAN)
                if (result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                    result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true
                }
            }
        }
    }

    fun speakFeedback(text: String) {
        if (isTtsReady && text.isNotBlank()) {
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "yggdrasil_tts")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 앱이 이미 포그라운드/백그라운드에 떠있을 때 새 딥링크 인텐트 처리
        handleDeepLink(intent)
    }

    private fun sanitizeDeepLinkQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        val clean = rawQuery.trim().take(300) // 비정상적인 버퍼 오버플로우 장문 컷

        // 위험 프롬프트 주입 키워드 감지 시 무효화
        val dangerousKeywords = listOf("ignore all previous instructions", "시스템 초기화", "단말 포맷", "su root", "chmod 777")
        if (dangerousKeywords.any { clean.lowercase().contains(it) }) {
            android.util.Log.e("MainActivity", "🚨 악의적 프롬프트 주입 시도 감지 및 차단: $clean")
            return null
        }
        return clean
    }

    /**
     * 빅스비 캡슐에서 전달된 app-launch 딥링크 파싱 및 탭 자동 라우팅
     * - yggdrasil://action?name=...&payload=... -> 자율 액션/플랜 검증 후 실행
     * - yggdrasil://chat?query=...  -> [대화] 탭 (0번) 및 자동 발화 전송
     * - yggdrasil://status          -> [상태] 탭 (1번) 판테온/세계수 관제 화면
     * - yggdrasil://archive?query=  -> [보관소] 탭 (2번) SQLCipher 기억 아카이브
     * - yggdrasil://settings        -> [설정] 탭 (3번) 보안 방어벽/시스템 권한
     * - yggdrasil://launch          -> [대화] 탭 (0번) 기본 실행
     */
    private fun handleDeepLink(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (action == Intent.ACTION_ASSIST || action == Intent.ACTION_VOICE_COMMAND) {
                android.util.Log.i("MainActivity", "🎙️ 시스템 어시스턴트 인텐트 수신 ($action) -> VoiceSession 및 대화 모드 연동")
                val triggered = com.yggdrasil.assistant.voice.YggdrasilVoiceInteractionService.triggerSession()
                if (!triggered) {
                    pendingTargetTab.value = 0
                }
                return
            }
        }

        val uri = intent?.data ?: return
        if (uri.scheme.equals("yggdrasil", ignoreCase = true)) {
            val host = uri.host?.lowercase()
            when (host) {
                "action" -> {
                    pendingTargetTab.value = 0
                    val actionName = sanitizeDeepLinkQuery(uri.getQueryParameter("name") ?: uri.getQueryParameter("type")) ?: "execute"
                    val payload = sanitizeDeepLinkQuery(uri.getQueryParameter("payload") ?: uri.getQueryParameter("command")) ?: ""
                    android.util.Log.i("MainActivity", "빅스비 Native Action 수신: $actionName, payload: $payload")
                    val fullCommand = if (payload.isNotBlank()) "$actionName $payload" else actionName
                    pendingDeepLinkQuery.value = fullCommand
                    speakFeedback("$actionName 작업을 검토합니다.")
                }
                "chat" -> {
                    pendingTargetTab.value = 0
                    val query = sanitizeDeepLinkQuery(uri.getQueryParameter("query"))
                    if (!query.isNullOrEmpty()) {
                        pendingDeepLinkQuery.value = query
                    }
                }
                "status" -> {
                    pendingTargetTab.value = 1
                }
                "archive" -> {
                    pendingTargetTab.value = 2
                    val query = uri.getQueryParameter("query")?.trim()
                    if (!query.isNullOrEmpty()) {
                        pendingDeepLinkQuery.value = query
                    }
                }
                "settings" -> {
                    pendingTargetTab.value = 3
                }
                "launch" -> {
                    pendingTargetTab.value = 0
                }
                else -> {
                    val query = uri.getQueryParameter("query")?.trim()
                    if (!query.isNullOrEmpty()) {
                        pendingDeepLinkQuery.value = query
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerOtpSnifferSafely()
    }

    override fun onPause() {
        super.onPause()
        tts?.stop()
        try {
            com.yggdrasil.assistant.voice.YggdrasilTtsManager.getInstance(this).stop()
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        unregisterOtpSnifferSafely()
        tts?.stop()
        try {
            com.yggdrasil.assistant.voice.YggdrasilTtsManager.getInstance(this).stop()
        } catch (_: Exception) {}
    }

    private fun registerOtpSnifferSafely() {
        if (otpSniffer == null) {
            otpSniffer = com.yggdrasil.assistant.security.OtpSmsAutoSniffer { otpCode ->
                android.util.Log.i("MainActivity", "🎯 2FA OTP 자동 추출 성공: $otpCode")
                runOnUiThread {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Yggdrasil 2FA OTP", otpCode)
                    clipboard?.setPrimaryClip(clip)
                    android.widget.Toast.makeText(this@MainActivity, "2FA 인증번호 [$otpCode] 자동 복사 완료", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            val filter = android.content.IntentFilter(android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(otpSniffer, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(otpSniffer, filter)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "OTP SMS 리시버 등록 예외", e)
            }
        }
    }

    private fun unregisterOtpSnifferSafely() {
        otpSniffer?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            otpSniffer = null
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
        try {
            com.yggdrasil.assistant.voice.YggdrasilTtsManager.getInstance(this).stop()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
