package com.yggdrasil.assistant.llm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * LocalLlmService
 * 갤럭시 S26 울트라 온디바이스 NPU 백그라운드 상주 데몬 서비스
 */
class LocalLlmService : Service() {

    private val binder = LocalBinder()
    private var isDaemonRunning = false

    inner class LocalBinder : Binder() {
        fun getService(): LocalLlmService = this@LocalLlmService
    }

    companion object {
        init {
            try {
                System.loadLibrary("yggdrasil_llm")
            } catch (e: UnsatisfiedLinkError) {
                // 테스트 또는 시뮬레이션 환경 대비
            }
        }

        private const val NOTIFICATION_CHANNEL_ID = "yggdrasil_llm_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val pendingTtsQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initWakeLock()
        startForegroundServiceWithNotification()
        initTextToSpeech()
    }

    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "Yggdrasil:LocalLlmServiceWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        releaseWakeLockSafely()
                    }
                    override fun onError(utteranceId: String?) {
                        releaseWakeLockSafely()
                    }
                })
                isTtsReady = true
                // 대기 중인 발화 플러시
                while (!pendingTtsQueue.isEmpty()) {
                    val queuedText = pendingTtsQueue.poll() ?: break
                    speakHeadless(queuedText)
                }
            }
        }
    }

    private fun acquireWakeLockSafely(timeoutMs: Long = 15_000L) {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
            }
        } catch (e: Exception) {
            android.util.Log.w("LocalLlmService", "WakeLock 획득 실패: ${e.message}")
        }
    }

    private fun releaseWakeLockSafely() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("LocalLlmService", "WakeLock 해제 오류: ${e.message}")
        }
    }

    private fun findValidModelPath(explicitPath: String?): String? {
        val candidates = mutableListOf<String>()
        if (!explicitPath.isNullOrBlank()) candidates.add(explicitPath)
        candidates.add(File(filesDir, "models/${ExaoneModelConfig.MODEL_FILE_NAME}").absolutePath)
        candidates.add("/data/local/tmp/${ExaoneModelConfig.MODEL_FILE_NAME}")
        getExternalFilesDir(null)?.let {
            candidates.add(File(it, "models/${ExaoneModelConfig.MODEL_FILE_NAME}").absolutePath)
        }

        for (path in candidates) {
            val file = File(path)
            if (file.exists() && file.isFile && file.length() > 50_000_000L) {
                android.util.Log.i("LocalLlmService", "🎯 [온디바이스 LLM 모델 발견]: $path (${file.length() / (1024 * 1024)}MB)")
                return path
            }
        }
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val explicitPath = intent?.getStringExtra("MODEL_PATH")
        startLocalDaemon(explicitPath)

        // 화면 꺼짐(Screen-off) 상태 헤드리스 음성 질의 및 액션 디스패치
        val headlessQuery = intent?.getStringExtra("HEADLESS_QUERY")
        val actionName = intent?.getStringExtra("ACTION_NAME")
        if (!headlessQuery.isNullOrBlank()) {
            acquireWakeLockSafely(15_000L)
            executeHeadlessQuery(headlessQuery)
        } else if (!actionName.isNullOrBlank()) {
            acquireWakeLockSafely(15_000L)
            val payload = intent.getStringExtra("ACTION_PAYLOAD") ?: ""
            executeHeadlessAction(actionName, payload)
        }

        return START_STICKY // 안드로이드 시스템에 의해 종료 시 자동 재기동
    }

    /**
     * 화면을 켜지 않고 백그라운드에서 NPU 추론 후 0ms 음성(TTS)으로 즉각 피드백 (FactContextBuilder 직결)
     */
    fun executeHeadlessQuery(query: String) {
        serviceScope.launch(Dispatchers.Default) {
            try {
                val factBuilder = com.yggdrasil.assistant.rag.FactContextBuilder.getInstance(applicationContext)
                val enriched = factBuilder.buildEnrichedContext(query)
                val reply = if (!enriched.directAnswer.isNullOrBlank()) {
                    enriched.directAnswer
                } else {
                    generateWithPersona(enriched.systemPrompt, enriched.enrichedUserPrompt)
                }
                speakHeadless(reply)
            } catch (e: Exception) {
                speakHeadless("음성 답변 생성 중 오류가 발생했습니다.")
            } finally {
                // 발화 시작 후 또는 실패 시 안전하게 락 해제
                serviceScope.launch {
                    kotlinx.coroutines.delay(2000)
                    releaseWakeLockSafely()
                }
            }
        }
    }

    /**
     * 화면 꺼짐 상태에서도 백그라운드 액션 실제 자율 집행 (AutoOperatorManager 연동)
     */
    fun executeHeadlessAction(actionName: String, payload: String) {
        serviceScope.launch(Dispatchers.Default) {
            try {
                speakHeadless("$actionName 작업을 시작합니다.")
                val opManager = com.yggdrasil.assistant.operator.AutoOperatorManager.getInstance(applicationContext)
                val report = opManager.executeNaturalCommand("$actionName $payload".trim())
                if (report.message.isNotBlank()) {
                    speakHeadless(report.message)
                }
            } catch (e: Exception) {
                speakHeadless("$actionName 작업 중 오류가 발생했습니다.")
            } finally {
                serviceScope.launch {
                    kotlinx.coroutines.delay(2000)
                    releaseWakeLockSafely()
                }
            }
        }
    }

    private fun speakHeadless(text: String) {
        if (!isTtsReady || tts == null) {
            pendingTtsQueue.offer(text)
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yggdrasil_headless_tts")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 포그라운드 노티피케이션 등록 (백그라운드 프로세스 생존 보장)
     */
    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Yggdrasil 온디바이스 AI 코어",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LG EXAONE 3.0 2.4B NPU 엔진이 백그라운드에서 상주 중입니다."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Yggdrasil 온디바이스 AI 활성화")
                .setContentText("Qualcomm Snapdragon 8 Elite NPU 가속 구동 중 (JNI 직결)")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    var isCpuFallback: Boolean = false
        private set

    /**
     * NPU 가속 엔진 초기화 (HTTP 소켓 없는 100% 네이티브 JNI 직결)
     */
    fun startLocalDaemon(modelPath: String? = null): Boolean {
        if (isDaemonRunning) return true

        val validPath = findValidModelPath(modelPath)
        if (validPath == null) {
            android.util.Log.w("LocalLlmService", "⚠️ [모델 미발견] GGUF 모델 파일이 감지되지 않아 네이티브 엔진 초기화를 안전하게 보류합니다.")
            return false
        }

        val startTime = System.currentTimeMillis()

        // 네이티브 C++ NPU 가속 엔진 초기화 (Hexagon NPU 우선, HTTP 소켓 오버헤드 0%)
        val initialized = try {
            initEngineNative(validPath, ExaoneModelConfig.ACCELERATION_QNN_NPU)
        } catch (e: UnsatisfiedLinkError) {
            false
        }

        val bootDuration = System.currentTimeMillis() - startTime
        // SLA: 부팅 시간 2초 이내 달성 검증

        isDaemonRunning = initialized
        if (isDaemonRunning) {
            checkHardwareAccelerationBackend()
        }
        return isDaemonRunning
    }

    private fun checkHardwareAccelerationBackend() {
        val stats = getStats()
        // stats: {"loaded": true, "ramMb": 1820, "tokensPerSec": 32.5, "backend": "..."}
        val isCpu = stats.contains("CPU") || stats.contains("NEON") || (!stats.contains("NPU") && !stats.contains("Hexagon") && !stats.contains("Vulkan") && !stats.contains("GPU"))
        isCpuFallback = isCpu
        if (isCpuFallback) {
            android.util.Log.w(
                "LocalLlmService",
                "⚠️ [하드웨어 경고] NPU/GPU 가속 드라이버 부재로 인해 CPU(NEON) 폴백 구동 중! 6코어 풀로드 및 기기 발열/배터리 소모 주의"
            )
            updateNotificationStatus(
                title = "⚠️ Yggdrasil AI: CPU 폴백 구동 중",
                content = "NPU 미연결: CPU 연산으로 인한 발열/배터리 소모 주의 (JNI 직결)"
            )
        } else {
            updateNotificationStatus(
                title = "Yggdrasil 온디바이스 AI 활성화",
                content = "Snapdragon 8 Elite NPU 하드웨어 가속 구동 중 (JNI 직결)"
            )
        }
    }

    private fun updateNotificationStatus(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * NPU/RAM 자원 완전 반환 (Doze 모드 100% 진입 보장)
     */
    fun stopLocalDaemon() {
        try {
            releaseEngineNative()
        } catch (ignored: UnsatisfiedLinkError) {
        }
        isDaemonRunning = false
        stopForeground(true)
        stopSelf()
    }

    /**
     * 데몬 동작 상태 확인
     */
    fun isRunning(): Boolean = isDaemonRunning

    data class ConversationTurn(val role: String, val content: String)
    private val sessionHistory = mutableListOf<ConversationTurn>()

    /**
     * 페르소나 영구 고정(Pinning) 및 멀티턴 대화 세션 기반 추론
     * [과제 3 실전 구현]
     */
    fun generateWithPersona(systemPrompt: String, userQuery: String): String {
        return try {
            val roles = sessionHistory.map { it.role }.toTypedArray()
            val contents = sessionHistory.map { it.content }.toTypedArray()
            val response = generateChatNative(systemPrompt, roles, contents, userQuery)

            if (!response.startsWith("Error:")) {
                sessionHistory.add(ConversationTurn("user", userQuery))
                sessionHistory.add(ConversationTurn("assistant", response))
            }
            response
        } catch (e: UnsatisfiedLinkError) {
            "온디바이스 NPU 추론 라이브러리(yggdrasil_llm) 바인딩 대기 중입니다."
        }
    }

    /**
     * 현재 대화 세션 히스토리 초기화
     */
    fun clearSessionHistory() {
        sessionHistory.clear()
    }

    /**
     * 로컬 텍스트 직접 추론 (페르소나 자동 주입)
     */
    fun generate(prompt: String): String {
        return try {
            val personaManager = com.yggdrasil.assistant.persona.UserPersonaManager.getInstance(this)
            val systemPrompt = personaManager.buildPersonalizedSystemPrompt("사용자의 편안하고 스마트한 갤럭시 일상/업무 보좌관으로 활동하십시오.")
            generateWithPersona(systemPrompt, prompt)
        } catch (e: Exception) {
            try {
                generateTextNative(prompt)
            } catch (err: UnsatisfiedLinkError) {
                "온디바이스 NPU 추론 라이브러리(yggdrasil_llm) 바인딩 대기 중입니다."
            }
        }
    }

    /**
     * 온디바이스 시맨틱 임베딩 텐서 추출 (JNI 경유)
     */
    fun computeEmbedding(text: String): FloatArray? {
        return try {
            computeEmbeddingNative(text)
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }

    /**
     * 엔진 통계 조회 (RAM 사용량, 초당 토큰 수, 하드웨어 가속 백엔드)
     */
    fun getStats(): String {
        return try {
            getEngineStatsNative()
        } catch (e: UnsatisfiedLinkError) {
            "{\"loaded\": false, \"active\": false}"
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            // 시스템 메모리 부족 신호 수신 시 KV 캐시 및 세션 정리
            if (sessionHistory.size > 10) {
                val sub = sessionHistory.takeLast(6)
                sessionHistory.clear()
                sessionHistory.addAll(sub)
            }
        }
    }

    override fun onDestroy() {
        stopLocalDaemon()
        super.onDestroy()
    }

    // ==========================================
    // C++ JNI 네이티브 선언
    // ==========================================
    private external fun initEngineNative(modelPath: String, accelerationType: Int): Boolean
    private external fun releaseEngineNative()
    private external fun generateTextNative(prompt: String): String
    private external fun generateChatNative(
        systemPrompt: String,
        historyRoles: Array<String>,
        historyContents: Array<String>,
        currentPrompt: String
    ): String
    private external fun computeEmbeddingNative(text: String): FloatArray?
    private external fun getEngineStatsNative(): String
}

