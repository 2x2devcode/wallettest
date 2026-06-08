package com.twox2.wallet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.data.db.WalletTransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionListItem(
    tx: WalletTransactionEntity,
    formattedAmount: String,
    onClick: (() -> Unit)? = null
) {
    val icon = when (tx.type) {
        "sent", "transfer", "withdraw" -> Icons.AutoMirrored.Filled.Send
        "staking" -> Icons.Default.Savings
        else -> Icons.AutoMirrored.Filled.CallReceived
    }
    val label = when (tx.type) {
        "sent" -> "Enviada"
        "received", "deposit" -> "Recebida"
        "staking" -> "Staking"
        "transfer" -> "Transferência"
        "withdraw" -> "Saque"
        else -> tx.type.replaceFirstChar { it.uppercase() }
    }

    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(
                    tx.txHash.take(18) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tx.blockHeight >= 0) {
                    Text(
                        "Bloco ${tx.blockHeight} · ${tx.confirmations} conf.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formattedAmount,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Text(
                    formatDate(tx.timestamp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

private fun formatDate(epoch: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epoch * 1000))
}
