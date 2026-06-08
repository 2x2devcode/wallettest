package com.twox2.wallet.wallet

import android.content.Context
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.P2PClient
import com.twox2.wallet.network.PeerDiscovery
import com.twox2.wallet.sync.BlockchainSyncService
import com.twox2.wallet.ui.FeeTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WalletRepository(context: Context) {
    private val walletManager = WalletManager.get(context)
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val balance: Flow<Long> = walletManager.balance
    val transactions: Flow<List<WalletTransactionEntity>> = walletManager.transactions
    val allTransactions: Flow<List<WalletTransactionEntity>> =
        walletManager.getDatabase().walletTransactionDao().observeAll()
    val syncState = walletManager.syncState

    fun hasWallet(): Boolean = walletManager.hasWallet()

    fun getWallet(): WalletInfo? = walletManager.loadWallet()

    fun createWallet(): WalletInfo = walletManager.createWallet()

    fun restoreWallet(wif: String): WalletInfo = walletManager.restoreFromWif(wif)

    fun startAutoSync() {
        BlockchainSyncService.start(context)
    }

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    suspend fun sendCoins(
        toAddress: String,
        amountCoins: Double,
        feeTier: FeeTier = FeeTier.MEDIUM
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val wallet = walletManager.loadWallet() ?: error("Carteira não encontrada")
            val amount = (amountCoins * com.twox2.wallet.chain.ChainParams.COIN).toLong()
            val utxos = walletManager.getDatabase().utxoDao().getUnspent()
            val tx = TransactionBuilder.buildAndSign(
                utxos = utxos,
                toAddress = toAddress,
                amount = amount,
                changeAddress = wallet.address,
                privateKey = wallet.privateKey,
                publicKey = wallet.publicKey,
                feePerByte = feeTier.feePerByte
            )

            val peers = PeerDiscovery.discoverPeers(3)
            var broadcast = false
            for (peer in peers) {
                val client = P2PClient(peer)
                if (client.connect() && client.handshake()) {
                    client.sendTransaction(tx)
                    broadcast = true
                    client.disconnect()
                    break
                }
            }
            require(broadcast) { "Falha ao transmitir transação" }

            val txHash = tx.getHash().toHex()
            walletManager.getDatabase().walletTransactionDao().insert(
                WalletTransactionEntity(
                    txHash = txHash,
                    blockHeight = -1,
                    timestamp = System.currentTimeMillis() / 1000,
                    amount = -amount,
                    fee = 0,
                    type = "sent",
                    address = toAddress,
                    confirmations = 0
                )
            )
            tx.inputs.forEach { input ->
                walletManager.getDatabase().utxoDao().markSpent(
                    input.prevTxHash.toHex(),
                    input.prevIndex.toInt()
                )
            }
            txHash
        }
    }

    private val context = context.applicationContext
}
