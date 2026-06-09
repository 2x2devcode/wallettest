package com.twox2.wallet.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.R
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.DashboardTransactionItem
import com.twox2.wallet.ui.components.GradientButton
import com.twox2.wallet.ui.components.SyncStatusText
import com.twox2.wallet.ui.components.WalletHeader
import com.twox2.wallet.ui.theme.CardGradientEnd
import com.twox2.wallet.ui.theme.CardGradientStart
import com.twox2.wallet.ui.theme.GreenAccent
import com.twox2.wallet.ui.theme.OrangeSendEnd
import com.twox2.wallet.ui.theme.OrangeSendStart
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TealReceiveEnd
import com.twox2.wallet.ui.theme.TealReceiveStart
import com.twox2.wallet.ui.theme.TextMuted

@Composable
fun OverviewScreen(
    viewModel: WalletViewModel,
    onNavigateToSend: () -> Unit,
    onNavigateToReceive: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val balance by viewModel.balance.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val blockCount by viewModel.blockCount.collectAsState()
    val explorerBlockCount by viewModel.explorerBlockCount.collectAsState()
    val localHeight = syncProgress.height.takeIf { it > 0 } ?: blockCount
    val blockHeight = listOf(
        localHeight,
        syncProgress.networkHeight,
        explorerBlockCount ?: 0
    ).max()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshUsdPrice()
            viewModel.refreshWalletBalance()
            viewModel.refreshExplorerBlockCount()
            viewModel.startAutoSync()
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        WalletHeader()

        SyncStatusText(syncProgress, isVerifying = false)

        BalanceCard(
            balance = viewModel.formatBalanceShort(balance),
            usdEstimate = viewModel.formatUsdEstimate(balance),
            blockHeight = blockHeight
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GradientButton(
                text = "Send",
                icon = Icons.AutoMirrored.Filled.Send,
                gradient = Brush.horizontalGradient(listOf(OrangeSendStart, OrangeSendEnd)),
                onClick = onNavigateToSend,
                modifier = Modifier.weight(1f)
            )
            GradientButton(
                text = "Receive",
                icon = Icons.Default.ArrowDownward,
                gradient = Brush.horizontalGradient(listOf(TealReceiveStart, TealReceiveEnd)),
                onClick = onNavigateToReceive,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
            Text(
                "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            TextButton(onClick = onNavigateToHistory) {
                Text("View All >", color = GreenAccent)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Text(
                "No transactions yet.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = TextMuted
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transactions.take(3).forEach { tx ->
                    DashboardTransactionItem(
                        tx = tx,
                        formattedAmount = viewModel.formatBalanceShort(kotlin.math.abs(tx.amount)),
                        formattedFee = if (tx.fee > 0) viewModel.formatFee(tx.fee) else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BalanceCard(balance: String, usdEstimate: String, blockHeight: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(CardGradientStart, CardGradientEnd)))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_2x2),
                contentDescription = "2X2 Logo",
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                balance,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                usdEstimate,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Mainnet • Block #$blockHeight",
                style = MaterialTheme.typography.bodySmall,
                color = TealPrimary
            )
        }
    }
}
