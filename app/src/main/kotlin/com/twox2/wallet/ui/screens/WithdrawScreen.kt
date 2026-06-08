package com.twox2.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.SendState
import com.twox2.wallet.ui.WalletViewModel

@Composable
fun WithdrawScreen(viewModel: WalletViewModel) {
    var externalAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val sendState by viewModel.sendState.collectAsState()
    val balance by viewModel.balance.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Saques",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Retire 2X2 da sua carteira para um endereço externo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Saldo disponível para saque", style = MaterialTheme.typography.labelMedium)
                Text(
                    viewModel.formatBalance(balance),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = externalAddress,
            onValueChange = { externalAddress = it },
            label = { Text("Endereço externo") },
            placeholder = { Text("2x2coin:... ou endereço Base58") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Valor do saque (2X2)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("Referência (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.send(externalAddress, amount, "withdraw") },
            modifier = Modifier.fillMaxWidth(),
            enabled = externalAddress.isNotBlank() && amount.isNotBlank() && sendState !is SendState.Loading
        ) {
            if (sendState is SendState.Loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Sacar")
            }
        }

        when (val state = sendState) {
            is SendState.Success -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Saque enviado para a rede!\nTX: ${state.txHash}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is SendState.Error -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            else -> Unit
        }
    }
}
