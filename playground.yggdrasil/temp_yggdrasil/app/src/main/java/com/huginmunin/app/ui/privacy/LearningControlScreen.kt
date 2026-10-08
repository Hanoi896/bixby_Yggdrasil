package com.huginmunin.app.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LearningControlScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val isPaused by viewModel.isLearningPaused.collectAsState()
    val pauseEndTime by viewModel.pauseEndTime.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Learning Control (Privacy)",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (isPaused) "Learning is PAUSED" else "Learning is ACTIVE",
            style = MaterialTheme.typography.titleLarge,
            color = if (isPaused) Color.Red else Color.Green
        )
        
        if (isPaused && pauseEndTime > 0) {
            val date = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pauseEndTime))
            Text(text = "Until: $date")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.toggleLearningPause(!isPaused) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPaused) Color.Gray else Color.Red
            )
        ) {
            Text(text = if (isPaused) "Resume Learning" else "Pause Learning (Indefinite)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (!isPaused) {
            Button(onClick = { viewModel.toggleLearningPause(true, 3600000) }) { // 1 hour
                Text(text = "Pause for 1 Hour")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}
