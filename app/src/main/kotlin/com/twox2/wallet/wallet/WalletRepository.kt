package com.twox2.wallet.wallet

import android.content.Context
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.chain.UInt256
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.ExplorerApi
import com.twox2.wallet.network.MarketDataApi
import com.twox2.wallet.network.TransactionBroadcaster
import com.twox2.wallet.sync.BlockchainSyncService
import com.twox2.wallet.sync.ExplorerWalletSync
import com.twox2.wallet.sync.SpendPreparer
import com.twox2.wallet.sync.SyncEngine
import com.twox2.wallet.ui.FeeTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.twox2.wallet.data.db.UtxoEntity

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

    suspend fun fetchExplorerBalance(): Long = withContext(Dispatchers.IO) {
        val addresses = walletManager.getAllReceiveLegacyAddresses()
        if (addresses.isEmpty()) return@withContext 0L
        ExplorerWalletSync.fetchExplorerBalance(addresses)
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

    suspend fun reindexBlockchain() = withContext(Dispatchers.IO) {
        SyncEngine.reindexBlockchain(context)
        fetchExplorerBalance()
    }

    suspend fun deleteWallet() = withContext(Dispatchers.IO) {
        SyncEngine.stopSync()
        walletManager.clearChainData()
        walletManager.deleteWallet(context)
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

            SpendPreparer.prepare(context)

            val wallet = walletManager.loadWallet() ?: error("Carteira não encontrada")
            val amount = (amountCoins * ChainParams.COIN).toLong()
            val fixedFee = feeTier.feeSatoshis
            val db = walletManager.getDatabase()
            val addresses = walletManager.getAllReceiveLegacyAddresses()
            val addressSet = addresses.toSet()

            val utxos = db.utxoDao().getUnspent()
            val utxoKeys = mutableMapOf<Long, TransactionBuilder.SigningKey>()
            for (utxo in utxos) {
                val key = walletManager.resolveSigningKey(utxo.scriptPubKey)
                    ?: continue
                utxoKeys[utxo.id] = TransactionBuilder.SigningKey(key.first, key.second)
            }
            require(utxoKeys.isNotEmpty()) { "Nenhuma chave disponível para assinar transação" }

            var spendableUtxos = utxos.filter { utxoKeys.containsKey(it.id) }
            require(spendableUtxos.isNotEmpty()) { "Nenhum UTXO disponível para gastar" }

            val spendableTotal = spendableUtxos.sumOf { it.value }
            require(spendableTotal >= amount + fixedFee) {
                "Saldo insuficiente (disponível: ${spendableTotal / ChainParams.COIN} ${ChainParams.CURRENCY})"
            }

            val candidateUtxos = selectCandidateUtxos(spendableUtxos, amount, fixedFee)
            val validatedUtxos = UtxoValidator.filterSpendable(candidateUtxos, addressSet)
            require(validatedUtxos.isNotEmpty()) {
                "UTXOs indisponíveis na rede (já gastos). Vá em Configurações → Reindexar blockchain."
            }
            val validatedTotal = validatedUtxos.sumOf { it.value }
            require(validatedTotal >= amount + fixedFee) {
                "Saldo confirmado insuficiente após validação na rede"
            }

            val networkTime = ExplorerApi.getNetworkTime()
                ?: (System.currentTimeMillis() / 1000)

            val buildResult = TransactionBuilder.buildAndSign(
                utxos = validatedUtxos,
                utxoKeys = utxoKeys,
                toAddress = toAddress.trim(),
                amount = amount,
                changeAddress = wallet.address,
                fixedFee = fixedFee,
                networkTime = networkTime
            )
            val tx = buildResult.transaction

            val broadcast = withTimeout(35_000) {
                TransactionBroadcaster.broadcast(tx, context)
            }
            when (broadcast) {
                is TransactionBroadcaster.BroadcastResult.Rejected ->
                    error("Transação rejeitada pela rede: ${broadcast.reason}")
                is TransactionBroadcaster.BroadcastResult.Failed ->
                    error("Falha ao transmitir. Verifique sua conexão e tente novamente.")
                is TransactionBroadcaster.BroadcastResult.Success -> Unit
            }

            val txHash = tx.getHash().toHex()
            db.walletTransactionDao().insert(
                WalletTransactionEntity(
                    txHash = txHash,
                    blockHeight = -1,
                    timestamp = System.currentTimeMillis() / 1000,
                    amount = -amount,
                    fee = buildResult.fee,
                    type = "sent",
                    address = toAddress,
                    confirmations = 0
                )
            )
            tx.inputs.forEach { input ->
                db.utxoDao().markSpent(
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

        private fun selectCandidateUtxos(
            utxos: List<UtxoEntity>,
            amount: Long,
            fee: Long
        ): List<UtxoEntity> {
            val sorted = utxos.sortedByDescending { it.value }
            val selected = mutableListOf<UtxoEntity>()
            var total = 0L
            for (utxo in sorted) {
                selected.add(utxo)
                total += utxo.value
                if (total >= amount + fee) break
            }
            return selected
        }
    }
}
