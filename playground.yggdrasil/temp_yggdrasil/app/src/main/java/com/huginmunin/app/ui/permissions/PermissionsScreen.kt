package com.huginmunin.app.ui.permissions

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.R
import com.huginmunin.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val permissions by viewModel.activePermissions.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.nav_back))
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
                Text(
                    text = stringResource(R.string.permissions_active),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(permissions, key = { it.id }) { permission ->
                PermissionCard(
                    permission = permission,
                    onRevoke = { viewModel.revokePermission(it.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.permissions_request_new),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items((0..10).toList()) { level ->
                PermissionLevelCard(
                    level = level,
                    onGrant = { viewModel.grantPermission(it, "default_scope") }
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    permission: com.huginmunin.data.entity.PermissionEntity,
    onRevoke: (com.huginmunin.data.entity.PermissionEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permissions_level, permission.level),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = permission.scope,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (permission.expiresAt != Long.MAX_VALUE) {
                    Text(
                        text = stringResource(R.string.permissions_expires, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(permission.expiresAt))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = { onRevoke(permission) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.permissions_revoke))
            }
        }
    }
}

@Composable
fun PermissionLevelCard(
    level: Int,
    onGrant: (Int) -> Unit
) {
    val title = when (level) {
        0 -> stringResource(R.string.level_0)
        1 -> stringResource(R.string.level_1)
        2 -> stringResource(R.string.level_2)
        3 -> stringResource(R.string.level_3)
        4 -> stringResource(R.string.level_4)
        5 -> stringResource(R.string.level_5)
        6 -> stringResource(R.string.level_6)
        7 -> stringResource(R.string.level_7)
        8 -> stringResource(R.string.level_8)
        9 -> stringResource(R.string.level_9)
        10 -> stringResource(R.string.level_10)
        else -> "Unknown"
    }
    
    val description = when (level) {
        0 -> stringResource(R.string.level_0_desc)
        1 -> stringResource(R.string.level_1_desc)
        2 -> stringResource(R.string.level_2_desc)
        3 -> stringResource(R.string.level_3_desc)
        4 -> stringResource(R.string.level_4_desc)
        5 -> stringResource(R.string.level_5_desc)
        6 -> stringResource(R.string.level_6_desc)
        7 -> stringResource(R.string.level_7_desc)
        8 -> stringResource(R.string.level_8_desc)
        9 -> stringResource(R.string.level_9_desc)
        10 -> stringResource(R.string.level_10_desc)
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (level >= 6) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { onGrant(level) },
                colors = if (level >= 6) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(stringResource(R.string.permissions_grant))
            }
        }
    }
}

