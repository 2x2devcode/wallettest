package com.twox2.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.PageHeader
import com.twox2.wallet.ui.components.TransactionListItem

@Composable
fun HistoryScreen(viewModel: WalletViewModel) {
    val allTx by viewModel.allTransactions.collectAsState()
    var filter by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var selectedTx by remember { mutableStateOf<WalletTransactionEntity?>(null) }

    val filtered = allTx.filter { tx ->
        val matchesType = when (filter) {
            "sent" -> tx.type in listOf("sent", "transfer", "withdraw")
            "received" -> tx.type in listOf("received", "deposit")
            else -> true
        }
        val matchesSearch = search.isBlank() ||
            tx.txHash.contains(search, ignoreCase = true) ||
            tx.address.contains(search, ignoreCase = true)
        matchesType && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader("Histórico", "Todas as transações da carteira")

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Pesquisar") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Todas") })
            FilterChip(selected = filter == "sent", onClick = { filter = "sent" }, label = { Text("Enviadas") })
            FilterChip(selected = filter == "received", onClick = { filter = "received" }, label = { Text("Recebidas") })
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Text(
                "Nenhuma transação encontrada.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filtered.forEach { tx ->
                    TransactionListItem(
                        tx = tx,
                        formattedAmount = viewModel.formatBalance(kotlin.math.abs(tx.amount)),
                        onClick = { selectedTx = tx }
                    )
                }
            }
        }
    }

    selectedTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { selectedTx = null },
            title = { Text("Detalhes da Transação") },
            text = {
                Column {
                    Text("TXID: ${tx.txHash}")
                    Text("Tipo: ${tx.type}")
                    Text("Valor: ${viewModel.formatBalance(kotlin.math.abs(tx.amount))}")
                    Text("Bloco: ${if (tx.blockHeight >= 0) tx.blockHeight else "Pendente"}")
                    Text("Confirmações: ${tx.confirmations}")
                    if (tx.address.isNotBlank()) Text("Endereço: ${tx.address}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTx = null }) { Text("Fechar") }
            }
        )
    }
}
