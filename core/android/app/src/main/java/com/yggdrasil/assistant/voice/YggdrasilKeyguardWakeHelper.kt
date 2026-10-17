package com.yggdrasil.assistant.voice

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Window
import android.view.WindowManager

/**
 * [YggdrasilKeyguardWakeHelper]
 * 화면 꺼짐(Screen-off) 및 Doze Mode 상태에서 핫워드가 감지되었을 때,
 * 시스템 키가드(Keyguard) 잠금을 안전하게 관통하고 화면을 즉각 점등(Turn-On)시키는 헬퍼.
 */
object YggdrasilKeyguardWakeHelper {

    private const val TAG = "KeyguardWakeHelper"

    /**
     * 화면이 꺼져 있을 때 화면을 강제 점등하고 3초간 WakeLock 획득
     */
    @Suppress("DEPRECATION")
    fun wakeUpScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!pm.isInteractive) {
                Log.i(TAG, "🌙 화면 꺼짐 상태 감지 -> 디스플레이 강제 점등 (WakeUp)")
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "Yggdrasil:ScreenWakeup"
                )
                wakeLock.acquire(3000L) // 3초 유지 후 자동 반환
            }
        } catch (e: Exception) {
            Log.w(TAG, "화면 깨우기 중 예외", e)
        }
    }

    /**
     * VoiceInteractionSession 또는 액티비티 윈도우에 잠금화면 위 노출 플래그 설정
     */
    fun applyShowOnKeyguardFlags(window: Window?) {
        if (window == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.attributes.let { _ ->
                // API 27+에서는 WindowManager 메서드 사용
            }
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    /**
     * 필요 시 키가드 해제 요청
     */
    fun requestDismissKeyguard(activity: Activity, onDismissed: (() -> Unit)? = null) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.i(TAG, "🔓 키가드 잠금 해제 성공")
                    onDismissed?.invoke()
                }

                override fun onDismissError() {
                    Log.w(TAG, "키가드 잠금 해제 에러")
                }

                override fun onDismissCancelled() {
                    Log.d(TAG, "키가드 잠금 해제 취소됨")
                }
            })
        }
    }
}
