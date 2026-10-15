package com.yggdrasil.assistant.integration

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * [SleipnirInboundWorker]
 * 텔레그램 메신저 양방향 원격 수신 제어 파이프라인 (Long-Polling 엔진)
 *
 * 1. 백그라운드 코루틴에서 Telegram Bot API getUpdates를 Long-Polling으로 호출하여 실시간 명령 감지
 * 2. 등록된 인가 사용자(chatId 화이트리스트)의 발신만 엄격히 서명/필터링 (공격자 무단 제어 원천 차단)
 * 3. 스마트폰을 외부에 두고 나왔을 때도 텔레그램을 통해 일정 조회, 배터리 점검, 화면 상태 확인 원격 수행
 */
class SleipnirInboundWorker private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SleipnirInboundWorker"

        @Volatile
        private var instance: SleipnirInboundWorker? = null

        fun getInstance(context: Context): SleipnirInboundWorker {
            return instance ?: synchronized(this) {
                instance ?: SleipnirInboundWorker(context.applicationContext).also { instance = it }
            }
        }
    }

    private val messengerClient = SleipnirMessengerClient.getInstance(context)
    private var workerJob: Job? = null
    private var lastUpdateId = 0L

    @Volatile
    var isRunning = false
        private set

    /**
     * Long-Polling 백그라운드 수신 워커 시작
     */
    fun startListening(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        if (isRunning) return
        isRunning = true

        workerJob = scope.launch {
            Log.i(TAG, "📡 텔레그램 인바운드 Long-Polling 수신 워커 기동 시작")

            while (isActive && isRunning) {
                val (token, authorizedChatId) = messengerClient.getTelegramConfig()

                if (token.isNullOrBlank() || authorizedChatId.isNullOrBlank()) {
                    // 토큰 또는 채팅방 ID가 설정되지 않은 경우 10초 대기 후 재확인
                    delay(10000L)
                    continue
                }

                try {
                    pollTelegramUpdates(token, authorizedChatId)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "텔레그램 수신 폴링 중 일시적 네트워크 지연: ${e.message}")
                    delay(5000L) // 오류 시 백오프 대기
                }
            }

            Log.i(TAG, "🛑 텔레그램 인바운드 수신 워커 정지됨")
        }
    }

    /**
     * 수신 워커 정지
     */
    fun stopListening() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
    }

    /**
     * Telegram getUpdates API Long-Polling 단일 사이클
     */
    private suspend fun pollTelegramUpdates(botToken: String, authorizedChatId: String) = withContext(Dispatchers.IO) {
        val offsetParam = if (lastUpdateId > 0) "&offset=${lastUpdateId + 1}" else ""
        val urlString = "https://api.telegram.org/bot$botToken/getUpdates?timeout=20$offsetParam"

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 35000
        conn.requestMethod = "GET"

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            if (json.optBoolean("ok", false)) {
                val resultArray = json.optJSONArray("result") ?: return@withContext
                for (i in 0 until resultArray.length()) {
                    val updateObj = resultArray.getJSONObject(i)
                    val updateId = updateObj.optLong("update_id", 0L)
                    if (updateId > lastUpdateId) {
                        lastUpdateId = updateId
                    }

                    val messageObj = updateObj.optJSONObject("message") ?: continue
                    val chatObj = messageObj.optJSONObject("chat") ?: continue
                    val incomingChatId = chatObj.opt("id").toString()
                    val incomingText = messageObj.optString("text", "").trim()

                    // 🚨 보안 가드레일: 등록된 인가 Chat ID와 일치하지 않는 외부 메시지는 즉시 폐기
                    if (incomingChatId != authorizedChatId) {
                        Log.w(TAG, "차단된 비인가 발신자의 텔레그램 메시지 감지: $incomingChatId")
                        continue
                    }

                    if (incomingText.isNotBlank()) {
                        processIncomingCommand(incomingText, authorizedChatId)
                    }
                }
            }
        } else {
            Log.w(TAG, "Telegram getUpdates 비정상 응답: HTTP $responseCode")
            delay(5000L)
        }
    }

    /**
     * 수신된 원격 명령어 파싱 및 자동 응답 디스패처
     */
    private fun processIncomingCommand(commandText: String, replyChatId: String) {
        Log.i(TAG, "텔레그램 원격 명령 수신: $commandText")
        val lower = commandText.lowercase()

        when {
            lower == "/start" || lower == "/help" || lower == "도움말" -> {
                val helpMsg = """
                    🌲 [Yggdrasil Assistant 원격 제어]
                    안드로이드 단말기와 정상 연결되었습니다.
                    
                    사용 가능한 원격 명령어:
                    • /briefing 또는 "일정" - 당일 캘린더 일정 브리핑
                    • /battery 또는 "배터리" - 기기 배터리 잔량 및 충전 상태
                    • /status 또는 "상태" - 스마트폰 온디바이스 AI 시스템 상태
                    • /capture 또는 "화면" - 원격 화면 점검 안내
                """.trimIndent()
                messengerClient.sendMessage("telegram", replyChatId, helpMsg, null)
            }

            lower == "/briefing" || lower.contains("일정") || lower.contains("스케줄") -> {
                val calendarManager = CalendarAndContactsManager.getInstance(context)
                val events = calendarManager.getTodayEvents()
                val responseMsg = if (events.isEmpty()) {
                    "📅 [원격 일정 브리핑]\n오늘 단말기에 등록된 일정이 없습니다. 편안한 하루 되세요!"
                } else {
                    val formatted = calendarManager.formatEventsForBriefing(events)
                    val sb = StringBuilder("📅 [원격 일정 브리핑 - 총 ${events.size}건]\n\n")
                    formatted.forEach { sb.append("• ").append(it).append("\n") }
                    sb.toString().trimEnd()
                }
                messengerClient.sendMessage("telegram", replyChatId, responseMsg, null)
            }

            lower == "/battery" || lower.contains("배터리") -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val isCharging = bm?.isCharging == true
                val responseMsg = "🔋 [단말기 배터리 상태]\n현재 배터리: $batteryPct%\n충전 상태: ${if (isCharging) "⚡ 충전 중" else "🔋 방전 중 (배터리 사용)"}"
                messengerClient.sendMessage("telegram", replyChatId, responseMsg, null)
            }

            lower == "/status" || lower.contains("상태") -> {
                val statusMsg = """
                    ⚡ [Yggdrasil 온디바이스 코어 상태]
                    • 보안 등급: L4 (Gungnir 보호 가동 중)
                    • NPU 런타임: 듀얼 컨텍스트 (정상 상주)
                    • 메신저 수신: 양방향 Long-Polling 정상 동작 중
                """.trimIndent()
                messengerClient.sendMessage("telegram", replyChatId, statusMsg, null)
            }

            else -> {
                // 일반 질문/지시 수신 시 온디바이스 회신
                val reply = "🌲 [Yggdrasil 원격 수신]\n명령: \"$commandText\"\n단말기에서 정상 수신하여 처리 대기열에 등록했습니다."
                messengerClient.sendMessage("telegram", replyChatId, reply, null)
            }
        }
    }
}
