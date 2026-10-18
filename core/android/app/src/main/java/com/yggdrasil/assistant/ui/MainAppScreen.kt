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
    onSendMessageToNpuStream: (suspend (String, (String) -> Unit) -> String)? = null,
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
                    onSendMessageToNpuStream = onSendMessageToNpuStream,
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val profile = remember { personaManager.getProfile() }
    val growthManager = remember { com.yggdrasil.assistant.growth.WorldTreeGrowthManager.getInstance(context) }
    var growthStatus by remember { mutableStateOf(growthManager.getGrowthStatus()) }

    LaunchedEffect(Unit) {
        growthStatus = growthManager.getGrowthStatus()
    }

    val ramText = remember {
        try {
            val actManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0) {
                val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val availGb = memInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val usedGb = totalGb - availGb
                String.format(java.util.Locale.US, "%.2f GB / %.1f GB (실측)", usedGb, totalGb)
            } else {
                "정상 가동 중"
            }
        } catch (_: Exception) {
            "정상 가동 중"
        }
    }

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

        // 세계수(Yggdrasil) 실시간 성장 & 지혜 포인트(WP) 카드
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("세계수 성장 현황", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Lv.${growthStatus.level} ${growthStatus.stageCode}", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(growthStatus.stageName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (growthStatus.growthPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color.White,
                    trackColor = Color(0xFF222222)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${growthStatus.wisdomPoints} WP", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${growthStatus.growthPercent}% (다음: ${growthStatus.nextStageWp} WP)", color = Color(0xFF777777), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                MonoStatusRow("누적 대화", "${growthStatus.dialogueCount} 회")
                MonoStatusRow("기억 아카이브", "${growthStatus.memoryCount} 건")
                MonoStatusRow("자율 액션 완수", "${growthStatus.actionCount} 건")
            }
        }

        val llmService = remember { com.yggdrasil.assistant.llm.LocalLlmService.getInstance() }
        val isLoaded = llmService?.isModelLoaded() ?: false
        val backendName = remember(isLoaded) { llmService?.getActiveBackendName() ?: "미탑재 / 언로드 상태" }
        val isBreakGlass = remember { com.yggdrasil.assistant.security.BreakGlassSecurityManager.getInstance(context).getSecurityStatus().isBreakGlassActive }

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
                MonoStatusRow("NPU 엔진", if (isLoaded) "LG EXAONE 3.0 2.4B (가동 중)" else "모델 미탑재 (다운로드 필요)")
                MonoStatusRow("연산 환경", backendName)
                MonoStatusRow("RAM 점유", ramText)
                MonoStatusRow("보안 상태", if (isBreakGlass) "비상 락다운 해제 상태" else "L4 방어벽 가동 (SQLCipher)")
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var recentMemories by remember { mutableStateOf<List<com.yggdrasil.assistant.rag.MuninSqlCipherDatabase.MemoryRecord>>(emptyList()) }
    var vaultFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val munin = com.yggdrasil.assistant.rag.MuninRagManager.getInstance(context)
                val memories = munin.loadRecentDialogues(15)
                val files = vaultManager.vaultDir.listFiles()?.toList() ?: emptyList()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    recentMemories = memories
                    vaultFiles = files
                }
            } catch (_: Exception) {}
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("기억 보관소", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("SQLCipher 하드웨어 암호화로 보호되는 개인 대화 및 아카이브입니다.", color = Color(0xFF888888), fontSize = 12.sp)
        }

        item {
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
                    Text("저장된 기억: ${recentMemories.size} 건 | 암호화 볼트 파일: ${vaultFiles.size} 개", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                }
            }
        }

        item {
            Text("최근 보관된 암호화 기억", color = Color(0xFFAAAAAA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (recentMemories.isEmpty()) {
            item {
                Text("아직 저장된 암호화 기억이 없습니다. 대화를 나누면 자동 아카이빙됩니다.", color = Color(0xFF666666), fontSize = 12.sp)
            }
        } else {
            items(recentMemories.size) { idx ->
                val mem = recentMemories[idx]
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(mem.content.take(120), color = Color(0xFFDDDDDD), fontSize = 12.sp, maxLines = 3)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(mem.timestamp, color = Color(0xFF666666), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
