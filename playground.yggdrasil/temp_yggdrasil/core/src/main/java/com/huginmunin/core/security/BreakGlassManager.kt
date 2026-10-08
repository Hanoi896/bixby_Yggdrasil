package com.huginmunin.core.security

import com.huginmunin.core.repository.LogRepository
import com.huginmunin.core.repository.TrusteeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakGlassManager @Inject constructor(
    private val logRepository: LogRepository,
    private val trusteeRepository: TrusteeRepository
) {
    private val _isBreakGlassActive = MutableStateFlow(false)
    val isBreakGlassActive = _isBreakGlassActive.asStateFlow()

    suspend fun activateBreakGlass(reason: String) {
        // 1. Log the critical event
        logRepository.logEvent(
            actor = "User",
            action = "BREAK_GLASS_ACTIVATED",
            details = "Reason: $reason",
            category = "CRITICAL_SECURITY"
        )

        // 2. Notify Trustees (Mock)
        // In a real app, this would send SMS/Email/Push to all trustees immediately.
        trusteeRepository.getTrustees().collect { trustees ->
            trustees.forEach { trustee ->
                // notifyTrustee(trustee, reason)
                println("NOTIFYING TRUSTEE ${trustee.name}: User activated Break-Glass! Reason: $reason")
            }
        }

        // 3. Grant full access state
        _isBreakGlassActive.value = true
    }

    suspend fun deactivateBreakGlass() {
        logRepository.logEvent(
            actor = "User",
            action = "BREAK_GLASS_DEACTIVATED",
            details = "Returning to normal security mode",
            category = "CRITICAL_SECURITY"
        )
        _isBreakGlassActive.value = false
    }
}
