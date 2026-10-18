package com.yggdrasil.assistant.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * [throttleTokenStream]
 * 120Hz 고주사율 유지를 위해 초당 수십 회 발생하는 NPU 토큰 스트리밍을
 * 33ms (약 30fps) 윈도우로 버퍼링하여 과도한 리컴포지션(Recomposition Thrashing)을 방지합니다.
 */
fun Flow<String>.throttleTokenStream(windowMs: Long = 33L): Flow<String> = channelFlow {
    val buffer = StringBuilder()
    var lastEmit = System.currentTimeMillis()

    collect { token ->
        buffer.append(token)
        val now = System.currentTimeMillis()
        if (now - lastEmit >= windowMs) {
            send(buffer.toString())
            buffer.clear()
            lastEmit = now
        }
    }
    if (buffer.isNotEmpty()) {
        send(buffer.toString())
    }
}
