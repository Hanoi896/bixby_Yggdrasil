package com.huginmunin.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 런타임 권한 요청 헬퍼 클래스
 */
object PermissionHandler {
    
    /**
     * 캘린더 관련 권한
     */
    object Calendar {
        const val READ_CALENDAR = Manifest.permission.READ_CALENDAR
        const val WRITE_CALENDAR = Manifest.permission.WRITE_CALENDAR
        
        val REQUIRED_PERMISSIONS = arrayOf(
            READ_CALENDAR,
            WRITE_CALENDAR
        )
        
        const val REQUEST_CODE = 1001
        
        /**
         * 캘린더 권한이 모두 승인되었는지 확인
         */
        fun hasPermissions(context: Context): Boolean {
            return REQUIRED_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        /**
         * 캘린더 권한 요청
         */
        fun requestPermissions(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE
            )
        }
        
        /**
         * 권한 결과 처리
         */
        fun handlePermissionResult(
            requestCode: Int,
            grantResults: IntArray,
            onGranted: () -> Unit,
            onDenied: () -> Unit
        ) {
            if (requestCode == REQUEST_CODE) {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        }
        
        /**
         * 권한 설명이 필요한지 확인
         */
        fun shouldShowRationale(activity: Activity): Boolean {
            return REQUIRED_PERMISSIONS.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }
    }
    
    /**
     * 위치 관련 권한
     */
    object Location {
        const val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        const val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
        
        val REQUIRED_PERMISSIONS = arrayOf(
            FINE_LOCATION,
            COARSE_LOCATION
        )
        
        const val REQUEST_CODE = 1002
        
        fun hasPermissions(context: Context): Boolean {
            return REQUIRED_PERMISSIONS.any { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        fun requestPermissions(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE
            )
        }
        
        fun handlePermissionResult(
            requestCode: Int,
            grantResults: IntArray,
            onGranted: () -> Unit,
            onDenied: () -> Unit
        ) {
            if (requestCode == REQUEST_CODE) {
                if (grantResults.isNotEmpty() && 
                    grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        }
    }
    
    /**
     * 연락처 권한
     */
    object Contacts {
        const val READ_CONTACTS = Manifest.permission.READ_CONTACTS
        
        val REQUIRED_PERMISSIONS = arrayOf(READ_CONTACTS)
        
        const val REQUEST_CODE = 1003
        
        fun hasPermissions(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        fun requestPermissions(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE
            )
        }
        
        fun handlePermissionResult(
            requestCode: Int,
            grantResults: IntArray,
            onGranted: () -> Unit,
            onDenied: () -> Unit
        ) {
            if (requestCode == REQUEST_CODE) {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        }
    }
    
    /**
     * 오디오 녹음 권한
     */
    object Audio {
        const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
        
        val REQUIRED_PERMISSIONS = arrayOf(RECORD_AUDIO)
        
        const val REQUEST_CODE = 1004
        
        fun hasPermissions(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        fun requestPermissions(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE
            )
        }
        
        fun handlePermissionResult(
            requestCode: Int,
            grantResults: IntArray,
            onGranted: () -> Unit,
            onDenied: () -> Unit
        ) {
            if (requestCode == REQUEST_CODE) {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        }
    }
    
    /**
     * Notification permission helper (Android 13+)
     */
    const val POST_NOTIFICATIONS = android.Manifest.permission.POST_NOTIFICATIONS
    const val NOTIFICATION_REQUEST_CODE = 1005
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST_CODE
            )
        }
    }
    
    fun handleNotificationPermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestCode == NOTIFICATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && 
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onGranted()
            } else {
                onDenied()
            }
        }
    }
}
