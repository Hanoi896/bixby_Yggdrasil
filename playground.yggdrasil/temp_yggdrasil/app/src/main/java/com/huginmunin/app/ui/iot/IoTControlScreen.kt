package com.huginmunin.app.ui.iot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoTControlScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Mock devices for demonstration
    val devices = remember {
        listOf(
            IoTDevice("거실 조명", "LIGHT", true, "ON"),
            IoTDevice("온도 조절", "THERMOSTAT", true, "22°C"),
            IoTDevice("현관 잠금", "LOCK", true, "LOCKED"),
            IoTDevice("보안 카메라", "CAMERA", false, "OFFLINE")
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IoT 기기 제어") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "기기 추가")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "MQTT 프로토콜을 사용하여 스마트 홈 기기를 제어합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(devices) { device ->
                IoTDeviceCard(device = device)
            }
        }
    }
}

data class IoTDevice(
    val name: String,
    val type: String,
    val isOnline: Boolean,
    val state: String
)

@Composable
fun IoTDeviceCard(device: IoTDevice) {
    var state by remember { mutableStateOf(device.state) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (device.type) {
                        "LIGHT" -> Icons.Default.Lightbulb
                        "THERMOSTAT" -> Icons.Default.Thermostat
                        "LOCK" -> Icons.Default.Lock
                        "CAMERA" -> Icons.Default.Videocam
                        else -> Icons.Default.Devices
                    },
                    contentDescription = null,
                    tint = if (device.isOnline) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (device.isOnline) state else "오프라인",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.isOnline) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            if (device.isOnline && device.type == "LIGHT") {
                Switch(
                    checked = state == "ON",
                    onCheckedChange = {
                        state = if (it) "ON" else "OFF"
                        // viewModel.controlIoTDevice(device.id, "power", state)
                    }
                )
            }
        }
    }
}
