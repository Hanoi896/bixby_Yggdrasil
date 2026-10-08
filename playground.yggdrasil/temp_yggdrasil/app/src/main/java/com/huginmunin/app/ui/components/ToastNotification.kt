package com.huginmunin.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ToastType {
    SUCCESS, ERROR, WARNING, INFO
}

data class ToastData(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 3000L
)

/**
 * Toast notification system
 */
object ToastManager {
    private val _toasts = mutableStateListOf<ToastData>()
    val toasts: List<ToastData> = _toasts

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        val toast = ToastData(message, type)
        _toasts.add(toast)
    }

    fun dismissToast(toast: ToastData) {
        _toasts.remove(toast)
    }
}

@Composable
fun ToastContainer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ToastManager.toasts.forEach { toast ->
                Toast(toast)
            }
        }
    }
}

@Composable
fun Toast(data: ToastData) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(data) {
        delay(data.duration)
        visible = false
        delay(300)
        ToastManager.dismissToast(data)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        val (backgroundColor, icon) = when (data.type) {
            ToastType.SUCCESS -> Color(0xFF34C759) to Icons.Default.CheckCircle
            ToastType.ERROR -> MaterialTheme.colorScheme.error to Icons.Default.Error
            ToastType.WARNING -> Color(0xFFFFCC00) to Icons.Default.Warning
            ToastType.INFO -> MaterialTheme.colorScheme.primary to Icons.Default.Info
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
                Text(
                    text = data.message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Show toast helper function
 */
fun showSuccessToast(message: String) = ToastManager.showToast(message, ToastType.SUCCESS)
fun showErrorToast(message: String) = ToastManager.showToast(message, ToastType.ERROR)
fun showWarningToast(message: String) = ToastManager.showToast(message, ToastType.WARNING)
fun showInfoToast(message: String) = ToastManager.showToast(message, ToastType.INFO)
