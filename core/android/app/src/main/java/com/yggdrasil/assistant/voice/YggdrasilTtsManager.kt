package com.yggdrasil.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [YggdrasilTtsManager]
 * 온디바이스 고음질 음성 피드백(Text-to-Speech) 싱글톤 엔진.
 * 삼성 One UI 및 안드로이드 순정 고음질 한국어 음성 팩을 활용하여
 * 어시스턴트의 모든 답변과 액션 진행 상황을 지체 없이 즉각 음성 출력한다.
 */
class YggdrasilTtsManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "YggdrasilTtsManager"

        @Volatile
        private var instance: YggdrasilTtsManager? = null

        fun getInstance(context: Context): YggdrasilTtsManager {
            return instance ?: synchronized(this) {
                instance ?: YggdrasilTtsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingQueue = ConcurrentLinkedQueue<Pair<String, (() -> Unit)?>>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val callbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "한국어 TTS 데이터가 없거나 지원되지 않습니다. 기본 언어로 폴백합니다.")
                    tts?.language = Locale.getDefault()
                }

                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(playbackAttributes)

                tts?.setPitch(1.05f)
                tts?.setSpeechRate(1.05f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.i(TAG, "🔊 [TTS 발화 시작]: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.i(TAG, "🔊 [TTS 발화 완료]: $utteranceId")
                        abandonAudioFocusSafely()
                        if (utteranceId != null) {
                            callbacks.remove(utteranceId)?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS 발화 오류: $utteranceId")
                        abandonAudioFocusSafely()
                        if (utteranceId != null) {
                            callbacks.remove(utteranceId)?.invoke()
                        }
                    }
                })

                isReady = true
                Log.i(TAG, "🔊 [온디바이스 TTS 엔진 초기화 완료] 실시간 음성 피드백 가동 준비 완료")

                // 대기 중이던 음성 큐 플러시
                while (!pendingQueue.isEmpty()) {
                    val (text, callback) = pendingQueue.poll() ?: break
                    speak(text, callback)
                }
            } else {
                Log.e(TAG, "TextToSpeech 초기화 실패 (status=$status)")
            }
        }
    }

    /**
     * 지정된 텍스트를 음성으로 출력
     * @param text 출력할 문장
     * @param onDone 발화 종료 시 실행될 콜백 (선택)
     */
    @Synchronized
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) {
            onDone?.invoke()
            return
        }

        Log.i(TAG, "🔊 [speak 호출]: '$clean' (ready=$isReady)")

        if (!isReady || tts == null) {
            pendingQueue.offer(Pair(clean, onDone))
            return
        }

        requestAudioFocusSafely()

        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "yggdrasil_${System.currentTimeMillis()}"
        if (onDone != null) {
            callbacks[utteranceId] = onDone
        }
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * 인간형 대화에 최적화된 텍스트 정제기
     * (URL, 마크다운, 이모지, 출처 태그를 제거하고 장문은 핵심 1~2문장으로 압축)
     */
    private fun sanitizeForSpeech(raw: String): String {
        var text = raw
            .replace(Regex("https?://\\S+|www\\.\\S+"), "")
            .replace(Regex("[*#_`~>|\\[\\]]"), " ")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\[출처:[^\\]]+\\]"), "")
            .replace(Regex("\\(출처:[^\\)]+\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.length > 140) {
            val sentenceEndIndex = text.indexOfAny(listOf(". ", "! ", "? "), 60)
            text = if (sentenceEndIndex != -1 && sentenceEndIndex <= 140) {
                text.substring(0, sentenceEndIndex + 1)
            } else {
                text.take(125).substringBeforeLast(" ") + " 등입니다. 자세한 내용은 화면을 확인해 주세요."
            }
        }
        return text
    }

    /**
     * 발화 즉각 중단
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        abandonAudioFocusSafely()
    }

    private fun requestAudioFocusSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* 포커스 변화 수신 */ }
                    .build()

                focusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "오디오 포커스 획득 예외", e)
        }
    }

    private fun abandonAudioFocusSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
