package com.huginmunin.app.gungnir

import com.huginmunin.app.draupnir.Draupnir
import com.huginmunin.app.gungnir.skills.PaymentSkill
import com.huginmunin.app.munin.MuninRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gungnir: The Spear (Critical Executor)
 * 실패해선 안 되는 중요한 작업을 수행합니다. (금융, IoT, 보안)
 */
@Singleton
class GungnirExecutor @Inject constructor(
    private val draupnir: Draupnir,
    private val munin: MuninRepository,
    private val paymentSkill: PaymentSkill
) {
    private val scope = CoroutineScope(SupervisorJob() + draupnir.criticalDispatcher)

    fun executePayment(amount: Int, receiver: String) {
        scope.launch {
            try {
                munin.log("Gungnir", "Preparing to throw spear (Payment)...")
                val params = mapOf("amount" to amount, "receiver" to receiver)
                paymentSkill.execute(params)
            } catch (e: Exception) {
                munin.log("Gungnir", "Missed target (Error): ${e.message}", "ERROR")
                // TODO: 재시도 로직 (Retry Policy)
            }
        }
    }
}
