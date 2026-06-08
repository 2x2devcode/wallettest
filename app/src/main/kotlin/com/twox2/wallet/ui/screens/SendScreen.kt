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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.FeeTier
import com.twox2.wallet.ui.SendState
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.PageHeader

@Composable
fun SendScreen(viewModel: WalletViewModel) {
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var feeSlider by remember { mutableFloatStateOf(1f) }
    val sendState by viewModel.sendState.collectAsState()
    val balance by viewModel.balance.collectAsState()

    val feeTier = when {
        feeSlider < 0.5f -> FeeTier.SLOW
        feeSlider < 1.5f -> FeeTier.MEDIUM
        else -> FeeTier.FAST
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
    ) {
        PageHeader("Enviar", "Transferir 2X2 para outro endereço")

        Text(
            "Saldo: ${viewModel.formatBalance(balance)}",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Endereço do destinatário") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Valor (2X2)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Taxa: ${feeTier.label}", modifier = Modifier.padding(horizontal = 16.dp))
        Slider(
            value = feeSlider,
            onValueChange = { feeSlider = it },
            valueRange = 0f..2f,
            steps = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Resumo", style = MaterialTheme.typography.titleSmall)
                Text("Para: ${address.ifBlank { "—" }}")
                Text("Valor: ${amount.ifBlank { "—" }} 2X2")
                Text("Taxa: ${feeTier.label}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.send(address, amount, feeTier) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = address.isNotBlank() && amount.isNotBlank() && sendState !is SendState.Loading
        ) {
            if (sendState is SendState.Loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Confirmar e Enviar")
            }
        }

        when (val state = sendState) {
            is SendState.Success -> {
                Text(
                    "Enviado! TX: ${state.txHash}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is SendState.Error -> {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> Unit
        }
    }
}
