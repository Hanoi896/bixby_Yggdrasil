package com.yggdrasil.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.yggdrasil.assistant.integration.CalendarAndContactsManager
import com.yggdrasil.assistant.integration.SleipnirMessengerClient
import com.yggdrasil.assistant.permission.AppPermissionManager
import com.yggdrasil.assistant.persona.UserPersonaManager
import com.yggdrasil.assistant.persona.UserPersonaManager.SpeechTone
import com.yggdrasil.assistant.proactive.ProactiveRoutineManager
import com.yggdrasil.assistant.storage.AppStorageVaultManager
import com.yggdrasil.assistant.storage.ModelAutoSyncManager
import com.yggdrasil.assistant.voice.YggdrasilHotwordDetector
import com.yggdrasil.assistant.voice.YggdrasilVoiceprintVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [SettingsScreen] - Black & White 극도의 미니멀리즘 디자인
 * 갤럭시 S26 Ultra의 AMOLED 디스플레이에 최적화된 Pure Black & Crisp White 테마.
 * 1. 시스템 필수 권한 관리 허브 (접근성, 알림, 캘린더 등 원클릭 허용)
 * 2. 내 맞춤 페르소나 (호칭, AI 이름, 말투)
 * 3. 선제적 능동 비서 On/Off 및 세부 루틴
 * 4. 프라이버시 쉴드 (시크릿 모드)
 * 5. 격리 스토리지 볼트 & AI 모델 관리 (SAF 파일 선택기 / 앱 전용 폴더 동기화)
 * 6. 긴급 비상 차단 (Break-Glass)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    personaManager: UserPersonaManager,
    proactiveManager: ProactiveRoutineManager,
    vaultManager: AppStorageVaultManager,
    onEmergencyLockdown: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val permissionManager = remember { AppPermissionManager.getInstance(context) }
    var permissions by remember { mutableStateOf(permissionManager.checkAllPermissions()) }

    // 페르소나 상태
    val profile = remember { personaManager.getProfile() }
    var userCallName by remember { mutableStateOf(profile.userCallName) }
    var aiName by remember { mutableStateOf(profile.aiName) }
    var selectedTone by remember { mutableStateOf(profile.tone) }

    // 능동 비서 상태
    val proactiveConfig = remember { proactiveManager.getConfig() }
    var isProactiveMaster by remember { mutableStateOf(proactiveConfig.isMasterEnabled) }
    var enableMorning by remember { mutableStateOf(proactiveConfig.enableMorningBriefing) }
    var enableBatteryCare by remember { mutableStateOf(proactiveConfig.enableLowBatteryCare) }
    var enableCommute by remember { mutableStateOf(proactiveConfig.enableCommuteRoutine) }

    // 프라이버시 상태 (Munin 영구 SharedPreferences 연동)
    val muninManager = remember { com.yggdrasil.assistant.rag.MuninRagManager.getInstance(context) }
    var isLearningPaused by remember { mutableStateOf(muninManager.isLearningPaused()) }

    // 고유 화자 인증 (음성 지문 캘리브레이션) 상태
    val hotwordDetector = remember { YggdrasilHotwordDetector.getInstance(context) }
    val voiceVerifier = remember { hotwordDetector.voiceprintVerifier }
    var isVoiceCalibrated by remember { mutableStateOf(voiceVerifier.isCalibrated()) }
    var currentCalibrationStep by remember { mutableIntStateOf(1) }
    var isRecordingCalibration by remember { mutableStateOf(false) }
    var calibrationFeedback by remember { mutableStateOf<String?>(null) }

    // 메신저(Sleipnir) 및 캘린더 매니저 상태
    val messengerClient = remember { SleipnirMessengerClient.getInstance(context) }
    val calendarManager = remember { CalendarAndContactsManager.getInstance(context) }
    val (savedTgToken, savedTgChatId) = remember { messengerClient.getTelegramConfig() }
    var tgBotToken by remember { mutableStateOf(savedTgToken ?: "") }
    var tgChatId by remember { mutableStateOf(savedTgChatId ?: "") }
    var tgFeedback by remember { mutableStateOf<String?>(null) }
    var isTestingTelegram by remember { mutableStateOf(false) }

    var todayEvents by remember { mutableStateOf(calendarManager.getTodayEvents()) }
    var calendarFeedback by remember { mutableStateOf<String?>(null) }

    // 스토리지 및 모델 관리 상태
    val modelSyncManager = remember { ModelAutoSyncManager.getInstance(context) }
    var modelStatus by remember { mutableStateOf(modelSyncManager.getStatus()) }
    var storageSummary by remember { mutableStateOf(vaultManager.getStorageUsageSummary()) }
    var isCleaned by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<Int?>(null) }
    var importFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // SAF 시스템 파일 선택기 런처
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            importProgress = 0
            importFeedbackMessage = "SAF 스트림을 통해 내부 볼트로 안전 복사 중..."
            coroutineScope.launch {
                val ok = modelSyncManager.importModelFromUri(uri) { percent ->
                    importProgress = percent
                }
                isImporting = false
                importProgress = null
                modelStatus = modelSyncManager.getStatus()
                storageSummary = vaultManager.getStorageUsageSummary()
                importFeedbackMessage = if (ok) {
                    "🎉 SAF 모델 가져오기 성공! 즉시 온디바이스 NPU 추론에 활성화되었습니다."
                } else {
                    "🚨 모델 가져오기 실패: GGUF 형식 또는 파일 크기(500MB 이상)를 확인하세요."
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "설정",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF000000)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // [카드 0] 단독 Android AI 어시스턴트 일원화 (Google Gemini / ChatGPT 방식)
            val isDefaultAssistant = remember(permissions) {
                permissionManager.isDefaultAssistantServiceEnabled()
            }
            MonoCard(title = "기본 디지털 어시스턴트 단일화") {
                Text(
                    "구글 제미나이나 ChatGPT처럼 안드로이드 순정 AI 비서(VoiceInteractionService)로 일원화되어 작동합니다.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDefaultAssistant) "● 기본 어시스턴트로 활성화됨" else "○ 기본 어시스턴트 미지정",
                            color = if (isDefaultAssistant) Color(0xFF4CAF50) else Color(0xFFFFD700),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDefaultAssistant) 
                                "홈 버튼 길게 누르기, 측면 전원 키, 하단 스와이프 시 이그드라실이 즉시 기동됩니다." 
                            else 
                                "원클릭 설정으로 이그드라실을 기본 비서로 등록하세요.",
                            color = Color(0xFF777777),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .clickable {
                                val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "One UI 설정",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "💡 팁: 갤럭시 설정 > 유용한 기능 > 측면 버튼에서 '길게 누르기'를 기본 어시스턴트 호출로 연결할 수 있습니다.",
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            // [카드 1] 필수 시스템 권한 상태 (핵심 신규 추가)
            MonoCard(title = "스마트폰 필수 권한 상태") {
                Text(
                    "화면 자동 조작과 일정/알림 연동에 필요한 권한들입니다.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                permissions.forEach { perm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    perm.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (perm.isCritical) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "필수",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                perm.description,
                                color = Color(0xFF777777),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }

                        if (perm.isGranted) {
                            Text(
                                "허용됨",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val intent = permissionManager.createSettingIntent(perm)
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "설정하기",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Divider(color = Color(0xFF1A1A1A), modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            // [카드] 고유 화자 인증 (3문장 성문 등록)
            MonoCard(title = "고유 화자 인증 (음성 지문 등록)") {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVoiceCalibrated) "● 본인 인증 활성화" else "○ 미등록 (표준 모드)",
                            color = if (isVoiceCalibrated) Color(0xFFFFD700) else Color(0xFF888888),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (isVoiceCalibrated) {
                            Text(
                                "초기화",
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable {
                                        voiceVerifier.resetVoiceprint()
                                        isVoiceCalibrated = false
                                        currentCalibrationStep = 1
                                        calibrationFeedback = "음성 프로필이 초기화되었습니다. 다시 등록해주세요."
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val displayCallName = if (aiName.isNotBlank()) aiName else "이그드라실"
                    Text(
                        text = "사용자 고유의 성대 음역대(F0 피치)와 24차원 성도 동특성을 10문장으로 정밀 학습하여, 말투·억양까지 Munin 암호화 영구 기억소에 안전하게 각인합니다.",
                        color = Color(0xFF777777),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 10문장 고정밀 생체 음향 캘리브레이션 탭
                    val sentences = listOf(
                        displayCallName,                              // 1. 단일 AI 호출어
                        "이그드라실 도와줘",                           // 2. 기본 서브 호출
                        "오늘 날씨는 어때?",                           // 3. 의문문 상승조 억양
                        "화면 아래로 스크롤해줘",                      // 4. 일상 제어 명령
                        "카톡 들어가서 메시지 확인해",                 // 5. 복합 연쇄 지시문
                        "볼륨 조금만 키워줘",                          // 6. 음량/설정 조정
                        "내일 오전 열 시에 알람 맞춰줘",              // 7. 시간/일정 설정
                        "가장 가까운 편의점 어디야?",                  // 8. 위치/정보 질의
                        "오늘 하루도 정말 수고 많았어",                // 9. 감정/부드러운 평서문
                        "이제 홈 화면으로 돌아가줘"                    // 10. 단호한 확정 명령
                    )
                    sentences.forEachIndexed { index, sentence ->
                        val step = index + 1
                        val isCurrent = step == currentCalibrationStep && !isVoiceCalibrated
                        val isDone = isVoiceCalibrated || step < currentCalibrationStep

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (isCurrent) Color(0xFF1E1E1E) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isDone) "✓ " else "$step. ",
                                color = if (isDone) Color(0xFFFFD700) else if (isCurrent) Color.White else Color(0xFF555555),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "\"$sentence\"",
                                color = if (isDone) Color(0xFFAAAAAA) else if (isCurrent) Color.White else Color(0xFF555555),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isVoiceCalibrated) {
                        Button(
                            onClick = {
                                if (isRecordingCalibration) return@Button
                                isRecordingCalibration = true
                                calibrationFeedback = "🎙️ ${currentCalibrationStep}/10단계 발화 듣는 중... 말씀해주세요."

                                coroutineScope.launch {
                                    val sample = recordVoiceSample(context)
                                    isRecordingCalibration = false
                                    if (sample != null && sample.isNotEmpty()) {
                                        val result = voiceVerifier.addCalibrationSample(sample, sample.size)
                                        calibrationFeedback = result.message
                                        if (result.isCompleted) {
                                            isVoiceCalibrated = true
                                            currentCalibrationStep = 1
                                        } else {
                                            currentCalibrationStep = (YggdrasilVoiceprintVerifier.CALIBRATION_TOTAL_STEPS + 1 - result.remainingCount)
                                                .coerceIn(1, YggdrasilVoiceprintVerifier.CALIBRATION_TOTAL_STEPS)
                                        }
                                    } else {
                                        calibrationFeedback = "⚠️ 마이크 녹음 실패. 권한을 확인해주세요."
                                    }
                                }
                            },
                            enabled = !isRecordingCalibration,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecordingCalibration) Color(0xFF333333) else Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text(
                                text = if (isRecordingCalibration) "🎙️ 24차원 음향 분석 중..." else "🎙️ ${currentCalibrationStep}/10단계 녹음하고 등록",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    calibrationFeedback?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = if (msg.contains("완료") || msg.contains("성공")) Color(0xFFFFD700) else Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // [카드 2] 내 맞춤 페르소나
            MonoCard(title = "내 맞춤 페르소나") {
                OutlinedTextField(
                    value = userCallName,
                    onValueChange = {
                        userCallName = it
                        personaManager.completeOnboarding(it, aiName, selectedTone)
                    },
                    label = { Text("호칭 (예: 지훈이 형, 대표님)", color = Color(0xFF888888)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = aiName,
                    onValueChange = {
                        aiName = it
                        personaManager.completeOnboarding(userCallName, it, selectedTone)
                    },
                    label = { Text("AI 이름 (기본: 이그드라실)", color = Color(0xFF888888)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("대화 말투", color = Color(0xFF888888), fontSize = 12.sp)

                Column(modifier = Modifier.padding(top = 4.dp)) {
                    SpeechTone.values().forEach { tone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTone = tone
                                    personaManager.completeOnboarding(userCallName, aiName, tone)
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = (selectedTone == tone),
                                onClick = {
                                    selectedTone = tone
                                    personaManager.completeOnboarding(userCallName, aiName, tone)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.White,
                                    unselectedColor = Color(0xFF555555)
                                )
                            )
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                Text(tone.displayName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(tone.description, color = Color(0xFF777777), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // [카드 3] 선제적 능동 비서
            MonoCard(title = "선제적 능동 비서") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("능동 비서 마스터 스위치", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("먼저 상황을 인지해 말 걸기", color = Color(0xFF777777), fontSize = 11.sp)
                    }
                    Switch(
                        checked = isProactiveMaster,
                        onCheckedChange = {
                            isProactiveMaster = it
                            proactiveManager.setMasterEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF222222)
                        )
                    )
                }

                if (isProactiveMaster) {
                    Divider(color = Color(0xFF1A1A1A), modifier = Modifier.padding(vertical = 8.dp))

                    MonoToggleItem("기상 모닝 브리핑", "아침 알람 해제 시 오늘 일정 요약", enableMorning) {
                        enableMorning = it
                        proactiveManager.updateRoutineSettings(enableMorningBriefing = it)
                    }

                    MonoToggleItem("배터리 긴급 케어", "15% 이하 시 절전 및 백그라운드 정리 제안", enableBatteryCare) {
                        enableBatteryCare = it
                        proactiveManager.updateRoutineSettings(enableLowBatteryCare = it)
                    }

                    MonoToggleItem("퇴근/귀가 스마트홈 루틴", "귀가 시 조명 및 에어컨 가동 제안", enableCommute) {
                        enableCommute = it
                        proactiveManager.updateRoutineSettings(enableCommuteRoutine = it)
                    }
                }
            }

            // [카드 3-1] 실제 캘린더 일정 동기화 (Google Calendar Provider)
            MonoCard(title = "실제 캘린더 일정 동기화 (Google Calendar)") {
                Text(
                    "단말기 순정 CalendarContract를 통해 구글/삼성 캘린더와 실시간 동기화됩니다.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (todayEvents.isEmpty()) {
                    Text(
                        "• 오늘 예정된 캘린더 일정이 없습니다. (또는 캘린더 권한 허용 대기 중)",
                        color = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                } else {
                    todayEvents.forEach { event ->
                        val timeStr = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREAN).format(java.util.Date(event.startTimeMillis))
                        Text(
                            "• $timeStr  ${event.title}${if (event.location.isNotBlank()) " (${event.location})" else ""}",
                            color = Color(0xFFEEEEEE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            todayEvents = calendarManager.getTodayEvents()
                            calendarFeedback = "캘린더 일정 ${todayEvents.size}건 동기화 완료"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
                    ) {
                        Text("일정 새로고침", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            val oneHourLater = now + 3600000L
                            val twoHourLater = oneHourLater + 3600000L
                            val newId = calendarManager.insertEvent(
                                title = "[Yggdrasil] 어시스턴트 테스트 일정",
                                startTimeMillis = oneHourLater,
                                endTimeMillis = twoHourLater,
                                location = "이그드라실 보안 룸",
                                description = "단말기 실제 캘린더 Provider 연동 테스트입니다."
                            )
                            if (newId != -1L) {
                                todayEvents = calendarManager.getTodayEvents()
                                calendarFeedback = "✅ 테스트 일정이 캘린더 DB에 성공적으로 등록되었습니다 (ID: $newId)"
                            } else {
                                calendarFeedback = "⚠️ 일정 등록 실패: 시스템 권한(CALENDAR_WRITE)을 확인하세요."
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("테스트 일정 추가", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                calendarFeedback?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        msg,
                        color = if (msg.contains("✅") || msg.contains("완료")) Color(0xFF88FF88) else Color(0xFFFF8888),
                        fontSize = 11.sp
                    )
                }
            }

            // [카드 3-2] 텔레그램 봇 메신저 연동 (Telegram Bot API)
            MonoCard(title = "텔레그램 봇 메신저 연동 (Telegram API)") {
                Text(
                    "Sleipnir 고속 엔진을 통해 긴급 알림, 압축 파일 및 일일 리포트를 전송합니다.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tgBotToken,
                    onValueChange = { tgBotToken = it },
                    label = { Text("Telegram Bot Token (예: 123456:ABC-DEF1234)", color = Color(0xFF888888), fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tgChatId,
                    onValueChange = { tgChatId = it },
                    label = { Text("수신 대상 Chat ID (예: 987654321)", color = Color(0xFF888888), fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            messengerClient.saveTelegramConfig(tgBotToken, tgChatId)
                            tgFeedback = "✅ 텔레그램 인증 정보가 안전하게 저장되었습니다."
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
                    ) {
                        Text("설정 저장", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (tgBotToken.isBlank() || tgChatId.isBlank()) {
                                tgFeedback = "⚠️ Token과 Chat ID를 모두 입력해주세요."
                                return@Button
                            }
                            messengerClient.saveTelegramConfig(tgBotToken, tgChatId)
                            isTestingTelegram = true
                            tgFeedback = "텔레그램 테스트 메시지 전송 중..."

                            coroutineScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    messengerClient.testTelegramConnection()
                                }
                                isTestingTelegram = false
                                tgFeedback = if (result.success) {
                                    "🎉 [전송 성공] 텔레그램으로 테스트 메시지가 도착했습니다!"
                                } else {
                                    "🚨 [전송 실패] ${result.detailMessage}"
                                }
                            }
                        },
                        enabled = !isTestingTelegram,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(if (isTestingTelegram) "전송 중..." else "연결 테스트 발송", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 양방향 원격 수신 제어 (Long-Polling)
                val inboundWorker = remember { com.yggdrasil.assistant.integration.SleipnirInboundWorker.getInstance(context) }
                var isInboundListening by remember { mutableStateOf(inboundWorker.isRunning) }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0xFF222222))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("양방향 원격 수신 제어 (Long-Polling)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("외출 시 텔레그램으로 일정 조회 및 배터리 점검 원격 명령 수신", color = Color(0xFF777777), fontSize = 11.sp)
                    }
                    Switch(
                        checked = isInboundListening,
                        onCheckedChange = { enable ->
                            isInboundListening = enable
                            if (enable) {
                                inboundWorker.startListening()
                                tgFeedback = "📡 텔레그램 원격 수신 워커가 백그라운드에서 가동되었습니다."
                            } else {
                                inboundWorker.stopListening()
                                tgFeedback = "🛑 텔레그램 원격 수신 워커가 중지되었습니다."
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF222222)
                        )
                    )
                }

                tgFeedback?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        msg,
                        color = if (msg.contains("성공") || msg.contains("✅") || msg.contains("가동")) Color(0xFF88FF88) else Color(0xFFFF8888),
                        fontSize = 11.sp
                    )
                }
            }

            // [카드 4] 온디바이스 프라이버시
            MonoCard(title = "온디바이스 프라이버시") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("학습 잠시 끄기 (시크릿 모드)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("대화 내용 DB 저장 및 RAG 학습 0% 차단", color = Color(0xFF777777), fontSize = 11.sp)
                    }
                    Switch(
                        checked = isLearningPaused,
                        onCheckedChange = {
                            isLearningPaused = it
                            muninManager.setLearningPaused(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF222222)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• 성생활/의료/금융 정보는 스위치 상태와 무관하게 24시간 자동 격리 차단됩니다.",
                    color = Color(0xFF999999),
                    fontSize = 11.sp
                )
            }

            // [카드 5] 온디바이스 AI 모델 및 격리 볼트 관리
            MonoCard(title = "온디바이스 AI 모델 & 스토리지 볼트") {
                // 1. 현재 탑재된 모델 상태 요약
                Text(
                    "• AI 가중치: ${if (modelStatus.isModelReady) "${modelStatus.activeModelName} (${String.format(java.util.Locale.US, "%.2f", modelStatus.activeModelSizeBytes / (1024.0 * 1024.0 * 1024.0))} GB)" else "미탑재 (가져오기 필요)"}\n" +
                    "• 안전 롤백 슬롯: ${if (modelStatus.hasStableBackup) "보관 중 (${modelStatus.backupSizeBytes / (1024 * 1024)} MB)" else "없음"}\n" +
                    "• 암호화 DB: ${(storageSummary["vault_bytes"] ?: 0) / 1024} KB\n" +
                    "• 화면 캡처 캐시: ${if (isCleaned) "0" else (storageSummary["screenshot_cache_bytes"] ?: 0) / 1024} KB\n" +
                    "• 임시 아카이브 캐시: ${if (isCleaned) "0" else (storageSummary["archive_cache_bytes"] ?: 0) / 1024} KB",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 가져오기 진행률 표시
                if (isImporting && importProgress != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("모델 볼트 복사 중...", color = Color.White, fontSize = 11.sp)
                            Text("$importProgress%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (importProgress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color.White,
                            trackColor = Color(0xFF333333)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 피드백 메시지
                importFeedbackMessage?.let { msg ->
                    Text(
                        msg,
                        color = if (msg.contains("성공") || msg.contains("승격 완료")) Color(0xFF88FF88) else Color(0xFFFFBB77),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141414), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 방식 0: 온라인 공식 HuggingFace 1.6GB 가중치 다운로드
                val downloader = remember { com.yggdrasil.assistant.llm.ExaoneModelDownloader.getInstance(context) }
                val downloadProgress by downloader.progressFlow.collectAsState()

                if (downloadProgress.status == com.yggdrasil.assistant.llm.ExaoneModelDownloader.DownloadStatus.DOWNLOADING ||
                    downloadProgress.status == com.yggdrasil.assistant.llm.ExaoneModelDownloader.DownloadStatus.VERIFYING) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1.6GB 모델 다운로드 중... (${downloadProgress.speedBytesPerSec / (1024*1024)} MB/s)", color = Color.White, fontSize = 11.sp)
                            Text("${downloadProgress.progressPercent}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color.White,
                            trackColor = Color(0xFF333333)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            downloader.startDownload()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "🌐 온라인 공식 1.6GB 모델 다운로드 (이어받기/SHA-256)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 방식 1: SAF 시스템 파일 선택기
                Button(
                    onClick = {
                        safPickerLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "📂 파일 선택기로 모델 가져오기 (SAF)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 방식 2: 앱 전용 외부 폴더 원터치 동기화
                Button(
                    onClick = {
                        isImporting = true
                        importFeedbackMessage = "앱 전용 외부 폴더 점검 중..."
                        coroutineScope.launch {
                            val ok = modelSyncManager.importFromAppSpecificFolder()
                            isImporting = false
                            modelStatus = modelSyncManager.getStatus()
                            storageSummary = vaultManager.getStorageUsageSummary()
                            importFeedbackMessage = if (ok) {
                                "✅ 앱 전용 폴더에서 모델 승격 완료! (0.1초 완료)"
                            } else {
                                "ℹ️ 앱 전용 폴더에 .gguf 파일이 없습니다.\n경로: /Android/data/com.yggdrasil.assistant/files/models/"
                            }
                        }
                    },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1C),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Text(
                        "📥 앱 전용 폴더에서 즉시 등록 (ADB/파일앱)",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 긴급 롤백 버튼
                Button(
                    onClick = {
                        val ok = modelSyncManager.rollbackToStableBackup()
                        modelStatus = modelSyncManager.getStatus()
                        importFeedbackMessage = if (ok) "이전 안정 모델로 0.1초 롤백 완료!" else "보관된 이전 백업 모델이 없습니다."
                    },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF121212),
                        contentColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A))
                ) {
                    Text(
                        "신버전 오류 시 ➔ 이전 안정 모델로 롤백 (0.1초)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 임시 캐시 정리 버튼
                OutlinedButton(
                    onClick = {
                        vaultManager.runGarbageCollection()
                        isCleaned = true
                        storageSummary = vaultManager.getStorageUsageSummary()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFAAAAAA)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222222))
                ) {
                    Text(
                        if (isCleaned) "만료 임시 캐시 정리 완료" else "만료된 화면캡처/아카이브 캐시 비우기",
                        fontSize = 11.sp
                    )
                }
            }

            // [카드 6] 비상 방어 프로토콜
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("비상 방어 프로토콜 (Break-Glass)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "이상 감지 시 모든 권한을 즉시 차단하고 읽기 전용 L0 상태로 잠급니다.",
                        color = Color(0xFF777777),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onEmergencyLockdown,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E1E1E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF555555))
                    ) {
                        Text("긴급 시스템 차단 실행", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MonoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun MonoToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF777777), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF222222)
            )
        )
    }
}

/**
 * 캘리브레이션용 1.8초 단기 고속 음성 녹음 헬퍼
 */
private suspend fun recordVoiceSample(context: Context): ShortArray? = withContext(Dispatchers.IO) {
    val sampleRate = 16000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(sampleRate * 2)

    var record: AudioRecord? = null
    try {
        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuf
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            return@withContext null
        }

        val totalSamples = (sampleRate * 1.8).toInt() // 1.8초 (28,800 샘플)
        val resultBuffer = ShortArray(totalSamples)
        record.startRecording()

        var totalRead = 0
        val temp = ShortArray(1024)
        while (totalRead < totalSamples) {
            val toRead = Math.min(temp.size, totalSamples - totalRead)
            val read = record.read(temp, 0, toRead)
            if (read > 0) {
                System.arraycopy(temp, 0, resultBuffer, totalRead, read)
                totalRead += read
            } else {
                break
            }
        }
        resultBuffer
    } catch (e: Exception) {
        null
    } finally {
        try {
            record?.stop()
            record?.release()
        } catch (e: Exception) {
            // 무시
        }
    }
}
