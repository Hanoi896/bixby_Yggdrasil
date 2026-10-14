package com.yggdrasil.assistant.integration

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * NotificationCollectorService
 * Android NotificationListenerService 기반 실시간 스마트 알림 수집 및 요약기
 * 
 * 1. 카카오톡, SMS 문자메시지, Gmail 알림을 백그라운드에서 실시간 후킹
 * 2. 발신자(Sender), 메시지 본문(Text), 알림 수신 시각 구조화
 * 3. Hugin 두뇌 및 Munin 기억 저장소와 연동하여 핵심 정보 자동 브리핑
 */
class NotificationCollectorService : NotificationListenerService() {

    data class CapturedNotification(
        val packageName: String,
        val appName: String,
        val title: String,
        val content: String,
        val timestamp: Long
    )

    companion object {
        private val notificationHistory = mutableListOf<CapturedNotification>()
        private const val MAX_HISTORY = 100

        fun getRecentNotifications(): List<CapturedNotification> {
            synchronized(notificationHistory) {
                return notificationHistory.toList()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val extras = sbn.notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = when {
            pkg.contains("kakao") -> "카카오톡"
            pkg.contains("messaging") -> "문자메시지"
            pkg.contains("gm") || pkg.contains("email") -> "Gmail"
            pkg.contains("telegram") -> "텔레그램"
            else -> pkg
        }

        val captured = CapturedNotification(
            packageName = pkg,
            appName = appName,
            title = title,
            content = if (subText.isNotBlank()) "$text ($subText)" else text,
            timestamp = sbn.postTime
        )

        synchronized(notificationHistory) {
            notificationHistory.add(0, captured)
            if (notificationHistory.size > MAX_HISTORY) {
                notificationHistory.removeAt(notificationHistory.size - 1)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
