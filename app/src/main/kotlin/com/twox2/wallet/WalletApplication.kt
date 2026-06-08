package com.twox2.wallet

import android.app.Application
import com.twox2.wallet.sync.SyncEngine
import com.twox2.wallet.wallet.WalletRepository

class WalletApplication : Application() {
    lateinit var repository: WalletRepository
        private set

    override fun onCreate() {
        super.onCreate()
        SyncEngine.init(this)
        repository = WalletRepository(this)
    }
}
