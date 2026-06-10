package com.twox2.wallet.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Savings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.ui.theme.GreenReceived
import com.twox2.wallet.ui.theme.RedSent

data class TransactionDisplay(
    val label: String,
    val symbol: String,
    val accentColor: Color,
    val icon: ImageVector,
    val isIncoming: Boolean
)

fun transactionDisplay(tx: WalletTransactionEntity): TransactionDisplay {
    val incoming = tx.amount >= 0
    return when {
        tx.type == "staking" -> TransactionDisplay(
            label = "Staking",
            symbol = "+",
            accentColor = GreenReceived,
            icon = Icons.Default.Savings,
            isIncoming = true
        )
        incoming -> TransactionDisplay(
            label = "Recebimento",
            symbol = "+",
            accentColor = GreenReceived,
            icon = Icons.AutoMirrored.Filled.CallReceived,
            isIncoming = true
        )
        tx.type == "withdraw" -> TransactionDisplay(
            label = "Saque",
            symbol = "−",
            accentColor = RedSent,
            icon = Icons.AutoMirrored.Filled.Send,
            isIncoming = false
        )
        else -> TransactionDisplay(
            label = "Envio",
            symbol = "−",
            accentColor = RedSent,
            icon = Icons.AutoMirrored.Filled.Send,
            isIncoming = false
        )
    }
}
