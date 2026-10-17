package com.yggdrasil.assistant.voice

import android.content.Intent
import android.speech.RecognitionService

/**
 * [YggdrasilVoiceRecognitionService]
 * 온디바이스 음성 인식 표준 서비스 (기본 어시스턴트 필수 컴포넌트)
 */
class YggdrasilVoiceRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // 음성 청취 개시 (Google On-Device SpeechRecognizer 또는 내장 오디오 스트림 연동)
    }

    override fun onStopListening(listener: Callback?) {
        // 청취 중단
    }

    override fun onCancel(listener: Callback?) {
        // 청취 취소
    }
}
