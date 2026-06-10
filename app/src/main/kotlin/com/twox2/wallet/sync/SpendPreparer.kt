package com.twox2.wallet.sync

import android.content.Context
import android.util.Log
import com.twox2.wallet.wallet.WalletManager

/**
 * Preparação leve antes do envio — sem importar todo o histórico de transações.
 */
object SpendPreparer {
    private const val TAG = "TwoX2SpendPrep"

    suspend fun prepare(context: Context) {
        val walletManager = WalletManager.get(context)
        if (!walletManager.hasWallet()) return

        val addresses = walletManager.getAllReceiveLegacyAddresses()
        if (addresses.isEmpty()) return

        val utxoDao = walletManager.getDatabase().utxoDao()
        val localBalance = utxoDao.getUnspent().sumOf { it.value }
        val explorerBalance = ExplorerWalletSync.fetchExplorerBalance(addresses)

        if (localBalance > explorerBalance) {
            Log.w(TAG, "Saldo local ($localBalance) > explorer ($explorerBalance) — reconciliando")
            ExplorerWalletSync.reconcileUtxos(addresses, utxoDao)
        }
    }
}
