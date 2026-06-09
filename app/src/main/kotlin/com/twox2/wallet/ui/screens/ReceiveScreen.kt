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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twox2.wallet.crypto.receiveDisplayAddress
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.AddressBookSection
import com.twox2.wallet.ui.components.QrCodeImage
import com.twox2.wallet.ui.components.WalletHeader
import com.twox2.wallet.ui.theme.BackgroundDark
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted

@Composable
fun ReceiveScreen(viewModel: WalletViewModel, onBack: (() -> Unit)? = null) {
    val receiveAddresses by viewModel.receiveAddresses.collectAsState()
    val wallet by viewModel.wallet.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newAddressName by remember { mutableStateOf("") }
    val context = LocalContext.current

    val selected = viewModel.selectedReceiveAddress(receiveAddresses)
    val displayAddress = selected?.receiveDisplayAddress()?.takeIf { it.isNotBlank() }
        ?: wallet?.receiveDisplayAddress().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onBack != null) {
            WalletHeader(
                title = "Receive 2X2Coin",
                showBack = true,
                onBack = onBack
            )
        } else {
            WalletHeader(title = "Receive 2X2Coin")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Novo endereço", modifier = Modifier.padding(start = 4.dp))
            }
        }

        AddressBookSection(
            title = "Agenda de endereços de depósito",
            addresses = receiveAddresses,
            selectedId = selected?.id,
            onSelect = { viewModel.selectReceiveAddress(it.id) },
            onDelete = { viewModel.deleteSavedAddress(it) }
        )

        if (displayAddress.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            selected?.name?.let { name ->
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                QrCodeImage(data = displayAddress, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Your 2X2Coin Address",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                shortenAddress(displayAddress),
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
                        copyToClipboard(context, displayAddress)
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
                    onClick = { shareAddress(context, displayAddress) },
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

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Novo endereço de depósito") },
            text = {
                OutlinedTextField(
                    value = newAddressName,
                    onValueChange = { newAddressName = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createReceiveAddress(newAddressName)
                        showCreateDialog = false
                        newAddressName = ""
                    },
                    enabled = newAddressName.isNotBlank()
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
            }
        )
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
