package com.twox2.wallet.wallet

import android.content.Context
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.ExplorerApi
import com.twox2.wallet.network.MarketDataApi
import com.twox2.wallet.network.P2PClient
import com.twox2.wallet.network.PeerDiscovery
import com.twox2.wallet.sync.BlockchainSyncService
import com.twox2.wallet.sync.ExplorerWalletSync
import com.twox2.wallet.sync.SyncEngine
import com.twox2.wallet.ui.FeeTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WalletRepository(context: Context) {
    private val walletManager = WalletManager.get(context)
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val context = context.applicationContext

    val balance: Flow<Long> = walletManager.balance
    val transactions: Flow<List<WalletTransactionEntity>> = walletManager.transactions
    val allTransactions: Flow<List<WalletTransactionEntity>> =
        walletManager.getDatabase().walletTransactionDao().observeAll()
    val syncState = walletManager.syncState
    val blockCount = walletManager.getDatabase().blockHeaderDao().observeTipHeight()
    val sendAddresses: Flow<List<SavedAddressEntity>> = walletManager.sendAddresses
    val receiveAddresses: Flow<List<SavedAddressEntity>> = walletManager.receiveAddresses

    fun hasWallet(): Boolean = walletManager.hasWallet()

    fun getWallet(): WalletInfo? = walletManager.loadWallet()

    suspend fun ensurePrimaryReceiveAddress() = withContext(Dispatchers.IO) {
        walletManager.loadWallet()?.let { info ->
            walletManager.ensurePrimaryReceiveAddress(info)
            walletManager.migrateReceiveAddresses(info)
            ExplorerWalletSync.sync(context)
        }
    }

    suspend fun syncWalletFromExplorer() = withContext(Dispatchers.IO) {
        ExplorerWalletSync.sync(context)
    }

    suspend fun createWallet(): WalletInfo = withContext(Dispatchers.IO) {
        val info = walletManager.createWallet()
        walletManager.ensurePrimaryReceiveAddress(info)
        info
    }

    suspend fun restoreWallet(wif: String): WalletInfo = withContext(Dispatchers.IO) {
        val info = walletManager.restoreFromWif(wif)
        walletManager.ensurePrimaryReceiveAddress(info)
        info
    }

    fun startAutoSync() {
        BlockchainSyncService.start(context)
    }

    suspend fun verifySync() = withContext(Dispatchers.IO) {
        SyncEngine.verifySync(context)
    }

    suspend fun fetchExplorerBlockCount(): Int? = ExplorerApi.getBlockCount()

    suspend fun fetchUsdPrice(): Double? = withContext(Dispatchers.IO) {
        MarketDataApi.get2x2UsdtPrice()
    }

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    fun isValidAddress(address: String): Boolean = AddressEncoder.isValidAddress(address.trim())

    suspend fun saveSendAddress(name: String, address: String) = withContext(Dispatchers.IO) {
        walletManager.saveSendAddress(name, address)
    }

    suspend fun createReceiveAddress(name: String): SavedAddressEntity = withContext(Dispatchers.IO) {
        walletManager.createReceiveAddress(name)
    }

    suspend fun setDefaultReceiveAddress(id: Long) = withContext(Dispatchers.IO) {
        walletManager.setDefaultReceiveAddress(id)
    }

    suspend fun deleteSavedAddress(id: Long) = withContext(Dispatchers.IO) {
        walletManager.deleteSavedAddress(id)
    }

    suspend fun sendCoins(
        toAddress: String,
        amountCoins: Double,
        feeTier: FeeTier = FeeTier.STANDARD
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(amountCoins >= MIN_SEND_COINS) { "Envio mínimo: $MIN_SEND_COINS ${ChainParams.CURRENCY}" }
            require(amountCoins <= MAX_SEND_COINS) { "Envio máximo: $MAX_SEND_COINS ${ChainParams.CURRENCY}" }
            require(AddressEncoder.isValidAddress(toAddress)) { "Endereço inválido" }

            val wallet = walletManager.loadWallet() ?: error("Carteira não encontrada")
            val amount = (amountCoins * ChainParams.COIN).toLong()
            val utxos = walletManager.getDatabase().utxoDao().getUnspent()
            val utxoKeys = mutableMapOf<Long, TransactionBuilder.SigningKey>()
            for (utxo in utxos) {
                val key = walletManager.resolveSigningKey(utxo.scriptPubKey)
                    ?: continue
                utxoKeys[utxo.id] = TransactionBuilder.SigningKey(key.first, key.second)
            }
            require(utxoKeys.isNotEmpty()) { "Nenhuma chave disponível para assinar transação" }
            val spendableUtxos = utxos.filter { utxoKeys.containsKey(it.id) }
            val tx = TransactionBuilder.buildAndSign(
                utxos = spendableUtxos,
                utxoKeys = utxoKeys,
                toAddress = toAddress,
                amount = amount,
                changeAddress = wallet.address,
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

    companion object {
        const val MIN_SEND_COINS = 1.0
        const val MAX_SEND_COINS = 500_000.0
    }
}
