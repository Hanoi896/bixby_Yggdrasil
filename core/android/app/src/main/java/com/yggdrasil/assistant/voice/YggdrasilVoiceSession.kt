package com.yggdrasil.assistant.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.yggdrasil.assistant.nlp.KoreanSemanticLexicon
import com.yggdrasil.assistant.nlp.UserFeedbackLearner
import com.yggdrasil.assistant.nlp.YggdrasilContextualResolver
import com.yggdrasil.assistant.operator.AutoOperatorManager
import com.yggdrasil.assistant.rag.FactContextBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * [YggdrasilVoiceSession]
 * 갤럭시 빅스비와 동일한 1:1 플로팅 바텀 캡슐 및 실시간 음성/텍스트 멀티모달 상호작용 세션.
 * 
 * 1. 첫 번째 버튼(좌측 심볼): 클릭 시 즉각 음성 '명령어 듣는 모드'로 전환 및 재청취
 * 2. 두 번째 영역(중앙 텍스트): 탭 시 소프트 키보드가 올라오며 자유로운 텍스트 타이핑 모드로 전환
 * 3. 세 번째 영역(우측 화살표): 보라색 원형 전송 화살표(→) 버튼으로 작성된 명령어 즉시 발송
 */
class YggdrasilVoiceSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "YggdrasilVoiceSession"
    }

    private val sessionScope = CoroutineScope(Dispatchers.Main)
    private var lastCapturedScreen: Bitmap? = null
    @Volatile private var isShowingSession = false

    // UI 컴포넌트
    private var responseCard: LinearLayout? = null
    private var responseTextView: TextView? = null
    private var liveInputEditText: EditText? = null
    private var symbolButtonView: ImageView? = null
    private var sendArrowButton: ImageView? = null

    // 온디바이스 실시간 STT 엔진
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreateContentView(): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(0x55000000) // 은은한 반투명 딤 배경
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                hideSoftInput(this)
                stopSpeechRecognition()
                try {
                    YggdrasilTtsManager.getInstance(context).stop()
                } catch (_: Exception) {}
                hide()
            }
        }

        val density = context.resources.displayMetrics.density

        // 하단 플로팅 컨테이너 (바텀 마진 상향: 네비게이션 바/제스처 바를 넉넉히 회피하여 손쉽게 조작 가능한 120dp 높이 확보)
        val bottomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padBottom = (120 * density).toInt() // 약 120dp 높이로 화면 하단에서 확실하게 끌어올림
            val padHoriz = (20 * density).toInt()
            setPadding(padHoriz, 0, padHoriz, padBottom)
            setOnClickListener { /* 캡슐 내부 클릭 시 닫히지 않음 */ }
        }

        // 1. 상단 응답 카드 (초기 GONE)
        val cardDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * density
            setColor(0xFFF7F7F9.toInt()) // 빅스비 스타일 프리미엄 소프트 화이트
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            val padCardH = (20 * density).toInt()
            val padCardV = (16 * density).toInt()
            setPadding(padCardH, padCardV, padCardH, padCardV)
            visibility = View.GONE
            elevation = 16f
        }
        val respText = TextView(context).apply {
            text = "안녕하세요! 무엇을 도와드릴까요?"
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 15.5f
            setLineSpacing(10f, 1.15f)
        }
        responseTextView = respText
        card.addView(respText)
        responseCard = card

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (12 * density).toInt()
        }
        bottomContainer.addView(card, cardParams)

        // 2. 하단 입력 플로팅 캡슐 바 (멀티모달: 음성 + 키보드 타이핑 + 전송 화살표)
        val capsuleDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32 * density
            setColor(0xFFFFFFFF.toInt())
            setStroke((1.5f * density).toInt(), 0xFFE2E2E8.toInt())
        }
        val capsule = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = capsuleDrawable
            gravity = Gravity.CENTER_VERTICAL
            val padH = (14 * density).toInt()
            setPadding(padH, 0, padH, 0)
            elevation = 20f
        }

        // 🌟 [요청 1] 첫 번째 사진 버튼: 클릭 시 즉시 "음성 명령어 듣는 모드"로 전환
        val symbolDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(0xFF3875F6.toInt(), 0xFF7A5AF8.toInt()) // 블루-퍼플 그라디언트
        }
        val symbolSize = (32 * density).toInt()
        val symbolIcon = ImageView(context).apply {
            background = symbolDrawable
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(symbolSize, symbolSize).apply {
                marginEnd = (10 * density).toInt()
            }
            setOnClickListener {
                Log.i(TAG, "🎙️ 좌측 심볼 버튼 클릭 -> 음성 명령어 듣는 모드로 전환")
                hideSoftInput(this)
                liveInputEditText?.clearFocus()
                liveInputEditText?.setText("")
                liveInputEditText?.hint = "듣고 있어요... 말씀해 주세요"
                startSpeechRecognition()
            }
        }
        symbolButtonView = symbolIcon
        capsule.addView(symbolIcon)

        // 🌟 [요청 2] 두 번째 사진 영역: 탭 시 사용자가 직접 텍스트 타이핑 가능하도록 EditText 적용
        val liveInputEdit = EditText(context).apply {
            background = null // 기본 밑줄 제거하여 슬릭한 플로팅 룩 유지
            hint = "듣고 있어요... 말씀해 주세요"
            setHintTextColor(0xFF8E8E93.toInt())
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 2
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            // 사용자가 타이핑 영역을 터치하면 STT를 일시 멈추고 소프트 키보드를 즉시 띄움
            setOnClickListener {
                Log.i(TAG, "⌨️ 중앙 입력 영역 터치 -> 텍스트 타이핑 모드 진입")
                stopSpeechRecognition()
                showSoftInput(this)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    stopSpeechRecognition()
                }
            }
            // 소프트 키보드의 '전송' / '완료' 엔터키 클릭 시 즉시 실행
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    val input = text.toString().trim()
                    if (input.isNotBlank()) {
                        hideSoftInput(v)
                        stopSpeechRecognition()
                        processVoiceCommand(input)
                        true
                    } else false
                } else false
            }
        }
        liveInputEditText = liveInputEdit
        capsule.addView(liveInputEdit)

        // 🌟 [요청 3] 세 번째 & 네 번째 사진: 보라색 원형 전송 화살표(→) 버튼 추가
        val sendBtnDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF6C52EE.toInt()) // 4번째 사진과 동일한 프리미엄 퍼플
        }
        val sendSize = (34 * density).toInt()
        val sendArrow = ImageView(context).apply {
            background = sendBtnDrawable
            setImageDrawable(createSendArrowDrawable(density))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(sendSize, sendSize).apply {
                marginStart = (8 * density).toInt()
            }
            setOnClickListener {
                val typed = liveInputEditText?.text?.toString()?.trim() ?: ""
                if (typed.isNotBlank()) {
                    Log.i(TAG, "🚀 전송 화살표 버튼 클릭 -> 입력된 명령어 즉시 실행: '$typed'")
                    hideSoftInput(this)
                    stopSpeechRecognition()
                    processVoiceCommand(typed)
                } else {
                    // 입력창이 비어있을 때는 다시 음성 듣기 모드 시작
                    hideSoftInput(this)
                    startSpeechRecognition()
                }
            }
        }
        sendArrowButton = sendArrow
        capsule.addView(sendArrow)

        val capsuleHeight = (58 * density).toInt()
        val capsuleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            capsuleHeight
        )
        bottomContainer.addView(capsule, capsuleParams)

        val rootParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(bottomContainer, rootParams)

        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        isShowingSession = true
        Log.i(TAG, "🎙️ [VoiceSession 표시] 빅스비 스타일 플로팅 오버레이 전면 전개")
        
        // 🌟 Doze 모드 및 화면 꺼짐 잠금화면 위 노출 플래그 적용
        YggdrasilKeyguardWakeHelper.applyShowOnKeyguardFlags(window?.window)

        // 핫워드 엔진 마이크 독점 해제 -> SpeechRecognizer에게 마이크 완전 양도
        try {
            YggdrasilHotwordDetector.getInstance(context).stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "핫워드 마이크 양도 중 예외", e)
        }

        // 텍스트 타이핑 키보드가 캡슐 UI를 가리지 않도록 소프트 인풋 모드 설정 & 포커스 허용
        window?.window?.let { win ->
            win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        responseCard?.visibility = View.GONE
        liveInputEditText?.setText("")
        liveInputEditText?.hint = "듣고 있어요... 말씀해 주세요"
        liveInputEditText?.setTextColor(0xFF3875F6.toInt())

        // 세션 열림과 동시에 실시간 STT 엔진 구동
        startSpeechRecognition()
    }

    override fun onHide() {
        super.onHide()
        isShowingSession = false
        liveInputEditText?.let { hideSoftInput(it) }
        stopSpeechRecognition()

        // 🌟 대화 끄거나 화면 닫히면 재생 중인 TTS 즉시 차단
        try {
            YggdrasilTtsManager.getInstance(context).stop()
        } catch (e: Exception) {
            Log.w(TAG, "세션 종료 TTS 정지 실패", e)
        }

        // 세션 종료 후 백그라운드 핫워드 상시 감시 복원
        try {
            YggdrasilHotwordDetector.getInstance(context).startListening()
        } catch (e: Exception) {
            Log.w(TAG, "핫워드 백그라운드 재개 중 예외", e)
        }
    }

    override fun onDestroy() {
        try {
            YggdrasilTtsManager.getInstance(context).stop()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    /**
     * 4번째 사진 스타일의 깨끗한 벡터 화살표(→) 드로어블 동적 생성
     */
    private fun createSendArrowDrawable(density: Float): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.4f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val path = Path()

            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val stemLen = 6f * density
                val arrowHead = 4.5f * density

                path.reset()
                // 수평 축 (stem)
                path.moveTo(cx - stemLen, cy)
                path.lineTo(cx + stemLen, cy)
                // 화살표 머리 (arrowhead pointing right)
                path.moveTo(cx + stemLen - arrowHead, cy - arrowHead)
                path.lineTo(cx + stemLen, cy)
                path.lineTo(cx + stemLen - arrowHead, cy + arrowHead)

                canvas.drawPath(path, paint)
            }

            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    private fun showSoftInput(view: View) {
        try {
            view.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {
            Log.w(TAG, "키보드 표시 실패", e)
        }
    }

    private fun hideSoftInput(view: View) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (e: Exception) {
            // 무시
        }
    }

    /**
     * 삼성 One UI 및 안드로이드 순정 환경에 최적화된 온디바이스 STT 인식기 인스턴스 생성
     * (기본 선택 서비스 부재로 인한 ERROR_CLIENT 5 방어)
     */
    private fun createOptimizedSpeechRecognizer(): SpeechRecognizer {
        // 1. Google 온디바이스 음성인식 전용 컴포넌트 명시적 바인딩
        val googleTtsComponent = ComponentName(
            "com.google.android.tts",
            "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"
        )
        try {
            return SpeechRecognizer.createSpeechRecognizer(context, googleTtsComponent)
        } catch (e: Exception) {
            Log.w(TAG, "GoogleTTSRecognitionService 바인딩 실패, 대체 수단 시도", e)
        }

        // 2. Android 12+ 순정 온디바이스 음성 인식기 시도
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            try {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (e: Exception) {
                Log.w(TAG, "createOnDeviceSpeechRecognizer 실패", e)
            }
        }

        // 3. 시스템 기본 인식기
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    /**
     * 실시간 음성 스트리밍 STT 시작 (말하는 문장을 즉각 화면에 표시 + 7초 발화 유예 시간)
     */
    private fun startSpeechRecognition() {
        stopSpeechRecognition()

        sessionScope.launch(Dispatchers.Main) {
            // 🌟 세션이 이미 닫힌 경우 비동기 유령 세션 생성 원천 방어
            if (!isShowingSession) return@launch

            try {
                val recognizer = createOptimizedSpeechRecognizer()
                if (!isShowingSession) {
                    recognizer.destroy()
                    return@launch
                }

                speechRecognizer = recognizer.apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            liveInputEditText?.hint = "듣고 있어요..."
                            if (liveInputEditText?.text.isNullOrBlank()) {
                                liveInputEditText?.setText("")
                            }
                        }

                        override fun onBeginningOfSpeech() {
                            liveInputEditText?.hint = "입력 중..."
                            // 🌟 [기둥 2: 말 끊기 (Acoustic Barge-in)]
                            // 사용자가 말을 시작하는 순간 AI의 TTS 음성 출력을 즉시 중단하여 자연스러운 전이중 대화 구현
                            try {
                                YggdrasilTtsManager.getInstance(context).stop()
                            } catch (e: Exception) {
                                Log.w(TAG, "말 끊기 TTS 정지 실패", e)
                            }
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // 마이크 입력 볼륨 레벨(RMS)에 따른 심볼 아이콘 실시간 시각 반응
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.4f, 1.0f)
                            symbolButtonView?.alpha = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {
                            // 수신된 오디오 스트림 프레임 유효성 모니터링
                            if (buffer != null && buffer.isNotEmpty()) {
                                // 오디오 버퍼 정상 인입 확인
                            }
                        }

                        override fun onEndOfSpeech() {
                            liveInputEditText?.hint = "처리 중..."
                            symbolButtonView?.alpha = 1.0f
                        }

                        override fun onError(error: Int) {
                            Log.w(TAG, "음성 인식 에러 코드: $error")
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    liveInputEditText?.hint = "말씀을 듣지 못했습니다. 탭하여 직접 타이핑해 보세요."
                                    // 4초간 추가 타이핑 입력이 없으면 화면을 가리지 않도록 자동 닫힘
                                    sessionScope.launch(Dispatchers.Main) {
                                        delay(4000)
                                        if (isShowingSession && liveInputEditText?.text.isNullOrBlank()) {
                                            hide()
                                        }
                                    }
                                }
                                11 -> { // SpeechRecognizer.ERROR_ALREADY_IN_PROGRESS
                                    stopSpeechRecognition()
                                }
                                else -> {
                                    liveInputEditText?.hint = "음성을 인식하지 못했습니다. 다시 말씀해 주세요."
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognizedText = matches?.firstOrNull() ?: ""
                            if (recognizedText.isNotBlank()) {
                                // 1. 아래 입력창에 사용자가 말한 문장 최종 고정
                                liveInputEditText?.setText(recognizedText)
                                liveInputEditText?.setSelection(recognizedText.length)
                                liveInputEditText?.setTextColor(0xFF1C1C1E.toInt())

                                // 2. 상단 카드에 즉시 처리 시작
                                processVoiceCommand(recognizedText)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            // 🌟 사용자가 지금 말하고 있는 중간 단어들을 실시간으로 글자로 스트리밍 타이핑!
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partialText = matches?.firstOrNull() ?: ""
                            if (partialText.isNotBlank()) {
                                liveInputEditText?.setText(partialText)
                                liveInputEditText?.setSelection(partialText.length)
                                liveInputEditText?.setTextColor(0xFF1C1C1E.toInt())
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {
                            Log.d(TAG, "SpeechRecognizer 이벤트 수신: $eventType")
                        }
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 실시간 중간 문장 반환 필수 플래그
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    // 🌟 저지연 반응 튜닝: 발화 종료 후 1.5초 무음 감지 시 즉각 판정
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer 시작 실패", e)
                liveInputEditText?.hint = "음성 인식을 시작할 수 없습니다."
            }
        }
    }

    private fun stopSpeechRecognition() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // 무시
        }
        speechRecognizer = null
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            lastCapturedScreen = screenshot
        }
    }

    /**
     * 사용자 음성 명령 수신 후 상단 카드 모달에 결과 출력 & 자율 액션 수행
     */
    fun processVoiceCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        responseCard?.visibility = View.VISIBLE
        responseTextView?.text = "⚡ 작업을 수행하는 중입니다..."

        sessionScope.launch(Dispatchers.Default) {
            val customName = try {
                com.yggdrasil.assistant.persona.UserPersonaManager.getInstance(context).getProfile().aiName.trim()
            } catch (_: Exception) { "" }

            // 1. "자비스 카톡 보내줘", "이그드라실 홈으로 가" 등 호출 호칭 접두어 자동 정제
            var cleanCommand = trimmed
            val prefixes = mutableListOf("이그드라실아", "이그드라실야", "이그드라실", "어이 이그드라실")
            if (customName.isNotBlank()) {
                prefixes.add(0, "${customName}아")
                prefixes.add(0, "${customName}야")
                prefixes.add(0, customName)
            }
            for (prefix in prefixes) {
                if (cleanCommand.startsWith(prefix, ignoreCase = true)) {
                    cleanCommand = cleanCommand.substring(prefix.length).trim().removePrefix(",").trim()
                    break
                }
            }

            // 부르기만 하고 명령어가 없는 경우 ("자비스", "이그드라실")
            if (cleanCommand.isBlank()) {
                val callName = if (customName.isNotBlank()) customName else "이그드라실"
                withContext(Dispatchers.Main) {
                    responseTextView?.text = "네! 말씀하세요. 무엇을 도와드릴까요?"
                }
                return@launch
            }

            // 🌟 [기둥 3: 시각-기억 결합 맥락 복원기 (Contextual Resolution Engine)]
            // 화면 상의 텍스트와 대화 장기기억을 참조하여 "그거", "거기", "이 사람", "이거" 대명사 및 지시어를 실체 엔티티로 복원
            val contextualResolver = YggdrasilContextualResolver.getInstance(context)
            val contextResolvedCommand = contextualResolver.resolveContext(cleanCommand)
            Log.i(TAG, "🔍 [맥락 복원 전/후]: '$cleanCommand' -> '$contextResolvedCommand'")

            // 🌟 [기둥 3: 한국어 기초 문법 & 일상어 온톨로지 정규화 (Korean Semantic Lexicon)]
            // 온톨로지 DB(korean_action_ontology.json)를 참조하여 인메모리로 동사/명사/축약어 일괄 정규화
            val normalizedLexicon = KoreanSemanticLexicon.normalize(contextResolvedCommand, context)
            val finalCommand = normalizedLexicon.normalizedText
            Log.i(TAG, "📖 [국어 기초/일상어 정규화]: '$contextResolvedCommand' -> '$finalCommand'")

            // 2. 온디바이스 지능형 인텐트 플래너 및 실시간 TTS 엔진 연동
            val planner = YggdrasilIntentPlanner.getInstance(context)
            val plan = planner.planAction(finalCommand)
            val ttsManager = YggdrasilTtsManager.getInstance(context)

            // 복원 및 추출된 주요 엔티티를 맥락 메모리에 저장 (후속 대화에서 "그 사람", "거기" 참조 가능)
            if (plan.target.isNotBlank()) {
                contextualResolver.recordEntity(plan.target, "TARGET")
            }
            if (plan.appName.isNotBlank()) {
                contextualResolver.recordEntity(plan.appName, "APP")
            }

            Log.i(TAG, "🧠 [인텐트 플래너 결과]: intent=${plan.intent}, spoken='${plan.spokenFeedback}'")

            // 실시간 자비스 보이스 피드백 (소리 내어 즉각 대답)
            if (plan.spokenFeedback.isNotBlank()) {
                ttsManager.speak(plan.spokenFeedback)
            }

            when (plan.intent) {
                YggdrasilIntentPlanner.ActionIntent.LAUNCH_APP -> {
                    withContext(Dispatchers.Main) { hide() }
                    delay(300)
                    val ok = AutoOperatorManager.getInstance(context).launchAppByName(plan.appName)
                    if (ok) {
                        UserFeedbackLearner.getInstance(context).recordExecutedAction(plan.intent, plan.appName, "", "")
                    } else {
                        ttsManager.speak("${plan.appName} 앱을 찾을 수 없습니다.")
                    }
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.SEND_MESSAGE -> {
                    withContext(Dispatchers.Main) { hide() }
                    delay(300)
                    // 사용자 선호 앱 오버라이드 확인 (예: 엄마한테 보낼 땐 문자 선호)
                    val preferredApp = UserFeedbackLearner.getInstance(context).getPreferredApp(plan.target)
                    val actualCommand = if (preferredApp != null && !finalCommand.contains(preferredApp)) {
                        finalCommand.replace("카카오톡", preferredApp).replace("메시지", preferredApp)
                    } else {
                        finalCommand
                    }

                    val report = AutoOperatorManager.getInstance(context).executeNaturalCommand(actualCommand)
                    if (report.success) {
                        UserFeedbackLearner.getInstance(context).recordExecutedAction(plan.intent, preferredApp ?: plan.appName, plan.target, plan.content)
                        ttsManager.speak("${plan.target}님께 전송을 완료했습니다.")
                    }
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.PLAY_MEDIA -> {
                    withContext(Dispatchers.Main) { hide() }
                    delay(300)
                    val mediaQuery = plan.content
                    val mediaIntent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(if (plan.appName == "유튜브뮤직") "com.google.android.apps.youtube.music" else "com.google.android.youtube")
                        putExtra("query", mediaQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(mediaIntent)
                    } catch (_: Exception) {
                        val webUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(mediaQuery, "UTF-8"))
                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.CONTROL_DEVICE -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    when (plan.content) {
                        "VOLUME_UP" -> audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                        "VOLUME_DOWN" -> audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                        "VOLUME_MUTE" -> audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, android.media.AudioManager.FLAG_SHOW_UI)
                        "FLASHLIGHT_ON", "FLASHLIGHT_OFF" -> {
                            try {
                                val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                                camManager?.cameraIdList?.firstOrNull()?.let { camId ->
                                    camManager.setTorchMode(camId, plan.content == "FLASHLIGHT_ON")
                                }
                            } catch (e: Exception) { Log.w(TAG, "플래시 제어 예외", e) }
                        }
                    }
                    delay(1200)
                    withContext(Dispatchers.Main) { hide() }
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.USER_FEEDBACK_CORRECTION -> {
                    val learner = UserFeedbackLearner.getInstance(context)
                    val correction = learner.learnFromCorrection(cleanCommand)
                    withContext(Dispatchers.Main) {
                        responseCard?.visibility = View.VISIBLE
                        responseTextView?.text = "🧠 [자가 교정 학습 완료]\n\n${correction.message}"
                    }
                    ttsManager.speak(correction.message)

                    if (correction.shouldReexecute && !correction.newAppName.isNullOrBlank()) {
                        delay(1000)
                        withContext(Dispatchers.Main) { hide() }
                        val app = correction.newAppName
                        val target = correction.target ?: ""
                        val content = correction.content ?: ""
                        AutoOperatorManager.getInstance(context).executeSendMessageDirect(app, target, content)
                    }
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.CHAINED_APP_ACTION,
                YggdrasilIntentPlanner.ActionIntent.SYSTEM_NAV,
                YggdrasilIntentPlanner.ActionIntent.SCROLL -> {
                    withContext(Dispatchers.Main) { hide() }
                    delay(300)
                    AutoOperatorManager.getInstance(context).executeNaturalCommand(finalCommand)
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.SEARCH_WEB -> {
                    val query = plan.content.ifBlank { cleanCommand }
                    val searchEngine = com.yggdrasil.assistant.search.OnDeviceSearchGroundingEngine.getInstance()
                    val searchRes = searchEngine.searchFact(query)

                    val displayText = if (searchRes.success) {
                        "🌐 [실시간 웹 정보: ${searchRes.source}]\n\n${searchRes.snippet}"
                    } else {
                        "🌐 '${query}' 관련 검색 결과를 찾지 못했습니다."
                    }

                    withContext(Dispatchers.Main) {
                        responseCard?.visibility = View.VISIBLE
                        responseTextView?.text = displayText
                    }
                    if (searchRes.success) {
                        ttsManager.speak(searchRes.snippet)
                    }
                    return@launch
                }

                YggdrasilIntentPlanner.ActionIntent.DIRECT_ANSWER -> {
                    val factBuilder = FactContextBuilder.getInstance(context)
                    val factResult = factBuilder.buildEnrichedContext(cleanCommand)

                    val answerText: String = if (!factResult.directAnswer.isNullOrBlank()) {
                        factResult.directAnswer!!
                    } else {
                        // 🌟 100% 온디바이스 NPU 실제 지능형 추론 (하드코딩 앵무새 답변 영구 척결)
                        val modelSync = com.yggdrasil.assistant.storage.ModelAutoSyncManager.getInstance(context)
                        val model = modelSync.getActiveModelFile()
                        val llmSvc = com.yggdrasil.assistant.llm.LocalLlmService.getInstance()

                        if (model == null || !model.exists()) {
                            "⚠️ 온디바이스 EXAONE 모델이 아직 탑재되지 않았습니다. 앱 설정 탭에서 모델을 가져와주세요."
                        } else if (llmSvc != null) {
                            if (!llmSvc.isRunning()) {
                                llmSvc.startLocalDaemon(model.absolutePath)
                            }
                            llmSvc.generateWithPersona(factResult.systemPrompt, factResult.enrichedUserPrompt)
                        } else {
                            try {
                                val sIntent = Intent(context, com.yggdrasil.assistant.llm.LocalLlmService::class.java).apply {
                                    putExtra("MODEL_PATH", model.absolutePath)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(sIntent)
                                } else {
                                    context.startService(sIntent)
                                }
                            } catch (_: Exception) {}
                            "온디바이스 NPU 추론 엔진을 초기화 중입니다. 잠시 후 다시 말씀해 주세요."
                        }
                    }

                    withContext(Dispatchers.Main) {
                        responseCard?.visibility = View.VISIBLE
                        responseTextView?.text = answerText
                    }
                    ttsManager.speak(answerText)
                    return@launch
                }
            }
        }
    }
}
