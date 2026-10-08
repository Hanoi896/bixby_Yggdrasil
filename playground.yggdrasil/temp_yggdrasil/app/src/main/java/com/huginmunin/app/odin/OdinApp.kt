package com.huginmunin.app.odin

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.huginmunin.app.draupnir.Draupnir
import com.huginmunin.app.munin.MuninRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Odin: The All-Father (Kernel)
 * 앱의 생명주기와 전반적인 오케스트레이션을 담당합니다.
 */
@HiltAndroidApp
class OdinApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var draupnir: Draupnir

    @Inject
    lateinit var munin: MuninRepository

    @Inject
    lateinit var crashHandler: CrashHandler

    override fun onCreate() {
        super.onCreate()
        // Genesis: 시스템 초기화
        try {
            initializeSystem()
        } catch (e: Exception) {
            // Fail gracefully - log error but don't crash during initialization
            android.util.Log.e("OdinApp", "Initialization failed", e)
        }
    }

    private fun initializeSystem() {
        // 1. Crash Handler 먼저 설정 (모든 크래시를 잡기 위해)
        crashHandler.initialize()
        
        // 2. Draupnir를 통해 자원 최적화 준비
        draupnir.optimizeResources()
        
        // 3. Munin 초기화 로그
        munin.log("Odin", "System initialized successfully", "INFO")
        munin.log("Odin", "Yggdrasil is alive - All systems operational", "INFO")
        
        // TODO: Hugin(사고) 깨우기
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
