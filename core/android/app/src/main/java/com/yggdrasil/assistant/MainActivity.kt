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

    // 빅스비 app-launch 딥링크로부터 전달받은 사용자 발화 쿼리 상태
    private val pendingDeepLinkQuery = mutableStateOf<String?>(null)

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
                onSendMessageToNpu = { userQuery ->
                    // 실제 온디바이스 llama.cpp 추론 호출 파이프라인
                    val model = modelSyncManager.getActiveModelFile()
                    val svc = llmService
                    if (model == null || !model.exists()) {
                        "⚠️ 온디바이스 EXAONE 모델이 아직 탑재되지 않았습니다.\n상단 [설정] 탭의 [온디바이스 AI 모델]에서 .gguf 파일을 가져와주세요."
                    } else if (svc != null) {
                        if (!svc.isRunning()) {
                            svc.startLocalDaemon(model.absolutePath)
                        }
                        svc.generate(userQuery)
                    } else {
                        "온디바이스 NPU 추론 엔진을 준비 중입니다. 잠시 후 다시 시도해 주세요."
                    }
                },
                onEmergencyLockdown = {
                    breakGlassManager.triggerEmergencyLockdown("사용자 UI 원클릭 긴급 차단")
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 앱이 이미 포그라운드/백그라운드에 떠있을 때 새 딥링크 인텐트 처리
        handleDeepLink(intent)
    }

    /**
     * 빅스비 캡슐에서 전달된 app-launch 딥링크 (yggdrasil://chat?query=...) 파싱
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme.equals("yggdrasil", ignoreCase = true)) {
            val query = uri.getQueryParameter("query")?.trim()
            if (!query.isNullOrEmpty()) {
                pendingDeepLinkQuery.value = query
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
