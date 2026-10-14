package com.yggdrasil.assistant.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * [AppPermissionManager]
 * 갤럭시 스마트폰에서 Yggdrasil의 모든 기능(화면 제어, 캘린더, 알림 감지 등)이
 * 100% 정상 작동하기 위해 필요한 권한들의 승인 상태를 점검하고 원클릭 설정을 유도하는 매니저.
 */
class AppPermissionManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: AppPermissionManager? = null

        fun getInstance(context: Context): AppPermissionManager {
            return instance ?: synchronized(this) {
                instance ?: AppPermissionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 권한 항목 정의
     */
    data class PermissionItem(
        val id: String,
        val name: String,
        val description: String,
        val isGranted: Boolean,
        val isCritical: Boolean,
        val intentAction: String? = null
    )

    /**
     * 전체 필수 권한의 승인 여부 조회
     */
    fun checkAllPermissions(): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. 접근성 서비스 (화면 자동 터치/타이핑) - 필수
        val isAccessibilityGranted = isAccessibilityServiceEnabled()
        list.add(
            PermissionItem(
                id = "accessibility",
                name = "접근성 서비스 (화면 조작)",
                description = "AI가 폰 화면을 직접 터치하고 앱을 조작하기 위해 필요합니다.",
                isGranted = isAccessibilityGranted,
                isCritical = true,
                intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )

        // 2. 알림 접근 허용 (카카오톡, 문자, Gmail 알림 감지) - 필수
        val isNotificationListenerGranted = isNotificationListenerEnabled()
        list.add(
            PermissionItem(
                id = "notification_listener",
                name = "알림 접근 허용 (수신 메시지 후킹)",
                description = "카카오톡, 문자, 중요 메일을 실시간으로 읽고 요약하기 위해 필요합니다.",
                isGranted = isNotificationListenerGranted,
                isCritical = true,
                intentAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
        )

        // 3. 구글 캘린더 조회 및 등록 권한
        val isCalendarGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionItem(
                id = "calendar",
                name = "캘린더 읽기/쓰기",
                description = "오늘의 일정을 조회하고 새 약속을 자동 등록하기 위해 필요합니다.",
                isGranted = isCalendarGranted,
                isCritical = false
            )
        )

        // 4. 구글 주소록 검색 권한
        val isContactsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionItem(
                id = "contacts",
                name = "연락처(주소록) 검색",
                description = "전화번호를 검색하고 상대방에게 메시지/파일을 전송하기 위해 필요합니다.",
                isGranted = isContactsGranted,
                isCritical = false
            )
        )

        // 5. 백그라운드 배터리 최적화 예외 (AI 프로세스 유지)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true
        list.add(
            PermissionItem(
                id = "battery_optimization",
                name = "배터리 최적화 제외 (백그라운드 지속)",
                description = "절전 모드로 인해 AI 비서가 백그라운드에서 강제 종료되는 것을 방지합니다.",
                isGranted = isBatteryIgnored,
                isCritical = true,
                intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS else null
            )
        )

        // 6. 다른 앱 위에 그리기 (긴급 비상 오버레이 버튼)
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
        list.add(
            PermissionItem(
                id = "system_overlay",
                name = "다른 앱 위에 표시 (긴급 중단 버튼)",
                description = "AI가 화면을 조작하는 중 언제든 손가락으로 멈출 수 있는 긴급 오버레이를 띄웁니다.",
                isGranted = isOverlayGranted,
                isCritical = true,
                intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.ACTION_MANAGE_OVERLAY_PERMISSION else null
            )
        )

        // 7. 알림 발행 권한 (Android 13+)
        val isPostNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        list.add(
            PermissionItem(
                id = "post_notifications",
                name = "알림 표시 권한",
                description = "아침 모닝 브리핑 및 배터리 케어 알림을 띄우기 위해 필요합니다.",
                isGranted = isPostNotificationGranted,
                isCritical = false
            )
        )

        return list
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "${context.packageName}/com.yggdrasil.assistant.screen_operator.YggdrasilAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedServiceName)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    /**
     * 권한 설정 화면으로 바로 이동하는 Intent 생성
     */
    fun createSettingIntent(item: PermissionItem): Intent {
        item.intentAction?.let { action ->
            val intent = Intent(action)
            if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                intent.data = Uri.parse("package:${context.packageName}")
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }

        // 일반 런타임 권한인 경우 앱 상세 설정창으로 이동
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
