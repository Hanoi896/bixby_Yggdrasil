package com.yggdrasil.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 0-GC 최적화: 매 컴포지션 재할당을 방지하는 불변 MCP 멘션 도구 목록
private val MCP_MENTION_ITEMS = listOf(
    "@calendar (일정 조회/등록)",
    "@memo (암호화 메모 저장)",
    "@screen_action (화면 제어/터치)",
    "@system_settings (설정/절전모드)"
)

/**
 * [ActionDockInputBar]
 * 사진 4 기반 차세대 프로페셔널 액션 도크 입력 바.
 * - 좌측: `+` 첨부 버튼 (비전 캡처, 문서 파일, 단말 제어 액션)
 * - 입력창: "커넥터, 스킬 등을 보려면 @ 입력하세요" 플레이스홀더 및 @ 멘션 자동완성
 * - 우측: `Flash ∨` NPU 연산 프리셋 선택기 (Flash 고속 Q4_K_M vs Deep 정밀 CoT)
 * - 최우측: `🎙️` 실시간 음성 STT 마이크 버튼
 */
@Composable
fun ActionDockInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onAttachClicked: () -> Unit = {},
    onVoiceClicked: () -> Unit = {},
    selectedPreset: String = "Flash",
    onPresetChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPresetMenu by remember { mutableStateOf(false) }
    var isMentionDismissed by remember(inputText) { mutableStateOf(!inputText.endsWith("@")) }
    val showMentionMenu = inputText.endsWith("@") && !isMentionDismissed

    Column(modifier = modifier.fillMaxWidth()) {
        // @ 멘션 퀵 도구 팝업
        if (showMentionMenu) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1C1C1C),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("온디바이스 MCP 스킬 & 커넥터", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    MCP_MENTION_ITEMS.forEach { item ->
                        Text(
                            text = item,
                            color = Color(0xFFDDDDDD),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val toolTag = item.substringBefore(" ") + " "
                                    onInputTextChanged(inputText.dropLast(1) + toolTag)
                                    isMentionDismissed = true
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // 입력 바 본체
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0xFF262626), RoundedCornerShape(28.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 좌측 + 첨부 버튼
                IconButton(
                    onClick = onAttachClicked,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "첨부",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 중앙 텍스트 입력창
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "커넥터, 스킬 등을 보려면 @ 입력하세요",
                            color = Color(0xFF666666),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputTextChanged,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 우측 Flash ∨ 모델 선택 드롭다운
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPresetMenu = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedPreset,
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "모델 선택",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showPresetMenu,
                        onDismissRequest = { showPresetMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Flash (고속 1.2W 초저전력)", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                onPresetChanged("Flash")
                                showPresetMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deep (정밀 CoT 사고 모드)", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                onPresetChanged("Deep")
                                showPresetMenu = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 최우측 마이크 버튼
                IconButton(
                    onClick = onVoiceClicked,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "음성 입력",
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
