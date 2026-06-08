package com.twox2.wallet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twox2.wallet.R
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted

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
        Image(
            painter = painterResource(R.drawable.logo_2x2),
            contentDescription = "2X2Coin Logo",
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "2X2Coin Wallet",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Official wallet for the 2x2Coin network",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (!showRestore) {
            Button(
                onClick = { viewModel.createWallet() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("Create New Wallet")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showRestore = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore Wallet")
            }
        } else {
            Text(
                "Import your WIF key or BIP39 mnemonic (12 words)",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = wifInput,
                onValueChange = { wifInput = it },
                label = { Text("WIF or Mnemonic") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    cursorColor = TealPrimary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.restoreWallet(wifInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = wifInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("Restore")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showRestore = false }) {
                Text("Back")
            }
        }
    }
}
