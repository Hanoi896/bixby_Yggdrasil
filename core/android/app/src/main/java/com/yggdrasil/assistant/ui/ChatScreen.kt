package com.yggdrasil.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.yggdrasil.assistant.persona.UserPersonaManager
import kotlinx.coroutines.launch

/**
 * [ChatMessage] 대화 메시지 모델
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: SenderType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SenderType {
    USER, ASSISTANT
}

/**
 * [ChatScreen] - Pure Black & White 인앱 1:1 대화 화면
 * 빅스비 호출 없이도 앱 내에서 직접 타이핑하여 온디바이스 NPU(EXAONE 3.0)와
 * 자유롭게 대화하고 작업을 지시할 수 있는 전용 챗 룸.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    personaManager: UserPersonaManager,
    onSendMessageToNpu: suspend (String) -> String,
    onSendMessageToNpuStream: (suspend (String, (String) -> Unit) -> String)? = null,
    pendingQuery: String? = null,
    onQueryConsumed: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val profile = remember { personaManager.getProfile() }
    val hapticManager = remember { HapticFeedbackManager.getInstance(context) }
    val sessionManager = remember { ChatSessionManager.getInstance(context) }

    var currentMode by remember { mutableStateOf(AssistantMode.CHAT) }
    var selectedPreset by remember { mutableStateOf("Flash") }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    // 세션 관리
    var sessions by remember { mutableStateOf(sessionManager.getSortedSessions()) }
    var activeSession by remember { mutableStateOf(sessions.firstOrNull() ?: sessionManager.createNewSession()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 대화 메시지 상태
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = SenderType.ASSISTANT,
                text = "${profile.userCallName}, 무엇을 도와드릴까요? 일정을 확인하거나 파일 압축, 화면 제어 등 편하게 말씀해 주세요."
            )
        )
    }

    fun startNewChat() {
        hapticManager.performActionClick()
        com.yggdrasil.assistant.llm.LocalLlmService.getInstance()?.clearKvCache()
        val newSession = sessionManager.createNewSession()
        sessions = sessionManager.getSortedSessions()
        activeSession = newSession
        messages.clear()
        messages.add(
            ChatMessage(
                sender = SenderType.ASSISTANT,
                text = "${profile.userCallName}, 새로운 대화가 시작되었습니다. 무엇을 도와드릴까요?"
            )
        )
        coroutineScope.launch { drawerState.close() }
    }

    // 화면 이탈 시 재생 중이던 음성 TTS 즉시 중단
    DisposableEffect(Unit) {
        onDispose {
            try {
                com.yggdrasil.assistant.voice.YggdrasilTtsManager.getInstance(context).stop()
            } catch (_: Exception) {}
        }
    }

    val quickChips = listOf(
        "오늘 주요 일정 알려줘",
        "배터리 절전 모드 켜줘",
        "다운로드 폴더 ZIP 압축해줘",
        "화면 정리하고 홈으로 가줘"
    )

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isThinking) return

        hapticManager.performActionClick()
        messages.add(ChatMessage(sender = SenderType.USER, text = trimmed))
        inputText = ""
        isThinking = true

        // 세션 첫 대화 시 세션 제목 자동 업데이트
        if (messages.size <= 3 && activeSession.title == "새 대화") {
            val autoTitle = trimmed.take(16)
            sessionManager.updateTitle(activeSession.id, autoTitle)
            sessions = sessionManager.getSortedSessions()
        }

        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)

            val finalReply = if (onSendMessageToNpuStream != null) {
                val assistantMsg = ChatMessage(sender = SenderType.ASSISTANT, text = "")
                messages.add(assistantMsg)
                val targetIndex = messages.size - 1
                val accumulated = StringBuilder()
                var firstToken = true

                val result = onSendMessageToNpuStream(trimmed) { token ->
                    if (firstToken) {
                        hapticManager.performTokenTick()
                        firstToken = false
                    }
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        accumulated.append(token)
                        if (targetIndex in messages.indices) {
                            messages[targetIndex] = messages[targetIndex].copy(text = accumulated.toString())
                        }
                    }
                }

                if (targetIndex in messages.indices) {
                    messages[targetIndex] = messages[targetIndex].copy(text = result.ifBlank { accumulated.toString() })
                }
                result.ifBlank { accumulated.toString() }
            } else {
                val response = onSendMessageToNpu(trimmed)
                messages.add(ChatMessage(sender = SenderType.ASSISTANT, text = response))
                response
            }

            isThinking = false
            listState.animateScrollToItem(messages.size - 1)

            // Munin AES-256 SQLCipher 암호화 DB에 실시간 대화 아카이빙
            launch(kotlinx.coroutines.Dispatchers.IO) {
                com.yggdrasil.assistant.rag.MuninRagManager.getInstance(context).archiveDialogue(trimmed, finalReply)
            }
        }
    }

    // 빅스비 캡슐 app-launch 딥링크 수신
    LaunchedEffect(pendingQuery) {
        val query = pendingQuery?.trim()
        if (!query.isNullOrEmpty()) {
            sendMessage(query)
            onQueryConsumed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF121212),
                modifier = Modifier.width(280.dp)
            ) {
                ChatHistoryDrawer(
                    sessions = sessions,
                    activeSessionId = activeSession.id,
                    onSessionSelected = { session ->
                        activeSession = session
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewChatClicked = { startNewChat() },
                    onTogglePin = { id ->
                        sessionManager.togglePin(id)
                        sessions = sessionManager.getSortedSessions()
                    },
                    onToggleFlag = { id ->
                        sessionManager.toggleFlag(id)
                        sessions = sessionManager.getSortedSessions()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "히스토리 서랍",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        // 사진 3: 중앙 [ 채팅 | 작업 ] 듀얼 세그먼트 스위치
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ModeSegmentedSwitch(
                                currentMode = currentMode,
                                onModeChanged = { mode ->
                                    hapticManager.performActionClick()
                                    currentMode = mode
                                }
                            )
                        }
                    },
                    actions = {
                        // 사진 1: 상단 우측 새 채팅 버튼
                        NewChatButton(
                            onClick = { startNewChat() },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF000000),
                        titleContentColor = Color.White
                    ),
                    modifier = Modifier.border(1.dp, Color(0xFF161616), RoundedCornerShape(0.dp))
                )
            },
            containerColor = Color(0xFF000000)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. 대화 메시지 리스트
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(msg = msg, aiName = profile.aiName)
                    }

                    if (isThinking) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            ) {
                                Text(
                                    "AI가 생각 중입니다...",
                                    color = Color(0xFF666666),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 2. 퀵 추천 칩 목록
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickChips) { chip ->
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF262626), RoundedCornerShape(16.dp))
                                .background(Color(0xFF0D0D0D), RoundedCornerShape(16.dp))
                                .clickable { sendMessage(chip) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(chip, color = Color(0xFFCCCCCC), fontSize = 12.sp)
                        }
                    }
                }

                // 3. 사진 4: 차세대 프로페셔널 액션 도크 입력 바 (키보드 imePadding 연동)
                ActionDockInputBar(
                    inputText = inputText,
                    onInputTextChanged = { inputText = it },
                    onSendMessage = { sendMessage(it) },
                    selectedPreset = selectedPreset,
                    onPresetChanged = { selectedPreset = it },
                    onAttachClicked = {
                        hapticManager.performActionClick()
                    },
                    onVoiceClicked = {
                        hapticManager.performActionClick()
                    },
                    modifier = Modifier.imePadding()
                )
            }
        }
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessage, aiName: String) {
    val isUser = msg.sender == SenderType.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(
                aiName,
                color = Color(0xFF888888),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isUser) Color.White else Color(0xFF121212),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .border(
                    width = if (isUser) 0.dp else 1.dp,
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val annotatedText = remember(msg.text, isUser) {
                StreamingMarkdownSpanBuilder.buildAnnotatedMarkdown(
                    rawText = msg.text,
                    textColor = if (isUser) Color.Black else Color.White,
                    isUser = isUser
                )
            }
            Text(
                text = annotatedText,
                color = if (isUser) Color.Black else Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
