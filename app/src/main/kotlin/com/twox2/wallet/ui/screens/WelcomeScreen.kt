package com.twox2.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.WalletViewModel

@Composable
fun WelcomeScreen(viewModel: WalletViewModel) {
    var showRestore by rememberSaveable { mutableStateOf(false) }
    var wifInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "2X2 Wallet",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Carteira oficial para a rede 2x2Coin",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (!showRestore) {
            Button(
                onClick = { viewModel.createWallet() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Criar Nova Carteira")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showRestore = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar Carteira")
            }
        } else {
            Text(
                "Importe sua chave WIF ou frase mnemônica BIP39 (12 palavras)",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = wifInput,
                onValueChange = { wifInput = it },
                label = { Text("WIF ou Mnemônico") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.restoreWallet(wifInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = wifInput.isNotBlank()
            ) {
                Text("Restaurar")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showRestore = false }) {
                Text("Voltar")
            }
        }
    }
}
