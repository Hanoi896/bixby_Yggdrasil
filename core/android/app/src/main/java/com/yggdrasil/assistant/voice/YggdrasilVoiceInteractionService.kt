package com.yggdrasil.assistant.voice

import android.content.ComponentName
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * [YggdrasilVoiceInteractionService]
 * 갤럭시 기본 디지털 어시스턴트(Default Digital Assistant) 시스템 서비스.
 * 빅스비/구글 어시스턴트를 완전히 대체하여 홈 버튼 롱프레스, 하단 모서리 스와이프 제스처 시
 * OS 레벨에서 Yggdrasil을 1순위로 호출한다.
 * 
 * 또한 [YggdrasilHotwordDetector]와 직접 결합하여 화면이 꺼진 상태에서도
 * "이그드라실" 음성 호출 시 어시스턴트 세션을 OS 레벨에서 즉각 띄운다.
 */
class YggdrasilVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceInteractionService"

        @Volatile
        private var instance: YggdrasilVoiceInteractionService? = null

        fun getInstance(): YggdrasilVoiceInteractionService? = instance

        fun triggerSession(flags: Int = VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT): Boolean {
            return instance?.let {
                it.showSession(Bundle(), flags)
                true
            } ?: false
        }
    }

    private var hotwordDetector: YggdrasilHotwordDetector? = null

    override fun onReady() {
        super.onReady()
        instance = this
        Log.i(TAG, "🌟 Yggdrasil 기본 디지털 어시스턴트 서비스 활성화 완료!")

        // 핫워드 감지기 초기화 및 리스너 등록
        hotwordDetector = YggdrasilHotwordDetector.getInstance(this).apply {
            setOnHotwordDetectedListener {
                Log.i(TAG, "🎙️ '이그드라실' 핫워드 감지 -> VoiceInteractionSession 즉시 팝업")
                val flags = VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT
                showSession(Bundle(), flags)
            }
            startListening()
        }
    }

    override fun onShutdown() {
        super.onShutdown()
        hotwordDetector?.stopListening()
        hotwordDetector = null
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "🛑 Yggdrasil 어시스턴트 서비스 종료")
    }
}

