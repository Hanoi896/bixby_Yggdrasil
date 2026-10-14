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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    pendingQuery: String? = null,
    onQueryConsumed: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val profile = remember { personaManager.getProfile() }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    // 기본 첫인사 메시지
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = SenderType.ASSISTANT,
                text = "${profile.userCallName}, 무엇을 도와드릴까요? 일정을 확인하거나 파일 압축, 화면 제어 등 편하게 말씀해 주세요."
            )
        )
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

        messages.add(ChatMessage(sender = SenderType.USER, text = trimmed))
        inputText = ""
        isThinking = true

        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val response = onSendMessageToNpu(trimmed)
            messages.add(ChatMessage(sender = SenderType.ASSISTANT, text = response))
            isThinking = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 빅스비 캡슐 app-launch 딥링크(yggdrasil://chat?query=...)로부터 전달된 발화 자동 전송
    LaunchedEffect(pendingQuery) {
        val query = pendingQuery?.trim()
        if (!query.isNullOrEmpty()) {
            sendMessage(query)
            onQueryConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            profile.aiName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            "온디바이스 EXAONE 3.0 • 100% 로컬 보안",
                            fontSize = 11.sp,
                            color = Color(0xFF777777)
                        )
                    }
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

            // 3. 하단 Black & White 입력 바
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(0.dp))
                    .background(Color(0xFF000000))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text("메시지를 입력하세요...", color = Color(0xFF555555), fontSize = 14.sp)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF444444),
                        unfocusedBorderColor = Color(0xFF222222),
                        cursorColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (inputText.isNotBlank()) Color.White else Color(0xFF222222), CircleShape)
                        .clickable(enabled = inputText.isNotBlank() && !isThinking) {
                            sendMessage(inputText)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "전송",
                        tint = if (inputText.isNotBlank()) Color.Black else Color(0xFF555555),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
            Text(
                text = msg.text,
                color = if (isUser) Color.Black else Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
