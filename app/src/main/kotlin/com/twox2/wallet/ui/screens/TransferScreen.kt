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
fun TransferScreen(viewModel: WalletViewModel) {
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val sendState by viewModel.sendState.collectAsState()
    val balance by viewModel.balance.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Transferências",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Envie 2X2 para outro endereço na rede 2x2Coin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Saldo: ${viewModel.formatBalance(balance)}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Endereço destino") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Quantidade (2X2)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.send(address, amount, "transfer") },
            modifier = Modifier.fillMaxWidth(),
            enabled = address.isNotBlank() && amount.isNotBlank() && sendState !is SendState.Loading
        ) {
            if (sendState is SendState.Loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Transferir")
            }
        }

        when (val state = sendState) {
            is SendState.Success -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Transação enviada!\nHash: ${state.txHash}",
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
