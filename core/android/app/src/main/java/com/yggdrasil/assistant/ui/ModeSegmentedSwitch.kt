package com.yggdrasil.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AssistantMode {
    CHAT, TASK
}

/**
 * [ModeSegmentedSwitch]
 * 사진 3 기반 `[ 채팅 | 작업 ]` 2단 캡슐형 모드 전환 스위치.
 * - 채팅 모드: 순수 NPU 지식 대화 (ReAct 도구 비활성화)
 * - 작업 모드: 화면 제어, 앱 조작 등 ReAct 5스텝 자율 에이전트 루프 활성화
 */
@Composable
fun ModeSegmentedSwitch(
    currentMode: AssistantMode,
    onModeChanged: (AssistantMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .padding(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 채팅 탭
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (currentMode == AssistantMode.CHAT) Color(0xFF333333) else Color.Transparent)
                    .clickable { onModeChanged(AssistantMode.CHAT) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "채팅",
                    color = if (currentMode == AssistantMode.CHAT) Color.White else Color(0xFF888888),
                    fontSize = 12.sp,
                    fontWeight = if (currentMode == AssistantMode.CHAT) FontWeight.Bold else FontWeight.Normal
                )
            }

            // 작업 탭
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (currentMode == AssistantMode.TASK) Color(0xFF333333) else Color.Transparent)
                    .clickable { onModeChanged(AssistantMode.TASK) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "작업",
                    color = if (currentMode == AssistantMode.TASK) Color.White else Color(0xFF888888),
                    fontSize = 12.sp,
                    fontWeight = if (currentMode == AssistantMode.TASK) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
