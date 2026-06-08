package com.twox2.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.PageHeader
import com.twox2.wallet.ui.theme.TealPrimary

@Composable
fun SettingsScreen(viewModel: WalletViewModel) {
    val wallet by viewModel.wallet.collectAsState()
    val darkTheme by viewModel.darkTheme.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val blockCount by viewModel.blockCount.collectAsState()
    val explorerBlockCount by viewModel.explorerBlockCount.collectAsState()
    val explorerLoading by viewModel.explorerLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshExplorerBlockCount()
    }

    val peers = when {
        syncProgress.connectedPeers.isNotEmpty() -> syncProgress.connectedPeers
        !syncState?.connectedPeers.isNullOrBlank() ->
            syncState!!.connectedPeers.split(", ").filter { it.isNotBlank() }
        syncProgress.peer != null -> listOf("${syncProgress.peer}:15190")
        syncState?.peerHost != null -> listOf("${syncState!!.peerHost}:15190")
        else -> emptyList()
    }

    val syncedBlocks = blockCount.takeIf { it > 0 }
        ?: syncProgress.blockCount.takeIf { it > 0 }
        ?: syncState?.blockCount
        ?: 0

    val lastBlockHeight = syncState?.bestHeight
        ?: syncProgress.height
        ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        PageHeader("Configurações", "Preferências do aplicativo")

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aplicativo", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    Text("Tema escuro")
                    Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rede e Sincronização", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rede: Mainnet")
                Text("P2P: porta 15190")
                Text("Sincronização: automática")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Peers conectados:", style = MaterialTheme.typography.labelMedium)
                if (peers.isEmpty()) {
                    Text(
                        "Nenhum peer conectado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    peers.forEach { peer ->
                        Text("• $peer", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Blocos sincronizados: $syncedBlocks")
                Text("Altura do último bloco (local): $lastBlockHeight")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Altura do último bloco (explorer):", style = MaterialTheme.typography.labelMedium)
                if (explorerLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 8.dp).height(24.dp),
                        color = TealPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        explorerBlockCount?.toString() ?: "Indisponível",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TealPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backup da Carteira", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Guarde sua chave WIF em local seguro. É essencial para recuperar sua carteira.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    wallet?.wif ?: "—",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sobre", style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("2X2 Wallet v${com.twox2.wallet.BuildConfig.VERSION_NAME}")
                Text(
                    "https://2x2coin.com/",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
