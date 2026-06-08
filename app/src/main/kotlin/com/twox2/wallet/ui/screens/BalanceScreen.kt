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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.ui.WalletViewModel

@Composable
fun BalanceScreen(viewModel: WalletViewModel) {
    val balance by viewModel.balance.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()

    val isSyncing = syncProgress.isSyncing || syncState?.isSyncing == true
    val progress = if (syncProgress.progress > 0) syncProgress.progress else (syncState?.progress ?: 0)
    val statusText = syncProgress.error
        ?: syncProgress.status.takeIf { it != "Aguardando" }
        ?: syncState?.statusMessage?.takeIf { it.isNotBlank() }
        ?: syncState?.lastError
        ?: syncState?.let { "Bloco ${it.bestHeight}" }
        ?: "Não sincronizado"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Saldo disponível", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    viewModel.formatBalance(balance),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "v${com.twox2.wallet.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blockchain", style = MaterialTheme.typography.titleMedium)
                        Text(statusText, style = MaterialTheme.typography.bodySmall)
                        syncProgress.peer?.let {
                            Text("Peer: $it", style = MaterialTheme.typography.bodySmall)
                        } ?: syncState?.peerHost?.let {
                            Text("Peer: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = { viewModel.startSync() },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                        }
                        Text("Sincronizar", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (isSyncing || progress > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("$progress%", style = MaterialTheme.typography.bodySmall)
                }
                if (syncProgress.error != null || syncState?.lastError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        syncProgress.error ?: syncState?.lastError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Transações recentes", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Text(
                "Nenhuma transação. Sincronize a blockchain para ver depósitos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transactions) { tx -> TransactionItem(tx, viewModel) }
            }
        }
    }
}

@Composable
private fun TransactionItem(tx: WalletTransactionEntity, viewModel: WalletViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(tx.type.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                Text(tx.txHash.take(16) + "...", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                viewModel.formatBalance(kotlin.math.abs(tx.amount)),
                color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
