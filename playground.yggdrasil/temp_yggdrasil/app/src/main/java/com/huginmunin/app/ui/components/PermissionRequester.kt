package com.huginmunin.app.ui.components

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.huginmunin.app.MainActivity
import com.huginmunin.app.utils.PermissionHandler

/**
 * 권한 요청 다이얼로그를 표시하는 Composable
 */
@Composable
fun rememberPermissionRequester(): PermissionRequester {
    val context = LocalContext.current
    val activity = context as? MainActivity
    
    return remember {
        PermissionRequester(activity)
    }
}

class PermissionRequester(private val activity: MainActivity?) {
    
    /**
     * 캘린더 권한 요청
     */
    fun requestCalendarPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        activity?.let { act ->
            if (PermissionHandler.Calendar.hasPermissions(act)) {
                onGranted()
            } else {
                act.requestCalendarPermission(onGranted, onDenied)
            }
        } ?: onDenied()
    }
    
    /**
     * 캘린더 권한 확인
     */
    fun hasCalendarPermission(): Boolean {
        return activity?.let { PermissionHandler.Calendar.hasPermissions(it) } ?: false
    }
}

/**
 * 캘린더 권한 요청 다이얼로그
 */
@Composable
fun CalendarPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("캘린더 권한 필요") },
        text = {
            Text(
                "AI 어시스턴트가 일정을 확인하려면 캘린더 접근 권한이 필요합니다.\n\n" +
                "• 다가오는 일정 확인\n" +
                "• 일정 알림\n" +
                "• 스마트 일정 제안"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("권한 허용")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 권한 거부 안내 다이얼로그
 */
@Composable
fun PermissionDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("권한이 거부되었습니다") },
        text = {
            Text(
                "캘린더 기능을 사용하려면 설정에서 권한을 허용해주세요.\n\n" +
                "설정 → 앱 → Hugin-Munin → 권한 → 캘린더"
            )
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("설정으로 이동")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}
