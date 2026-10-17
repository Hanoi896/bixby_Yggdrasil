package com.yggdrasil.assistant.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * [YggdrasilVoiceSessionService]
 * 어시스턴트 호출 시 플로팅 세션을 생성하는 서비스
 */
class YggdrasilVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return YggdrasilVoiceSession(this)
    }
}
