package com.twox2.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.ui.transactionDisplay
import com.twox2.wallet.ui.theme.SurfaceDark
import com.twox2.wallet.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionListItem(
    tx: WalletTransactionEntity,
    formattedAmount: String,
    formattedFee: String? = null,
    onClick: (() -> Unit)? = null
) {
    val display = transactionDisplay(tx)
    val amountText = "${display.symbol}$formattedAmount"

    val cardModifier = Modifier.fillMaxWidth()
    val content = @Composable {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(display.accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(display.accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        display.icon,
                        contentDescription = null,
                        tint = display.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(display.label, color = display.accentColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        tx.txHash.take(18) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    if (tx.blockHeight >= 0) {
                        Text(
                            "Bloco ${tx.blockHeight} · ${tx.confirmations} conf.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    if (formattedFee != null) {
                        Text(
                            "Taxa: $formattedFee",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amountText, color = display.accentColor, fontWeight = FontWeight.Bold)
                    Text(
                        formatDate(tx.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) { content() }
    } else {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) { content() }
    }
}

private fun formatDate(epoch: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epoch * 1000))
}
