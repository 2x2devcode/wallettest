package com.twox2.wallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.QrCodeImage
import com.twox2.wallet.ui.components.WalletHeader
import com.twox2.wallet.ui.theme.BackgroundDark
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted

@Composable
fun ReceiveScreen(viewModel: WalletViewModel, onBack: (() -> Unit)? = null) {
    val wallet by viewModel.wallet.collectAsState()
    val context = LocalContext.current
    val address = wallet?.cashAddress ?: ""

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onBack != null) {
            WalletHeader(
                title = "Receive 2X2Coin",
                showBack = true,
                onBack = onBack,
                showMenu = false
            )
        } else {
            WalletHeader(title = "Receive 2X2Coin", showMenu = false)
        }

        if (address.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                QrCodeImage(data = address, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Your 2X2Coin Address",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                shortenAddress(address),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = TealPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, address)
                        viewModel.showMessage("Address copied")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Copy Address", modifier = Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = { shareAddress(context, address) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealPrimary,
                        contentColor = BackgroundDark
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Share", modifier = Modifier.padding(start = 6.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Only send 2X2Coin (2X2) to this address",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun shortenAddress(address: String): String {
    if (address.length <= 24) return address
    return address.take(12) + "..." + address.takeLast(12)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("address", text))
}

private fun shareAddress(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "My 2X2 address: $address")
    }
    context.startActivity(Intent.createChooser(intent, "Share address"))
}
