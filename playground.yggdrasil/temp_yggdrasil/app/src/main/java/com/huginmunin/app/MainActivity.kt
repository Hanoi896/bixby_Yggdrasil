package com.huginmunin.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.huginmunin.app.service.WakeWordService
import com.huginmunin.app.ui.HuginMuninNavGraph
import com.huginmunin.app.ui.theme.HuginMuninTheme
import com.huginmunin.app.utils.PermissionHandler
import com.huginmunin.app.utils.LanguageManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    // 권한 요청 콜백 저장
    private var onPermissionGranted: (() -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null
    
    // Wake Word 브로드캐스트 리시버
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                val wakeWord = intent.getStringExtra(WakeWordService.EXTRA_WAKE_WORD)
                // Wake Word 감지 시 챗 화면으로 자동 이동 (추후 구현 가능)
                // TODO: Navigate to chat screen automatically
            }
        }
    }
    
    override fun attachBaseContext(newBase: Context) {
        // Apply saved language on activity creation
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Wake Word 브로드캐스트 리시버 등록
        registerWakeWordReceiver()
        
        // 모든 권한 요청
        requestAllPermissions()
        
        setContent {
            HuginMuninTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDest = if (LanguageManager.isOnboardingDone(this)) {
                        "dashboard"
                    } else {
                        "onboarding"
                    }
                    HuginMuninNavGraph(
                        navController = navController,
                        startDestination = startDest
                    )
                }
            }
        }
    }
    
    private fun requestAllPermissions() {
        // Request permissions sequentially: Audio -> Calendar -> Location -> Contacts -> Notifications
        if (!PermissionHandler.Audio.hasPermissions(this)) {
            this.onPermissionGranted = { requestCalendarPermissionAfterAudio() }
            this.onPermissionDenied = { requestCalendarPermissionAfterAudio() }
            PermissionHandler.Audio.requestPermissions(this)
        } else {
            requestCalendarPermissionAfterAudio()
        }
    }
    
    private fun requestCalendarPermissionAfterAudio() {
        if (!PermissionHandler.Calendar.hasPermissions(this)) {
            this.onPermissionGranted = { requestLocationPermissionAfterCalendar() }
            this.onPermissionDenied = { requestLocationPermissionAfterCalendar() }
            PermissionHandler.Calendar.requestPermissions(this)
        } else {
            requestLocationPermissionAfterCalendar()
        }
    }
    
    private fun requestLocationPermissionAfterCalendar() {
        if (!PermissionHandler.Location.hasPermissions(this)) {
            this.onPermissionGranted = { requestContactsPermissionAfterLocation() }
            this.onPermissionDenied = { requestContactsPermissionAfterLocation() }
            PermissionHandler.Location.requestPermissions(this)
        } else {
            requestContactsPermissionAfterLocation()
        }
    }
    
    private fun requestContactsPermissionAfterLocation() {
        if (!PermissionHandler.Contacts.hasPermissions(this)) {
            this.onPermissionGranted = { requestNotificationPermissionAfterContacts() }
            this.onPermissionDenied = { requestNotificationPermissionAfterContacts() }
            PermissionHandler.Contacts.requestPermissions(this)
        } else {
            requestNotificationPermissionAfterContacts()
        }
    }
    
    private fun requestNotificationPermissionAfterContacts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHandler.hasNotificationPermission(this)) {
                this.onPermissionGranted = { startWakeWordServiceIfPermitted() }
                this.onPermissionDenied = { startWakeWordServiceIfPermitted() }
                PermissionHandler.requestNotificationPermission(this)
            } else {
                startWakeWordServiceIfPermitted()
            }
        } else {
            startWakeWordServiceIfPermitted()
        }
    }
    
    private fun startWakeWordServiceIfPermitted() {
        if (PermissionHandler.Audio.hasPermissions(this)) {
            startWakeWordService()
        }
    }
    
    private fun checkAndRequestAudioPermission() {
        if (PermissionHandler.Audio.hasPermissions(this)) {
            // 권한이 이미 있으면 바로 서비스 시작
            startWakeWordService()
        } else {
            // 권한 요청
            this.onPermissionGranted = { startWakeWordService() }
            this.onPermissionDenied = { 
                // 권한 거부 시에도 앱은 실행되지만 서비스는 시작하지 않음
            }
            PermissionHandler.Audio.requestPermissions(this)
        }
    }
    
    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun registerWakeWordReceiver() {
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, filter)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PermissionHandler.Calendar.REQUEST_CODE -> {
                PermissionHandler.Calendar.handlePermissionResult(
                    requestCode,
                    grantResults,
                    onGranted = { onPermissionGranted?.invoke() },
                    onDenied = { onPermissionDenied?.invoke() }
                )
            }
            PermissionHandler.Location.REQUEST_CODE -> {
                PermissionHandler.Location.handlePermissionResult(
                    requestCode,
                    grantResults,
                    onGranted = { onPermissionGranted?.invoke() },
                    onDenied = { onPermissionDenied?.invoke() }
                )
            }
            PermissionHandler.Audio.REQUEST_CODE -> {
                PermissionHandler.Audio.handlePermissionResult(
                    requestCode,
                    grantResults,
                    onGranted = { onPermissionGranted?.invoke() },
                    onDenied = { onPermissionDenied?.invoke() }
                )
            }
            PermissionHandler.Contacts.REQUEST_CODE -> {
                PermissionHandler.Contacts.handlePermissionResult(
                    requestCode,
                    grantResults,
                    onGranted = { onPermissionGranted?.invoke() },
                    onDenied = { onPermissionDenied?.invoke() }
                )
            }
            PermissionHandler.NOTIFICATION_REQUEST_CODE -> {
                PermissionHandler.handleNotificationPermissionResult(
                    requestCode,
                    grantResults,
                    onGranted = { onPermissionGranted?.invoke() },
                    onDenied = { onPermissionDenied?.invoke() }
                )
            }
        }
    }
    
    /**
     * 권한 요청 (외부에서 호출 가능)
     */
    fun requestCalendarPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        this.onPermissionGranted = onGranted
        this.onPermissionDenied = onDenied
        PermissionHandler.Calendar.requestPermissions(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 리시버 해제
        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (e: Exception) {
            // 이미 해제된 경우 무시
        }
    }
}
