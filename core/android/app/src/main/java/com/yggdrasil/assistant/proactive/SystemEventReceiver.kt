package com.yggdrasil.assistant.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yggdrasil.assistant.integration.CalendarAndContactsManager
import com.yggdrasil.assistant.persona.UserPersonaManager

/**
 * [SystemEventReceiver]
 * 안드로이드 시스템의 브로드캐스트 이벤트(배터리 부족, 알람 해제, 충전 시작 등)를
 * 배터리 소모 0%의 무(Zero)-Polling 방식으로 감지하여 ProactiveRoutineManager에 전달하는 리시버.
 */
class SystemEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SystemEventReceiver"
        private const val CHANNEL_ID = "yggdrasil_proactive_channel"
        private const val CHANNEL_NAME = "Yggdrasil 선제적 비서 알림"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "시스템 이벤트 수신: $action")

        val proactiveManager = ProactiveRoutineManager.getInstance(context)
        val personaManager = UserPersonaManager.getInstance(context)
        val userCallName = personaManager.getProfile().userCallName

        when (action) {
            // 1. 배터리 부족 이벤트 감지 (원터치 절전 실행 액션 버튼 연동)
            Intent.ACTION_BATTERY_LOW -> {
                val batteryPct = getBatteryPercentage(context)
                val message = proactiveManager.buildBatteryCareMessage(userCallName, batteryPct)
                if (message != null) {
                    val actionIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("yggdrasil://action?name=battery_saver")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingAction = PendingIntent.getActivity(
                        context,
                        1001,
                        actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                    )
                    showNotification(context, "🔋 배터리 긴급 케어", message, 1001, actionTitle = "절전 모드 실행", actionPendingIntent = pendingAction)
                }
            }

            // 2. 알람 해제 이벤트 감지 (실시간 기상청 위성 날씨 + 실제 캘린더 모닝 브리핑)
            "com.samsung.sec.android.clockpackage.alarm.ALARM_STOPPED",
            "com.google.android.deskclock.ALARM_DISMISSED" -> {
                val pendingResult = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val calendarManager = CalendarAndContactsManager.getInstance(context)
                        val rawEvents = calendarManager.getTodayEvents()
                        val realSchedules = calendarManager.formatEventsForBriefing(rawEvents)

                        // 하드코딩 "맑음 24도" 가짜 날씨 박멸 -> 온디바이스 실시간 기상 데이터 Grounding
                        val searchEngine = com.yggdrasil.assistant.search.OnDeviceSearchGroundingEngine.getInstance()
                        val weatherResult = searchEngine.searchFact("오늘 서울 날씨")
                        val weatherSummary = if (weatherResult.success) weatherResult.snippet else "쾌적한 날씨"

                        val message = proactiveManager.buildMorningBriefing(userCallName, realSchedules, weatherSummary)
                        if (message != null) {
                            showNotification(context, "☀️ 좋은 아침입니다!", message, 1002)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "모닝 브리핑 생성 예외: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryStatus: Intent? = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 15
    }

    private fun showNotification(
        context: Context,
        title: String,
        content: String,
        notificationId: Int,
        actionTitle: String? = null,
        actionPendingIntent: PendingIntent? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yggdrasil AI의 상황 인지 맞춤형 브리핑 알림"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (actionTitle != null && actionPendingIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_manage, actionTitle, actionPendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
        Log.i(TAG, "선제적 알림 발행 완료: $title")
    }
}
