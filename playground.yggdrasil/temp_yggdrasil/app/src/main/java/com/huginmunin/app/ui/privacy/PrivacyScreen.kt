package com.huginmunin.app.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val isPaused by viewModel.isLearningPaused.collectAsState()
    val pauseEndTime by viewModel.pauseEndTime.collectAsState()
    
    var showDurationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("프라이버시 & 학습 제어") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPaused) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.Block else Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (isPaused) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPaused) "학습 일시정지됨" else "학습 활성화",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (isPaused && pauseEndTime > 0) {
                                    Text(
                                        text = "재개: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(pauseEndTime))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Divider()

                        Text(
                            text = if (isPaused) {
                                "• 데이터 수집이 완전히 중단되었습니다\n• 모델 업데이트가 일시정지되었습니다\n• 기존 학습 데이터는 유지됩니다"
                            } else {
                                "• 상호작용 패턴을 학습합니다\n• 온디바이스에서만 처리됩니다\n• 언제든지 일시정지할 수 있습니다"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isPaused) {
                        Button(
                            onClick = { viewModel.toggleLearningPause(false) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("학습 재개")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDurationDialog = true },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("일시정지")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "데이터 관리",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextButton(
                            onClick = { /* View collected data */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Visibility, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("수집된 데이터 보기")
                        }

                        Divider()

                        TextButton(
                            onClick = { /* Export data */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("데이터 내보내기")
                        }

                        Divider()

                        TextButton(
                            onClick = { /* Delete data */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("학습 데이터 삭제")
                        }
                    }
                }
            }
        }
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("학습 일시정지 기간") },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.toggleLearningPause(true, 1 * 60 * 60 * 1000L) // 1 hour
                        showDurationDialog = false
                    }) {
                        Text("1시간")
                    }
                    TextButton(onClick = {
                        viewModel.toggleLearningPause(true, 24 * 60 * 60 * 1000L) // 24 hours
                        showDurationDialog = false
                    }) {
                        Text("24시간")
                    }
                    TextButton(onClick = {
                        viewModel.toggleLearningPause(true, 7 * 24 * 60 * 60 * 1000L) // 7 days
                        showDurationDialog = false
                    }) {
                        Text("7일")
                    }
                    TextButton(onClick = {
                        viewModel.toggleLearningPause(true, Long.MAX_VALUE) // Indefinite
                        showDurationDialog = false
                    }) {
                        Text("무기한")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}
