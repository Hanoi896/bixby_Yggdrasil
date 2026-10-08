package com.huginmunin.app.ui.trustee

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.viewmodel.MainViewModel

@Composable
fun TrusteeManagementScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val trustees by viewModel.trustees.collectAsState()
    var newTrusteeName by remember { mutableStateOf("") }
    var newTrusteeRel by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Trustee Management (L6+)",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Add New Trustee", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newTrusteeName,
            onValueChange = { newTrusteeName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newTrusteeRel,
            onValueChange = { newTrusteeRel = it },
            label = { Text("Relationship (e.g. Spouse, Lawyer)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (newTrusteeName.isNotBlank()) {
                    viewModel.addTrustee(newTrusteeName, newTrusteeRel)
                    newTrusteeName = ""
                    newTrusteeRel = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Add Trustee")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Trusted Contacts:", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(trustees) { trustee ->
                Text(
                    text = "${trustee.name} (${trustee.relationship})",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}
