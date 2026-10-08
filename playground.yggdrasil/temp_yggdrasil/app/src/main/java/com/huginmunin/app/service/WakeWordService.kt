package com.huginmunin.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.huginmunin.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "휴긴" 또는 "뮤닌" Wake Word를 감지하는 백그라운드 서비스
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isListening = false
    
    companion object {
        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_WAKE_WORD_DETECTED = "com.huginmunin.WAKE_WORD_DETECTED"
        const val EXTRA_WAKE_WORD = "wake_word"
        
        // Wake Word 목록
        private val WAKE_WORDS = listOf(
            "휴긴", "hugin",
            "뮤닌", "munin",
            "휴긴뮤닌", "hugin munin"
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // SpeechRecognizer 초기화
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(createRecognitionListener())
            startListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "음성 호출 감지",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "\"휴긴\" 또는 \"뮤닌\" 호출을 감지합니다"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("음성 호출 대기 중")
            .setContentText("\"휴긴\" 또는 \"뮤닌\"이라고 말해보세요")
            .setSmallIcon(R.drawable.external_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startListening() {
        if (isListening) return
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            // 오류 발생 시 재시도
            serviceScope.launch {
                delay(1000)
                startListening()
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // 음성 인식 준비 완료
        }

        override fun onBeginningOfSpeech() {
            // 사용자가 말하기 시작
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 볼륨 변화 (필요 시 사용)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // 오디오 버퍼 수신
        }

        override fun onEndOfSpeech() {
            // 사용자가 말하기 종료
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            
            // 오류 후 재시작
            serviceScope.launch {
                delay(500)
                startListening()
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            
            // 인식 결과 확인
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.forEach { text ->
                if (containsWakeWord(text)) {
                    // Wake Word 감지!
                    onWakeWordDetected(text)
                    return
                }
            }
            
            // Wake Word가 없으면 계속 듣기
            serviceScope.launch {
                delay(300)
                startListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // 부분 결과에서도 Wake Word 확인 (빠른 반응)
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.forEach { text ->
                if (containsWakeWord(text)) {
                    // Wake Word 감지! (부분 결과)
                    speechRecognizer.stopListening()
                    onWakeWordDetected(text)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // 기타 이벤트
        }
    }

    /**
     * Wake Word 포함 여부 확인
     */
    private fun containsWakeWord(text: String): Boolean {
        val normalizedText = text.lowercase().trim()
        return WAKE_WORDS.any { wakeWord ->
            normalizedText.contains(wakeWord.lowercase())
        }
    }

    /**
     * Wake Word 감지 시 호출
     */
    private fun onWakeWordDetected(text: String) {
        // MainActivity에 브로드캐스트 전송
        val intent = Intent(ACTION_WAKE_WORD_DETECTED).apply {
            putExtra(EXTRA_WAKE_WORD, text)
            `package` = packageName
        }
        sendBroadcast(intent)
        
        // 알림 권한 확인 (Android 13+)
        if (!hasNotificationPermission()) {
            // 권한이 없으면 듣기만 계속
            serviceScope.launch {
                delay(2000)
                startListening()
            }
            return
        }
        
        // 알림 업데이트
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("호출 감지!")
            .setContentText("\"$text\" 감지됨")
            .setSmallIcon(R.drawable.external_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        // 2초 후 다시 듣기 시작
        serviceScope.launch {
            delay(2000)
            val defaultNotification = createNotification()
            notificationManager.notify(NOTIFICATION_ID, defaultNotification)
            startListening()
        }
    }
    
    /**
     * 알림 권한 확인 (Android 13+)
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 미만은 권한 불필요
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        speechRecognizer.destroy()
        serviceScope.cancel()
    }
}
