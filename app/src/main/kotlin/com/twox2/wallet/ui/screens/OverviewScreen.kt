package com.twox2.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.PageHeader
import com.twox2.wallet.ui.components.SyncProgressBar
import com.twox2.wallet.ui.components.TransactionListItem

@Composable
fun OverviewScreen(viewModel: WalletViewModel) {
    val balance by viewModel.balance.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val pending = viewModel.pendingBalance(transactions)

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader("Painel", "Visão geral da sua carteira 2X2")

        SyncProgressBar(syncProgress)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text("PoS Ativo") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BalanceCard("Total", viewModel.formatBalance(balance + pending), Modifier.weight(1f))
            BalanceCard("Disponível", viewModel.formatBalance(balance), Modifier.weight(1f))
            BalanceCard("Pendente", viewModel.formatBalance(pending), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Atividade Recente",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Text(
                "Nenhuma transação ainda.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions.take(5)) { tx ->
                    TransactionListItem(
                        tx = tx,
                        formattedAmount = viewModel.formatBalance(kotlin.math.abs(tx.amount))
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}
