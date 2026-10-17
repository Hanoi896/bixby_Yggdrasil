package com.yggdrasil.assistant.voice

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
        Log.i(TAG, "🎙️ [VoiceSession 표시] 빅스비 스타일 플로팅 오버레이 전면 전개")
        
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
        liveInputEditText?.let { hideSoftInput(it) }
        stopSpeechRecognition()
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
     * 실시간 음성 스트리밍 STT 시작 (말하는 문장을 즉각 화면에 표시 + 7초 발화 유예 시간)
     */
    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            liveInputEditText?.hint = "음성 인식기를 사용할 수 없습니다."
            return
        }

        stopSpeechRecognition()

        sessionScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            liveInputEditText?.hint = "듣고 있어요..."
                            if (liveInputEditText?.text.isNullOrBlank()) {
                                liveInputEditText?.setText("")
                            }
                        }

                        override fun onBeginningOfSpeech() {
                            liveInputEditText?.hint = "입력 중..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // 마이크 볼륨에 따른 반응
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            liveInputEditText?.hint = "처리 중..."
                        }

                        override fun onError(error: Int) {
                            Log.w(TAG, "음성 인식 에러 코드: $error")
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    liveInputEditText?.hint = "말씀을 듣지 못했습니다. 탭하여 직접 타이핑해 보세요."
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

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 실시간 중간 문장 반환 필수 플래그
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    // 🌟 발화 유예 시간 7초 확장: 사용자가 생각하며 멈칫해도 끊김 없이 끝까지 경청
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
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
            val lower = trimmed.lowercase()

            // 1. 화면 자율 조작 / 스마트폰 전체 앱 실행 명령 (홈, 뒤로, 스크롤, 클릭, 앱 실행, 메시지 전송, 일정 확인 등 포괄)
            val isActionCommand = lower.contains("켜") || lower.contains("열어") || lower.contains("실행") ||
                    lower.contains("내려") || lower.contains("올려") || lower.contains("스크롤") ||
                    lower.contains("클릭") || lower.contains("눌러") || lower.contains("터치") ||
                    lower.contains("뒤로") || lower.contains("홈") || lower.contains("바탕화면") ||
                    lower.contains("메인") || lower.contains("보내") || lower.contains("전송") ||
                    lower.contains("들어가") || lower.contains("가줘") || lower.contains("이동") ||
                    lower.contains("확인") || lower.contains("보여줘") || lower.contains("일정") ||
                    lower.contains("캘린더") || lower.contains("달력") || lower.contains("알람") ||
                    lower.contains("시계")

            if (isActionCommand) {
                val autoOperator = AutoOperatorManager.getInstance(context)
                val report = autoOperator.executeNaturalCommand(trimmed)

                withContext(Dispatchers.Main) {
                    responseTextView?.text = "🤖 ${report.message}"
                    // 앱 실행 및 스크롤 완료 후 1.6초 뒤 오버레이 부드럽게 닫기
                    sessionScope.launch {
                        delay(1600)
                        hide()
                    }
                }
                return@launch
            }

            // 2. 실시간 웹 팩트 검색 (Search Grounding)
            val factBuilder = FactContextBuilder.getInstance(context)
            val factResult = factBuilder.buildEnrichedContext(trimmed)

            if (factResult.directAnswer != null) {
                withContext(Dispatchers.Main) {
                    responseTextView?.text = factResult.directAnswer
                }
                return@launch
            }

            if (factResult.intent == FactContextBuilder.QueryIntent.WEB_SEARCH) {
                val searchEngine = com.yggdrasil.assistant.search.OnDeviceSearchGroundingEngine.getInstance()
                val keyword = trimmed.replace("검색해줘", "").replace("알려줘", "").replace("에 대해", "").trim()
                val searchRes = searchEngine.searchFact(keyword)

                withContext(Dispatchers.Main) {
                    if (searchRes.success) {
                        responseTextView?.text = "🌐 [실시간 웹 정보: ${searchRes.source}]\n\n${searchRes.snippet}"
                    } else {
                        responseTextView?.text = "🌐 실시간 검색 결과를 찾지 못했습니다."
                    }
                }
                return@launch
            }

            // 3. 일반 친절한 대화 응답 (사진 2와 같은 자연스러운 어시스턴트 멘트)
            withContext(Dispatchers.Main) {
                responseTextView?.text = "안녕하세요! 저는 사용자님의 스마트폰 자율 비서 Yggdrasil입니다.\n\n'유튜브 켜줘', '스크롤 내려줘', '설정 열어줘', '내일 서울 날씨 검색해줘' 처럼 무엇이든 명령해보세요."
            }
        }
    }
}
