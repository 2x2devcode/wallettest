package com.twox2.wallet.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twox2.wallet.sync.SyncProgress
import com.twox2.wallet.ui.theme.GreenAccent
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted

enum class SyncDisplayStatus(val message: String) {
    WAITING_PEERS("Aguardando peers"),
    SYNCING("Sincronizando.."),
    SYNCED("Sincronizado"),
    VERIFYING("Verificando transações")
}

fun resolveSyncStatus(syncProgress: SyncProgress, isVerifying: Boolean): SyncDisplayStatus {
    if (isVerifying) return SyncDisplayStatus.VERIFYING
    if (syncProgress.connectedPeers.isEmpty()) return SyncDisplayStatus.WAITING_PEERS
    if (syncProgress.isSyncing) return SyncDisplayStatus.SYNCING
    return SyncDisplayStatus.SYNCED
}

@Composable
fun SyncStatusText(
    syncProgress: SyncProgress,
    isVerifying: Boolean,
    modifier: Modifier = Modifier
) {
    val status = resolveSyncStatus(syncProgress, isVerifying)
    val color = when (status) {
        SyncDisplayStatus.SYNCED -> GreenAccent
        SyncDisplayStatus.VERIFYING -> TealPrimary
        SyncDisplayStatus.SYNCING -> TealPrimary
        SyncDisplayStatus.WAITING_PEERS -> TextMuted
    }

    Text(
        status.message,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
