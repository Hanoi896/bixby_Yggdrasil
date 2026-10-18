package com.yggdrasil.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [ChatHistoryDrawer]
 * 사진 2 기반 멀티 스레드 대화 히스토리 네비게이션 드로어.
 * - 상단: `새 채팅` 버튼 및 `전체 ∨` 필터 + 깔때기 아이콘
 * - 리스트: 핀(📌) 고정 및 깃발(🚩) 북마크 지원 세션 목록
 */
@Composable
fun ChatHistoryDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String,
    onSessionSelected: (ChatSession) -> Unit,
    onNewChatClicked: () -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("전체") }
    var showFilterMenu by remember { mutableStateOf(false) }

    val filteredSessions = remember(sessions, selectedFilter) {
        when (selectedFilter) {
            "고정됨" -> sessions.filter { it.isPinned }
            "북마크" -> sessions.filter { it.isFlagged }
            else -> sessions
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // 1. 새 채팅 버튼
        NewChatButton(
            onClick = onNewChatClicked,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 필터 헤더 ("전체 ∨", 깔때기)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showFilterMenu = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = selectedFilter,
                        color = Color(0xFF64B5F6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "필터 선택",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    listOf("전체", "고정됨", "북마크").forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                selectedFilter = f
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "필터",
                tint = Color(0xFF888888),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. 세션 스레드 리스트
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredSessions, key = { it.id }) { session ->
                val isSelected = session.id == activeSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                        .clickable { onSessionSelected(session) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title,
                        color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (session.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "핀 고정됨",
                                tint = Color(0xFF888888),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onTogglePin(session.id) }
                            )
                        }
                        if (session.isFlagged) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "북마크됨",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onToggleFlag(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
