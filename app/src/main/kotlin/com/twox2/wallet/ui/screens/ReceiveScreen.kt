package com.twox2.wallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.PageHeader
import com.twox2.wallet.ui.components.QrCodeImage

@Composable
fun ReceiveScreen(viewModel: WalletViewModel) {
    val wallet by viewModel.wallet.collectAsState()
    val context = LocalContext.current
    val address = wallet?.cashAddress ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageHeader("Receber", "Compartilhe seu endereço para receber 2X2")

        if (address.isNotBlank()) {
            QrCodeImage(data = address, modifier = Modifier.size(220.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                address,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            wallet?.address?.let { base58 ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    base58,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    copyToClipboard(context, address)
                    viewModel.showMessage("Endereço copiado")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Text("Copiar Endereço", modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { shareAddress(context, address) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Compartilhar", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("address", text))
}

private fun shareAddress(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Meu endereço 2X2: $address")
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar endereço"))
}
