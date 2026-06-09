package com.twox2.wallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.PageHeader
import com.twox2.wallet.ui.theme.BackgroundDark
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
    val reindexing by viewModel.reindexing.collectAsState()
    val deletingWallet by viewModel.deletingWallet.collectAsState()
    val context = LocalContext.current
    val wif = wallet?.wif

    var showReindexDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && wif != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(buildBackupFileContent(wif).toByteArray(Charsets.UTF_8))
                }
                viewModel.showMessage("Backup salvo")
            }.onFailure {
                viewModel.showMessage("Falha ao salvar backup")
            }
        }
    }

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

    val lastBlockHeight = syncState?.bestHeight
        ?: syncProgress.height.takeIf { it > 0 }
        ?: blockCount.takeIf { it > 0 }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showReindexDialog = true },
                    enabled = !reindexing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
                ) {
                    if (reindexing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TealPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        if (reindexing) "Reindexando..." else "Reindexar blockchain",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Text(
                    "Exclui blocos locais, transações e saldo, e baixa tudo novamente da rede.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
                    wif ?: "—",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                if (wif != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, wif)
                                viewModel.showMessage("Chave copiada")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Copiar", modifier = Modifier.padding(start = 6.dp))
                        }
                        Button(
                            onClick = {
                                saveLauncher.launch("2x2-wallet-backup.txt")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealPrimary,
                                contentColor = BackgroundDark
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Salvar", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Zona de Perigo", style = MaterialTheme.typography.titleSmall, color = Color(0xFFEF4444))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Excluir a carteira remove permanentemente todas as chaves, transações e dados locais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    enabled = !deletingWallet,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    if (deletingWallet) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        if (deletingWallet) "Excluindo..." else "Excluir carteira",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
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

    if (showReindexDialog) {
        AlertDialog(
            onDismissRequest = { if (!reindexing) showReindexDialog = false },
            title = { Text("Reindexar blockchain?") },
            text = {
                Text(
                    "Isso irá excluir todos os blocos, transações e saldo locais, " +
                        "e baixar novamente da rede. Suas chaves da carteira serão mantidas."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReindexDialog = false
                        viewModel.reindexBlockchain()
                    },
                    enabled = !reindexing
                ) { Text("Reindexar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReindexDialog = false },
                    enabled = !reindexing
                ) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deletingWallet) showDeleteDialog = false },
            title = { Text("Excluir carteira?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "ATENÇÃO: Esta ação é irreversível.\n\n" +
                        "Todos os dados serão perdidos permanentemente, incluindo chaves privadas, " +
                        "transações e saldo local.\n\n" +
                        "Certifique-se de ter salvo sua chave WIF antes de continuar."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteWallet()
                    },
                    enabled = !deletingWallet
                ) {
                    Text("Excluir permanentemente", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !deletingWallet
                ) { Text("Cancelar") }
            }
        )
    }
}

private fun buildBackupFileContent(wif: String): String = """
    2X2 Wallet - Backup da Carteira
    ================================

    Chave WIF (guarde em local seguro):
    $wif

    Não compartilhe esta chave com ninguém.
""".trimIndent()

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("wif", text))
}
