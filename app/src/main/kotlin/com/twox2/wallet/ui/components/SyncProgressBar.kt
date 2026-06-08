package com.twox2.wallet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twox2.wallet.sync.SyncProgress
import com.twox2.wallet.ui.theme.TealPrimary

@Composable
fun SyncProgressBar(syncProgress: SyncProgress, modifier: Modifier = Modifier) {
    val progress = syncProgress.progress.coerceIn(0, 100)
    if (!syncProgress.isSyncing && progress >= 100) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (syncProgress.isSyncing) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = TealPrimary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Text(
                "$progress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
