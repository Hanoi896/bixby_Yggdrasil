package com.yggdrasil.core.ui

import android.content.Context
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
import com.yggdrasil.core.permission.AppPermissionManager
import com.yggdrasil.core.persona.UserPersonaManager
import com.yggdrasil.core.persona.UserPersonaManager.SpeechTone
import com.yggdrasil.core.proactive.ProactiveRoutineManager
import com.yggdrasil.core.storage.AppStorageVaultManager

/**
 * [SettingsScreen] - Black & White 극도의 미니멀리즘 디자인
 * 갤럭시 S26 Ultra의 AMOLED 디스플레이에 최적화된 Pure Black & Crisp White 테마.
 * 1. 시스템 필수 권한 관리 허브 (접근성, 알림, 캘린더 등 원클릭 허용)
 * 2. 내 맞춤 페르소나 (호칭, AI 이름, 말투)
 * 3. 선제적 능동 비서 On/Off 및 세부 루틴
 * 4. 프라이버시 쉴드 (시크릿 모드)
 * 5. 격리 스토리지 볼트 관리
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

    // 프라이버시 상태
    var isLearningPaused by remember { mutableStateOf(false) }

    // 스토리지 상태
    var storageSummary by remember { mutableStateOf(vaultManager.getStorageUsageSummary()) }
    var isCleaned by remember { mutableStateOf(false) }

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
                        onCheckedChange = { isLearningPaused = it },
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

            // [카드 5] 격리 스토리지 볼트
            MonoCard(title = "앱 내부 격리 스토리지 볼트") {
                Text(
                    "• 모델 가중치: 2.17 GB (불변 안전 영역)\n" +
                    "• 암호화 DB: ${(storageSummary["vault_bytes"] ?: 0) / 1024} KB\n" +
                    "• 화면 캡처 캐시: ${if (isCleaned) "0" else (storageSummary["screenshot_cache_bytes"] ?: 0) / 1024} KB\n" +
                    "• 압축 아카이브 캐시: ${if (isCleaned) "0" else (storageSummary["archive_cache_bytes"] ?: 0) / 1024} KB",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        vaultManager.runGarbageCollection()
                        isCleaned = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (isCleaned) "만료 캐시 정리 완료" else "만료된 임시 캐시 즉시 비우기",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
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
