package com.yggdrasil.assistant.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yggdrasil.assistant.llm.ExaoneModelConfig
import com.yggdrasil.assistant.llm.ExaoneModelDownloader
import kotlinx.coroutines.launch

/**
 * [ModelDownloadScreen]
 * LG EXAONE 3.0 2.4B 온디바이스 NPU 모델 다운로드 및 SHA-256 무결성 검증 온보딩 화면
 * - Pure Black & Crisp White 모노크롬 One UI 미학 적용
 * - 실시간 다운로드 속도(MB/s), 남은 시간(ETA), 프로그레스 바, 잔여 스토리지 표시
 */
@Composable
fun ModelDownloadScreen(
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloader = remember { ExaoneModelDownloader.getInstance(context) }
    val progress by downloader.progressFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var requireWifiOnly by remember { mutableStateOf(false) }

    val pureBlack = Color(0xFF000000)
    val darkCard = Color(0xFF141414)
    val borderStroke = Color(0xFF2E2E2E)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8E8E93)
    val accentGreen = Color(0xFF34C759)
    val accentRed = Color(0xFFFF453A)
    val accentBlue = Color(0xFF0A84FF)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pureBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단 헤더
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "다운로드",
                tint = textPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "온디바이스 NPU 코어 프로비저닝",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "갤럭시 S26 울트라에서 100% 오프라인으로 구동되는\nLG EXAONE 3.0 모델 가중치를 기기에 설치합니다.",
                fontSize = 13.sp,
                color = textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }

        // 중앙 상태 카드 & 프로그레스
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderStroke, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = darkCard)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 모델 사양 정보 행
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = ExaoneModelConfig.MODEL_NAME,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "양자화: ${ExaoneModelConfig.QUANTIZATION_TYPE} (~1.6GB)",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }

                    // 상태 배지
                    val badgeColor = when (progress.status) {
                        ExaoneModelDownloader.DownloadStatus.COMPLETED -> accentGreen
                        ExaoneModelDownloader.DownloadStatus.DOWNLOADING,
                        ExaoneModelDownloader.DownloadStatus.VERIFYING -> accentBlue
                        ExaoneModelDownloader.DownloadStatus.ERROR -> accentRed
                        else -> textSecondary
                    }
                    val badgeText = when (progress.status) {
                        ExaoneModelDownloader.DownloadStatus.IDLE -> "대기 중"
                        ExaoneModelDownloader.DownloadStatus.CHECKING_STORAGE -> "용량 점검"
                        ExaoneModelDownloader.DownloadStatus.DOWNLOADING -> "다운로드 중"
                        ExaoneModelDownloader.DownloadStatus.PAUSED -> "일시 정지"
                        ExaoneModelDownloader.DownloadStatus.VERIFYING -> "SHA-256 검증"
                        ExaoneModelDownloader.DownloadStatus.COMPLETED -> "설치 완료"
                        ExaoneModelDownloader.DownloadStatus.ERROR -> "오류 발생"
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 프로그레스 바
                LinearProgressIndicator(
                    progress = { (progress.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = textPrimary,
                    trackColor = Color(0xFF242424)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 다운로드 통계 (용량 및 퍼센트)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val downloadedMB = progress.downloadedBytes / (1024 * 1024)
                    val totalMB = progress.totalBytes / (1024 * 1024)
                    Text(
                        text = "$downloadedMB MB / $totalMB MB",
                        fontSize = 12.sp,
                        color = textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${progress.progressPercent}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 다운로드 속도 및 잔여 시간
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val speedMBs = String.format("%.1f", progress.speedBytesPerSec / (1024f * 1024f))
                    val etaMin = progress.etaSeconds / 60
                    val etaSec = progress.etaSeconds % 60
                    val etaText = if (progress.etaSeconds > 0) "${etaMin}분 ${etaSec}초 남음" else "--"

                    Text(
                        text = "속도: $speedMBs MB/s",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Text(
                        text = "예상: $etaText",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }

                // SHA-256 해시 표시 (검증 완료 시)
                if (progress.sha256Checksum != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SHA-256: ${progress.sha256Checksum?.take(16)}...",
                        fontSize = 10.sp,
                        color = accentGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // 에러 메시지 출력
                if (progress.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "⚠️ ${progress.errorMessage}",
                        fontSize = 12.sp,
                        color = accentRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 스토리지 및 네트워크 체크 행
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val freeGB = String.format("%.1f", progress.freeStorageBytes / (1024f * 1024f * 1024f))
                    Text(
                        text = "기기 여유 공간: ${freeGB}GB (최소 4GB 필요)",
                        fontSize = 11.sp,
                        color = if (progress.freeStorageBytes < 4L * 1024 * 1024 * 1024) accentRed else textSecondary
                    )
                }
            }
        }

        // 하단 컨트롤 버튼 영역
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (progress.status) {
                ExaoneModelDownloader.DownloadStatus.COMPLETED -> {
                    Button(
                        onClick = onDownloadComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = textPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "대화",
                            tint = pureBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "온디바이스 NPU 대화 시작하기",
                            color = pureBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                ExaoneModelDownloader.DownloadStatus.DOWNLOADING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { downloader.pauseDownload() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderStroke),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "일시정지", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("일시 정지")
                        }

                        Button(
                            onClick = { downloader.cancelDownload() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1515))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "취소", tint = accentRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("다운로드 취소", color = accentRed)
                        }
                    }
                }

                ExaoneModelDownloader.DownloadStatus.PAUSED -> {
                    Button(
                        onClick = { downloader.startDownload(requireWifi = requireWifiOnly, scope = scope) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = textPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "재개", tint = pureBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("다운로드 이어받기", color = pureBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                ExaoneModelDownloader.DownloadStatus.ERROR -> {
                    Button(
                        onClick = { downloader.startDownload(requireWifi = requireWifiOnly, scope = scope) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = textPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "재시도", tint = pureBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("다시 시도하기", color = pureBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                else -> {
                    // IDLE 등
                    Button(
                        onClick = { downloader.startDownload(requireWifi = requireWifiOnly, scope = scope) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = textPrimary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "시작", tint = pureBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1.6GB 모델 가중치 다운로드 시작", color = pureBlack, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Wi-Fi 전용 다운로드 체크박스
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Checkbox(
                    checked = requireWifiOnly,
                    onCheckedChange = { requireWifiOnly = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = textPrimary,
                        checkmarkColor = pureBlack,
                        uncheckedColor = textSecondary
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Wi-Fi 환경에서만 다운로드 (데이터 절약)",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
        }
    }
}
