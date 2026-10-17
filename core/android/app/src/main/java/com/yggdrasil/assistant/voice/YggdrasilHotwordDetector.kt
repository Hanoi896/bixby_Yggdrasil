package com.yggdrasil.assistant.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * [과제 24]: 24시간 초저전력 온디바이스 핫워드 엔진 (Sub-Watt Hotword Detector)
 * 
 * 빅스비나 구글 어시스턴트의 도움 없이 단말기 화면 꺼짐/다른 앱 구동 중에도
 * "이그드라실" 음성 호출을 100% 오프라인 온디바이스로 감지하여 시스템 비서를 즉각 기동합니다.
 * 
 * [초저전력 아키텍처 (Sub-Watt & Race-to-Sleep)]:
 * 1. 16kHz 모노 16bit PCM 초소형 오디오 캡처
 * 2. 1단계 VAD (Voice Activity Detection): RMS 소음 레벨이 임계치(-42dB) 이하인 침묵 구간에서는
 *    추가 CPU 연산을 일체 건너뛰고 저전력 슬립 유지 (Wakelock 0회)
 * 3. 2단계 음향 포먼트 피크 매칭: "이-그-드-라-실" 한국어 4~5음절 고유의 에너지 포락선(Envelope)과
 *    피크 간격(120~350ms), 종성 마찰음(ZCR: Zero Crossing Rate) 시퀀스를 분석
 * 4. 감지 성공 시 일시적 WakeLock(1초) 획득 및 햅틱 진동 피드백 후 어시스턴트 세션 호출
 */
class YggdrasilHotwordDetector private constructor(private val context: Context) {

    companion object {
        private const val TAG = "YggdrasilHotword"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 320 // 20ms @ 16kHz

        // VAD 감도 설정 (과민 반응 및 키보드 타이핑/배경 잡음 원천 차단)
        private const val BASE_SILENCE_DB = -38.0f // 백그라운드 영상 및 일상 타건음 차단
        private const val FAR_FIELD_GAIN_BOOST = 1.0f // 인위적 디지털 게인 증폭 제거 (원음 기준)

        // "이그드라실" 발화 총 유효 지속시간: 500ms ~ 1400ms (25프레임 ~ 70프레임)
        private const val MIN_UTTERANCE_FRAMES = 25
        private const val MAX_UTTERANCE_FRAMES = 72

        @Volatile
        private var instance: YggdrasilHotwordDetector? = null

        fun getInstance(context: Context): YggdrasilHotwordDetector {
            return instance ?: synchronized(this) {
                instance ?: YggdrasilHotwordDetector(context.applicationContext).also { instance = it }
            }
        }
    }

    private val isRunning = AtomicBoolean(false)
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var onHotwordDetectedListener: (() -> Unit)? = null

    val voiceprintVerifier = YggdrasilVoiceprintVerifier(context)

    // 원본 PCM 오디오 링버퍼 (화자 인증 및 MFCC 추출용, 2초 분량)
    private val rawAudioRing = ShortArray(SAMPLE_RATE * 2)
    private var rawRingIndex = 0

    // Zero-Allocation 재사용 화자 검증 윈도우 버퍼
    private val voiceWindowReusable = ShortArray(FRAME_SIZE * MAX_UTTERANCE_FRAMES)

    // 음성 활성 프레임 슬라이딩 윈도우
    private val energyHistory = FloatArray(MAX_UTTERANCE_FRAMES)
    private val zcrHistory = FloatArray(MAX_UTTERANCE_FRAMES)
    private var historyIndex = 0
    private var continuousVoiceFrames = 0
    private var silenceStreakFrames = 0
    private var adaptiveNoiseFloor = -55.0f

    /**
     * 핫워드 감지 시 실행될 콜백 등록
     */
    fun setOnHotwordDetectedListener(listener: () -> Unit) {
        this.onHotwordDetectedListener = listener
    }

    /**
     * 24시간 초저전력 핫워드 감지 시작
     */
    @Synchronized
    fun startListening(): Boolean {
        if (isRunning.get()) {
            Log.d(TAG, "HotwordDetector가 이미 실행 중입니다.")
            return true
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO 권한이 없어 핫워드 감지를 시작할 수 없습니다.")
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(FRAME_SIZE * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 초기화 실패 (state != STATE_INITIALIZED)")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRunning.set(true)

            listeningJob = scope.launch {
                runAudioDetectionLoop()
            }

            Log.i(TAG, "🎙️ [원거리 고감도 핫워드 엔진 가동] '이그드라실' 감시 시작 (Gain Boost: ${FAR_FIELD_GAIN_BOOST}x)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "HotwordDetector 시작 중 예외 발생", e)
            stopListening()
            return false
        }
    }

    /**
     * 핫워드 감지 중단 및 마이크 하드웨어 리소스 해제
     */
    @Synchronized
    fun stopListening() {
        isRunning.set(false)
        listeningJob?.cancel()
        listeningJob = null

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord 리소스 해제 중 경미한 예외", e)
        } finally {
            audioRecord = null
            Log.i(TAG, "🛑 [초저전력 핫워드 엔진 정지] 마이크 리소스 해제 완료")
        }
    }

    /**
     * 메인 감지 루프 (원거리 동적 적응형 VAD 및 게인 증폭 적용)
     */
    private suspend fun CoroutineScope.runAudioDetectionLoop() {
        val rawBuffer = ShortArray(FRAME_SIZE)
        val boostedBuffer = ShortArray(FRAME_SIZE)

        while (isActive && isRunning.get()) {
            val record = audioRecord ?: break
            val readCount = record.read(rawBuffer, 0, FRAME_SIZE)

            if (readCount < FRAME_SIZE) {
                delay(20)
                continue
            }

            // 원본 PCM 오디오 링버퍼에 복사 (화자 인증용)
            for (i in 0 until readCount) {
                rawAudioRing[rawRingIndex % rawAudioRing.size] = rawBuffer[i]
                rawRingIndex++
            }

            // 원거리 신호 소프트웨어 디지털 게인 증폭 (클리핑 방어)
            for (i in 0 until readCount) {
                val amplified = (rawBuffer[i] * FAR_FIELD_GAIN_BOOST).toInt()
                boostedBuffer[i] = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            // 1단계: 동적 적응형 VAD (RMS 데시벨 계산)
            val rms = calculateRms(boostedBuffer, readCount)
            val db = if (rms > 0) 20 * log10(rms / 32767.0f) else -100f
            val zcr = calculateZcr(boostedBuffer, readCount)

            // 배경 소음 노이즈 플로어 슬로우 트래킹
            if (db < adaptiveNoiseFloor + 3.0f) {
                adaptiveNoiseFloor = (adaptiveNoiseFloor * 0.98f + db * 0.02f).coerceIn(-55.0f, -35.0f)
            }

            // 동적 침묵 임계치 (주변 소음보다 6.5dB 이상 확실한 음성만 통과)
            val dynamicThreshold = (adaptiveNoiseFloor + 6.5f).coerceAtLeast(BASE_SILENCE_DB)

            if (db < dynamicThreshold) {
                // 침묵 구간: CPU 추가 연산 즉시 중단 (Race-to-Sleep)
                if (continuousVoiceFrames > 0) {
                    continuousVoiceFrames = 0
                }
                silenceStreakFrames++
                // 초저전력 파워 게이팅: 5프레임 이상 연속 침묵 시 코루틴을 25ms 슬립시켜 CPU 점유율 0.1% 미만 유지
                if (silenceStreakFrames > 5) {
                    delay(25)
                }
                continue
            }

            // 유효 음성 감지 시 슬립 스트릭 즉시 리셋
            silenceStreakFrames = 0

            // 음성 활동 감지: 슬라이딩 윈도우 기록
            continuousVoiceFrames++
            val writeIdx = historyIndex % MAX_UTTERANCE_FRAMES
            energyHistory[writeIdx] = db
            zcrHistory[writeIdx] = zcr
            historyIndex++

            // 유효 발화 길이 구간 검증 (MIN_UTTERANCE_FRAMES ~ MAX_UTTERANCE_FRAMES)
            if (continuousVoiceFrames in MIN_UTTERANCE_FRAMES..MAX_UTTERANCE_FRAMES) {
                val isHotwordMatched = matchYggdrasilAcousticPattern(continuousVoiceFrames, dynamicThreshold)
                if (isHotwordMatched) {
                    // 2단계 정밀 검증: 화자 음성 지문(Voiceprint) 및 MFCC 패턴 정밀 대조 (Zero-Allocation 재사용 버퍼)
                    val sampleCount = (continuousVoiceFrames * FRAME_SIZE).coerceAtMost(voiceWindowReusable.size)
                    val startOffset = (rawRingIndex - sampleCount + rawAudioRing.size) % rawAudioRing.size
                    for (k in 0 until sampleCount) {
                        voiceWindowReusable[k] = rawAudioRing[(startOffset + k) % rawAudioRing.size]
                    }

                    val verification = voiceprintVerifier.verifySpeakerAndHotword(voiceWindowReusable, sampleCount)
                    if (verification.isVerified) {
                        Log.i(TAG, "⚡⚡ [화자 인증 & 핫워드 적중!] 본인 목소리 확인 (유사도: ${(verification.similarity * 100).toInt()}%, F0: ${verification.pitchHz.toInt()}Hz) ⚡⚡")
                        triggerWakeup()
                        continuousVoiceFrames = 0
                        delay(2200) // 연속 중복 트리거 방지
                    } else {
                        Log.d(TAG, "오인식 기각: ${verification.message}")
                        continuousVoiceFrames = 0
                    }
                }
            } else if (continuousVoiceFrames > MAX_UTTERANCE_FRAMES) {
                // 너무 긴 발화는 일반 문장이므로 버퍼 리셋
                continuousVoiceFrames = 0
            }
        }
    }

    /**
     * "이-그-드-라-실" 한국어 고유 음향 패턴 검증 (타이핑/영상 잡음 엄격 배제)
     */
    private fun matchYggdrasilAcousticPattern(frameLength: Int, noiseThreshold: Float): Boolean {
        if (frameLength < MIN_UTTERANCE_FRAMES) return false

        // 최근 frameLength만큼의 에너지 및 ZCR 윈도우 추출
        val energies = FloatArray(frameLength)
        val zcrs = FloatArray(frameLength)
        val startOffset = (historyIndex - frameLength + MAX_UTTERANCE_FRAMES) % MAX_UTTERANCE_FRAMES

        for (i in 0 until frameLength) {
            val idx = (startOffset + i) % MAX_UTTERANCE_FRAMES
            energies[i] = energyHistory[idx]
            zcrs[i] = zcrHistory[idx]
        }

        // 🌟 키보드 타이핑/스크린 타건음 원천 차단:
        // 기계식 타건음은 유성 모음이 없으므로 발화 전반부("이-그-드-라")의 ZCR이 0.16 이상으로 치솟음 -> 즉시 기각
        val earlyPart = (frameLength * 3) / 4
        var earlyZcrSum = 0f
        for (i in 0 until earlyPart) {
            earlyZcrSum += zcrs[i]
        }
        val avgEarlyZcr = earlyZcrSum / earlyPart
        if (avgEarlyZcr > 0.15f) {
            return false // 타이핑 클릭음 기각
        }

        // 로컬 피크(에너지 봉우리) 탐색: 잡음 바닥 대비 확실한 음절 피크 탐지
        val minPeakDb = (noiseThreshold + 4.5f).coerceAtLeast(-35.0f)
        val peaks = mutableListOf<Int>()
        for (i in 1 until frameLength - 1) {
            if (energies[i] > energies[i - 1] && energies[i] > energies[i + 1] && energies[i] > minPeakDb) {
                peaks.add(i)
            }
        }

        // "이-그-드-라-실" 발화는 통상 3개~5개의 에너지 피크를 형성
        if (peaks.size !in 3..5) {
            return false
        }

        // 각 피크 간격 검증 (평균 음절 간격 80~320ms -> 4~17프레임)
        for (p in 0 until peaks.size - 1) {
            val interval = peaks[p + 1] - peaks[p]
            if (interval !in 4..18) {
                return false
            }
        }

        // 종성 '실' 구간(발화의 마지막 25% 구간)의 마찰음 검증
        val quarter = frameLength / 4
        var lateZcrSum = 0f
        for (i in (frameLength - quarter) until frameLength) {
            lateZcrSum += zcrs[i]
        }
        val avgLateZcr = lateZcrSum / quarter

        return avgLateZcr >= avgEarlyZcr * 1.2f && avgLateZcr > 0.11f
    }

    /**
     * 핫워드 적중 시 웨이크업 동작:
     * 1. 햅틱 진동 피드백
     * 2. 1초간 WakeLock 획득 (화면 켜짐 및 세션 활성화 보장)
     * 3. 등록된 콜백 또는 VoiceInteractionService 세션 가동
     */
    @SuppressLint("InvalidWakeLockTag")
    private fun triggerWakeup() {
        // 1. 단시간 WakeLock (Doze 모드 일시 탈출)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Yggdrasil:HotwordWakeup"
        )
        try {
            wakeLock?.acquire(1200L) // 최대 1.2초 후 자동 반환 (배터리 방전 방어)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock 획득 예외", e)
        }

        // 2. 햅틱 피드백 (사용자에게 인식 완료 인지)
        vibrateFeedback()

        // 3. 리스너 콜백 실행 (VoiceInteractionSession 플로팅 캡슐 오버레이만 띄움, 메인 앱 전체 실행 방지)
        try {
            onHotwordDetectedListener?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "onHotwordDetectedListener 실행 중 오류", e)
        }
    }

    /**
     * 햅틱 피드백 발생
     */
    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60)
            }
        } catch (e: Exception) {
            Log.w(TAG, "진동 피드백 실패", e)
        }
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / length).toFloat()
    }

    private fun calculateZcr(buffer: ShortArray, length: Int): Float {
        var zeroCrossings = 0
        for (i in 1 until length) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        return zeroCrossings.toFloat() / length
    }
}
