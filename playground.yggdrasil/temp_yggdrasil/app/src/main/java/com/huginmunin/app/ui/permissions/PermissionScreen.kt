package com.huginmunin.app.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.appcompat.app.AppCompatActivity
import com.huginmunin.app.security.BiometricPromptManager

@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val permissions by viewModel.activePermissions.collectAsState()
    val context = LocalContext.current
    val biometricManager = remember { BiometricPromptManager() }

    fun requestPermissionWithAuth(level: Int, scope: String) {
        if (level >= 3) {
            val activity = context as? AppCompatActivity
            if (activity != null) {
                biometricManager.promptBiometricAuth(
                    activity = activity,
                    title = "Authentication Required",
                    subtitle = "Verify identity to grant L$level permission",
                    onResult = { result ->
                        if (result is BiometricPromptManager.BiometricResult.AuthenticationSucceeded) {
                            viewModel.grantPermission(level, scope)
                        }
                    }
                )
            }
        } else {
            viewModel.grantPermission(level, scope)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Permission Management",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = { requestPermissionWithAuth(1, "Read-Minimal") }) {
            Text(text = "Grant L1 (Read-Minimal)")
        }
        Button(onClick = { requestPermissionWithAuth(2, "Read-Extended") }) {
            Text(text = "Grant L2 (Read-Extended)")
        }
        Button(onClick = { requestPermissionWithAuth(3, "Assist-Automate") }) {
            Text(text = "Grant L3 (Assist-Automate) [Biometric]")
        }
        Button(onClick = { requestPermissionWithAuth(4, "Physical-Access") }) {
            Text(text = "Grant L4 (Physical-Access) [Hardware Key]")
        }
        Button(onClick = { viewModel.simulatePayment(50.0, "Merchant_X") }) {
            Text(text = "Simulate L5 Payment ($50)")
        }
        Button(onClick = { requestPermissionWithAuth(6, "Critical-Action") }) {
            Text(text = "Grant L6 (Critical) [Multi-sig]")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Active Permissions:", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(permissions) { perm ->
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(perm.grantedAt))
                val expires = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(perm.expiresAt))
                Text(text = "L${perm.level} (${perm.scope}) - Expires: $expires")
                Button(onClick = { viewModel.revokePermission(perm.id) }) {
                    Text("Revoke")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}
