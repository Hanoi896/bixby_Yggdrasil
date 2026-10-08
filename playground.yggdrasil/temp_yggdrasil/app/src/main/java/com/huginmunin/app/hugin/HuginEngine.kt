package com.huginmunin.app.hugin

import com.huginmunin.app.draupnir.Draupnir
import com.huginmunin.app.gungnir.GungnirExecutor
import com.huginmunin.app.munin.MuninRepository
import com.huginmunin.app.sleipnir.SleipnirRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hugin: The Thought (Intelligence)
 * 사용자 의도를 파악하고 실행 계획을 수립합니다.
 */
@Singleton
class HuginEngine @Inject constructor(
    private val draupnir: Draupnir,
    private val munin: MuninRepository,
    private val gungnir: GungnirExecutor,
    private val sleipnir: SleipnirRunner
) {
    private val scope = CoroutineScope(SupervisorJob() + draupnir.brainDispatcher)

    fun processCommand(command: String) {
        scope.launch {
            munin.log("Hugin", "Thinking about: $command")
            
            // Simple Rule-based NLU (Prototype)
            when {
                command.contains("송금") || command.contains("결제") -> {
                    munin.log("Hugin", "Intent detected: PAYMENT")
                    // TODO: Extract amount and receiver from NLU
                    gungnir.executePayment(10000, "Friend")
                }
                command.contains("다운로드") || command.contains("사진") -> {
                    munin.log("Hugin", "Intent detected: DOWNLOAD")
                    sleipnir.download("https://example.com/photo.jpg")
                }
                else -> {
                    munin.log("Hugin", "Intent unclear. Asking for clarification.")
                }
            }
        }
    }
}
