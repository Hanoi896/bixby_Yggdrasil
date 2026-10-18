package com.yggdrasil.assistant.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

/**
 * [BudsMediaButtonReceiver]
 * 갤럭시 버즈(Galaxy Buds) 및 블루투스 이어폰의 미디어 버튼/터치 패드 롱프레스 이벤트를 감지하여
 * Bluetooth SCO 오디오 라우팅을 활성화하고 화면이 꺼진 상태에서도 즉각 온디바이스 음성 비서 세션을 가동합니다.
 */
class BudsMediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BudsMediaButtonReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        Log.i(TAG, "🎧 [블루투스 미디어 버튼 인텐트 수신]: action=$action")

        if (Intent.ACTION_MEDIA_BUTTON == action) {
            @Suppress("DEPRECATION")
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_UP) {
                val keyCode = keyEvent.keyCode
                Log.i(TAG, "🎧 미디어 버튼 키코드 감지: $keyCode (반복횟수=${keyEvent.repeatCount})")

                when (keyCode) {
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        // 1. Bluetooth SCO 활성화하여 Buds 이어폰 마이크로 고감도 음성 입력 스트림 전환
                        try {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            audioManager?.apply {
                                mode = AudioManager.MODE_IN_COMMUNICATION
                                startBluetoothSco()
                                isBluetoothScoOn = true
                                Log.i(TAG, "🎙️ Galaxy Buds Bluetooth SCO 마이크 라우팅 연결 완료")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Bluetooth SCO 활성화 중 예외 발생", e)
                        }

                        // 2. 화면이 꺼져있는 경우 디스플레이 강제 점등 및 키가드 관통 플래그 선행 활성화
                        YggdrasilKeyguardWakeHelper.wakeUpScreen(context)

                        // 3. VoiceInteractionService 세션 즉각 팝업
                        val triggered = YggdrasilVoiceInteractionService.triggerSession()
                        if (!triggered) {
                            // 서비스 인스턴스가 없을 경우 인텐트를 통해 메인 세션으로 안전하게 라우팅
                            val launchIntent = Intent(context, com.yggdrasil.assistant.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("START_VOICE_SESSION", true)
                            }
                            context.startActivity(launchIntent)
                        }
                    }
                }
            }
        }
    }
}
