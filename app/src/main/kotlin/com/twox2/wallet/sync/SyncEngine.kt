package com.twox2.wallet.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object SyncEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var manager: BlockchainSyncManager? = null

    val syncProgress: StateFlow<SyncProgress>
        get() = requireNotNull(manager) { "SyncEngine não inicializado" }.syncProgress

    fun init(context: Context) {
        if (manager == null) {
            manager = BlockchainSyncManager(context.applicationContext)
        }
    }

    fun startSync(context: Context) {
        init(context)
        scope.launch {
            manager?.startSync()
        }
    }

    fun stopSync() {
        manager?.stopSync()
    }
}
