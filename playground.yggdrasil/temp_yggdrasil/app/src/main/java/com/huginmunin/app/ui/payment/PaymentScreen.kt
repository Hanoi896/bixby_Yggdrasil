package com.huginmunin.app.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var cardNumber by remember { mutableStateOf("") }
    var expMonth by remember { mutableStateOf("") }
    var expYear by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("결제") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "결제 기능은 L5 권한이 필요합니다. 모든 거래는 로그에 기록됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                text = "카드 정보",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = cardNumber,
                onValueChange = { if (it.length <= 19) cardNumber = it },
                label = { Text("카드 번호") },
                placeholder = { Text("1234 5678 9012 3456") },
                leadingIcon = { Icon(Icons.Default.CreditCard, null) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = expMonth,
                    onValueChange = { if (it.length <= 2) expMonth = it },
                    label = { Text("월") },
                    placeholder = { Text("MM") },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = expYear,
                    onValueChange = { if (it.length <= 2) expYear = it },
                    label = { Text("년") },
                    placeholder = { Text("YY") },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = cvc,
                    onValueChange = { if (it.length <= 4) cvc = it },
                    label = { Text("CVC") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }

            Divider()

            Text(
                text = "결제 정보",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("금액") },
                leadingIcon = { Text("₩", style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("수취인") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    // viewModel.processPayment(amount.toDouble(), "KRW", recipient)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = cardNumber.isNotBlank() && amount.isNotBlank()
            ) {
                Icon(Icons.Default.Payment, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("결제하기", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = "테스트 모드: 실제 결제가 처리되지 않습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
