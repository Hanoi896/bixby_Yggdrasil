package com.yggdrasil.assistant.integration

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yggdrasil.assistant.rag.MuninEmbeddingEngine
import com.yggdrasil.assistant.rag.MuninSqlCipherDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * NotificationCollectorService
 * Android NotificationListenerService 기반 실시간 스마트 알림 수집 및 암호화 영속기
 * 
 * 1. 카카오톡, SMS 문자메시지, Gmail 알림을 백그라운드에서 실시간 후킹
 * 2. 발신자(Sender), 메시지 본문(Text), 알림 수신 시각 구조화
 * 3. 프로세스 킬 시에도 소실되지 않도록 MuninSqlCipherDatabase에 비동기 암호화 INSERT
 */
class NotificationCollectorService : NotificationListenerService() {

    data class CapturedNotification(
        val id: Long = 0,
        val packageName: String,
        val appName: String,
        val title: String,
        val content: String,
        val timestamp: Long
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: MuninSqlCipherDatabase? = null

    companion object {
        private const val TAG = "NotificationCollector"
        private val memoryCache = mutableListOf<CapturedNotification>()
        private const val MAX_HISTORY = 100

        fun getRecentNotifications(): List<CapturedNotification> {
            synchronized(memoryCache) {
                return memoryCache.toList()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val db = MuninSqlCipherDatabase(applicationContext, MuninEmbeddingEngine())
            database = db
            // 초기 부팅 시 DB에서 최근 알림을 메모리 캐시로 복원
            serviceScope.launch {
                val dbList = db.loadRecentNotifications(MAX_HISTORY)
                synchronized(memoryCache) {
                    memoryCache.clear()
                    for (item in dbList) {
                        memoryCache.add(
                            CapturedNotification(
                                id = item.id,
                                packageName = item.packageName,
                                appName = item.appName,
                                title = item.title,
                                content = item.content,
                                timestamp = item.timestamp
                            )
                        )
                    }
                }
                Log.i(TAG, "DB로부터 과거 수신 알림 ${dbList.size}건 메모리 복원 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification DB 초기화 실패", e)
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

        synchronized(memoryCache) {
            memoryCache.add(0, captured)
            if (memoryCache.size > MAX_HISTORY) {
                memoryCache.removeAt(memoryCache.size - 1)
            }
        }

        // DB에 비동기 영속화 (Zero-Loss)
        serviceScope.launch {
            try {
                database?.insertNotification(
                    MuninSqlCipherDatabase.DbNotification(
                        packageName = captured.packageName,
                        appName = captured.appName,
                        title = captured.title,
                        content = captured.content,
                        timestamp = captured.timestamp
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "알림 DB 영속화 실패", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
