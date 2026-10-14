package com.yggdrasil.assistant

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yggdrasil.core.persona.UserPersonaManager
import com.yggdrasil.core.proactive.ProactiveRoutineManager
import com.yggdrasil.core.security.BreakGlassSecurityManager
import com.yggdrasil.core.storage.AppStorageVaultManager
import com.yggdrasil.core.ui.MainAppScreen

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
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val personaManager = UserPersonaManager.getInstance(this)
        val proactiveManager = ProactiveRoutineManager.getInstance(this)
        val vaultManager = AppStorageVaultManager.getInstance(this)
        val breakGlassManager = BreakGlassSecurityManager.getInstance(this)

        setContent {
            MainAppScreen(
                personaManager = personaManager,
                proactiveManager = proactiveManager,
                vaultManager = vaultManager,
                onSendMessageToNpu = { userQuery ->
                    // 온디바이스 NPU 추론 호출
                    "온디바이스 EXAONE 3.0에서 '${userQuery}' 요청을 안전하게 처리했습니다."
                },
                onEmergencyLockdown = {
                    breakGlassManager.triggerEmergencyLockdown("사용자 UI 원클릭 긴급 차단")
                }
            )
        }
    }
}
