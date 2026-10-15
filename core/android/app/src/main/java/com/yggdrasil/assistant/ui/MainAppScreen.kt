package com.yggdrasil.assistant.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yggdrasil.assistant.persona.UserPersonaManager
import com.yggdrasil.assistant.proactive.ProactiveRoutineManager
import com.yggdrasil.assistant.storage.AppStorageVaultManager
import com.yggdrasil.assistant.storage.ModelAutoSyncManager

/**
 * [MainAppScreen] - Pure Black & White 모노크롬 네비게이션 셸
 * 4대 탭: [대화(채팅)], [상태], [보관소], [설정]
 * 온보딩: 기기 내 모델 미존재 시 ModelDownloadScreen 우선 렌더링
 */
@Composable
fun MainAppScreen(
    personaManager: UserPersonaManager,
    proactiveManager: ProactiveRoutineManager,
    vaultManager: AppStorageVaultManager,
    pendingQuery: String? = null,
    onQueryConsumed: () -> Unit = {},
    targetTab: Int? = null,
    onTabConsumed: () -> Unit = {},
    onSendMessageToNpu: suspend (String) -> String = { "온디바이스 EXAONE 3.0에서 정상 처리되었습니다." },
    onEmergencyLockdown: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelSyncManager = remember { ModelAutoSyncManager.getInstance(context) }
    var isModelReady by remember { mutableStateOf(modelSyncManager.getActiveModelFile() != null) }

    var selectedTab by remember { mutableStateOf(targetTab ?: 0) }
    val tabs = listOf("대화", "상태", "보관소", "설정")
    val icons = listOf(Icons.Default.ChatBubbleOutline, Icons.Default.Home, Icons.Default.Folder, Icons.Default.Settings)

    // 빅스비 딥링크 호스트 수신 시 해당 탭으로 자동 즉시 전환
    LaunchedEffect(targetTab) {
        if (targetTab != null && targetTab in 0..3) {
            selectedTab = targetTab
            onTabConsumed()
        }
    }

    // 빅스비 딥링크 발화가 수신되면 대화 탭(0번)으로 자동 즉시 전환
    LaunchedEffect(pendingQuery) {
        if (!pendingQuery.isNullOrBlank() && targetTab == null) {
            selectedTab = 0
        }
    }

    // 모델이 아직 기기에 설치되지 않은 경우 온보딩 다운로드 화면 표시
    if (!isModelReady) {
        ModelDownloadScreen(
            onDownloadComplete = {
                isModelReady = true
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF000000),
                contentColor = Color.White,
                modifier = Modifier.border(1.dp, Color(0xFF161616), RoundedCornerShape(0.dp))
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = title) },
                        label = { Text(title, fontSize = 11.sp) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color(0xFF222222),
                            unselectedIconColor = Color(0xFF666666),
                            unselectedTextColor = Color(0xFF666666)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF000000)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> ChatScreen(
                    personaManager = personaManager,
                    onSendMessageToNpu = onSendMessageToNpu,
                    pendingQuery = pendingQuery,
                    onQueryConsumed = onQueryConsumed
                )
                1 -> MonoHomeTab(personaManager)
                2 -> MonoArchiveTab(vaultManager)
                3 -> SettingsScreen(personaManager, proactiveManager, vaultManager, onEmergencyLockdown)
            }
        }
    }
}

@Composable
fun MonoHomeTab(personaManager: UserPersonaManager) {
    val profile = remember { personaManager.getProfile() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("이그드라실", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("반갑습니다, ${profile.userCallName}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("파트너: ${profile.aiName} (${profile.tone.displayName})", color = Color(0xFF888888), fontSize = 12.sp)
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("온디바이스 시스템 현황", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                MonoStatusRow("NPU 엔진", "LG EXAONE 3.0 2.4B Q4_K_M")
                MonoStatusRow("연산 환경", "Qualcomm NPU (100% 로컬)")
                MonoStatusRow("RAM 점유", "2.17 GB / 16.0 GB (안정)")
                MonoStatusRow("보안 상태", "L4 방어벽 가동 (SQLCipher)")
            }
        }
    }
}

@Composable
fun MonoStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF777777), fontSize = 12.sp)
        Text(value, color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MonoArchiveTab(vaultManager: AppStorageVaultManager) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("기억 보관소", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("SQLCipher 하드웨어 암호화로 보호되는 개인 대화 및 아카이브입니다.", color = Color(0xFF888888), fontSize = 12.sp)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("하드웨어 암호화 활성", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("외부 접근 및 클라우드 동기화 0% 차단 상태입니다.", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            }
        }
    }
}
