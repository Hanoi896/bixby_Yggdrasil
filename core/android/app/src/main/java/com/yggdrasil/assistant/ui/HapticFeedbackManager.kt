package com.yggdrasil.assistant.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * [HapticFeedbackManager]
 * 단말기 리니어 리소네이터(햅틱 모터) 직결 물리 피드백 매니저.
 * NPU 첫 토큰 생성, ReAct 액션 완수, 보안 락다운 시 실감나는 촉각 피드백을 전달합니다.
 */
class HapticFeedbackManager private constructor(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    companion object {
        @Volatile
        private var instance: HapticFeedbackManager? = null

        fun getInstance(context: Context): HapticFeedbackManager {
            return instance ?: synchronized(this) {
                instance ?: HapticFeedbackManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * NPU 첫 토큰 수신 시 미세 틱 (Tick)
     */
    fun performTokenTick() {
        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10L)
        }
    }

    /**
     * 버튼 클릭 또는 작업 완료 시 햅틱 클릭 (Click)
     */
    fun performActionClick() {
        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25L)
        }
    }

    /**
     * 비상 락다운 및 고위험 보안 차단 시 강력한 펄스 진동
     */
    fun performHeavyAlert() {
        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 120, 80, 160)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 50, 150), -1)
        }
    }
}
