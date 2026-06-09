package com.twox2.wallet

import android.app.Application
import com.twox2.wallet.sync.ExplorerWalletSync
import com.twox2.wallet.sync.SyncEngine
import com.twox2.wallet.wallet.WalletManager
import com.twox2.wallet.wallet.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WalletApplication : Application() {
    lateinit var repository: WalletRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SyncEngine.init(this)
        repository = WalletRepository(this)
        startWalletRefreshLoop()
    }

    private fun startWalletRefreshLoop() {
        appScope.launch {
            while (isActive) {
                if (WalletManager.get(this@WalletApplication).hasWallet()) {
                    runCatching { ExplorerWalletSync.sync(this@WalletApplication) }
                }
                delay(15_000)
            }
        }
    }
}
