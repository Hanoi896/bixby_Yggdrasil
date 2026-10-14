package com.yggdrasil.core.proactive

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
import com.yggdrasil.core.persona.UserPersonaManager

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
            // 1. 배터리 부족 이벤트 감지
            Intent.ACTION_BATTERY_LOW -> {
                val batteryPct = getBatteryPercentage(context)
                val message = proactiveManager.buildBatteryCareMessage(userCallName, batteryPct)
                if (message != null) {
                    showNotification(context, "🔋 배터리 긴급 케어", message, 1001)
                }
            }

            // 2. 알람 해제 이벤트 감지 (기상 모닝 브리핑)
            "com.samsung.sec.android.clockpackage.alarm.ALARM_STOPPED",
            "com.google.android.deskclock.ALARM_DISMISSED" -> {
                val dummySchedules = listOf("오후 2:00 팀 미팅", "오후 6:30 저녁 약속")
                val dummyWeather = "맑음 (최고 24도)"
                val message = proactiveManager.buildMorningBriefing(userCallName, dummySchedules, dummyWeather)
                if (message != null) {
                    showNotification(context, "☀️ 좋은 아침입니다!", message, 1002)
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

    private fun showNotification(context: Context, title: String, content: String, notificationId: Int) {
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

        notificationManager.notify(notificationId, builder.build())
        Log.i(TAG, "선제적 알림 발행 완료: $title")
    }
}
