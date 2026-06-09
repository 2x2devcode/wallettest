package com.twox2.wallet.sync

import android.content.Context
import android.util.Log
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.data.db.UtxoDao
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.ExplorerApi
import com.twox2.wallet.wallet.WalletManager

object ExplorerWalletSync {
    private const val TAG = "TwoX2ExplorerWallet"
    private const val TX_PAGE_SIZE = 50
    private const val MAX_TX_PAGES = 40

    suspend fun sync(context: Context) {
        val walletManager = WalletManager.get(context)
        if (!walletManager.hasWallet()) return

        val db = walletManager.getDatabase()
        val utxoDao = db.utxoDao()
        val txDao = db.walletTransactionDao()

        val addresses = walletManager.getAllReceiveLegacyAddresses()
        if (addresses.isEmpty()) {
            Log.w(TAG, "Nenhum endereço de depósito legado encontrado")
            return
        }

        Log.d(TAG, "Sincronizando endereços: ${addresses.joinToString()}")
        val addressSet = addresses.toSet()
        var importedUtxos = 0
        var importedTxs = 0
        val seenTxIds = mutableSetOf<String>()

        for (address in addresses) {
            var start = 0
            repeat(MAX_TX_PAGES) {
                val page = ExplorerApi.getAddressTxs(address, start, TX_PAGE_SIZE)
                if (page.isEmpty()) return@repeat

                for (summary in page) {
                    if (summary.txid.isBlank() || !seenTxIds.add(summary.txid)) continue
                    val detail = ExplorerApi.getTx(summary.txid) ?: continue

                    val ourOutputs = detail.outputs.withIndex()
                        .filter { it.value.address in addressSet && it.value.amount > 0 }

                    val sentCoins = summary.sent
                    if (sentCoins > 0) {
                        val sentSat = (sentCoins * ChainParams.COIN).toLong()
                        if (!txDao.exists(summary.txid)) {
                            txDao.insert(
                                WalletTransactionEntity(
                                    txHash = summary.txid,
                                    blockHeight = detail.blockHeight,
                                    timestamp = detail.timestamp,
                                    amount = -sentSat,
                                    fee = detail.fee,
                                    type = "sent",
                                    address = address,
                                    confirmations = 0
                                )
                            )
                            importedTxs++
                        }
                    }

                    if (ourOutputs.isNotEmpty()) {
                        if (!txDao.exists(summary.txid)) {
                            val totalReceived = ourOutputs.sumOf { it.value.amount }
                            txDao.insert(
                                WalletTransactionEntity(
                                    txHash = summary.txid,
                                    blockHeight = detail.blockHeight,
                                    timestamp = detail.timestamp,
                                    amount = totalReceived,
                                    fee = 0,
                                    type = "received",
                                    address = ourOutputs.first().value.address,
                                    confirmations = 0
                                )
                            )
                            importedTxs++
                        }

                        for ((index, output) in ourOutputs) {
                            if (utxoDao.findByOutpoint(summary.txid, index) != null) continue
                            utxoDao.insert(
                                UtxoEntity(
                                    txHash = summary.txid,
                                    outputIndex = index,
                                    value = output.amount,
                                    scriptPubKey = AddressEncoder
                                        .addressToScriptPubKey(output.address)
                                        .toHex(),
                                    blockHeight = detail.blockHeight,
                                    spent = false
                                )
                            )
                            importedUtxos++
                        }
                    }
                }

                start += TX_PAGE_SIZE
                if (page.size < TX_PAGE_SIZE) return@repeat
            }
        }

        val pruned = reconcileUtxos(addresses, utxoDao)
        val localBalance = utxoDao.getUnspent().sumOf { it.value }
        val explorerBalance = addresses.sumOf { ExplorerApi.getAddressBalance(it) ?: 0L }
        if (importedUtxos > 0 || importedTxs > 0 || pruned > 0) {
            Log.i(
                TAG,
                "Importados: $importedUtxos UTXOs, $importedTxs txs, $pruned UTXOs obsoletos | " +
                    "saldo local=$localBalance explorer=$explorerBalance"
            )
        } else if (explorerBalance != localBalance) {
            Log.w(
                TAG,
                "Saldo explorer ($explorerBalance) != local ($localBalance) após reconciliação"
            )
        }
    }

    /**
     * Remove UTXOs locais que excedem o saldo confirmado no explorer.
     * Isso evita tentativas de gasto com outputs já consumidos na rede.
     */
    suspend fun reconcileUtxos(addresses: List<String>, utxoDao: UtxoDao): Int {
        val explorerTotal = addresses.sumOf { ExplorerApi.getAddressBalance(it) ?: 0L }
        val unspent = utxoDao.getUnspent().sortedBy { it.blockHeight }
        val localTotal = unspent.sumOf { it.value }
        if (localTotal <= explorerTotal) return 0

        var excess = localTotal - explorerTotal
        var pruned = 0
        Log.w(TAG, "Reconciliando UTXOs: local=$localTotal explorer=$explorerTotal excesso=$excess")

        for (utxo in unspent.reversed()) {
            if (excess <= 0) break
            utxoDao.markSpent(utxo.txHash, utxo.outputIndex)
            excess -= utxo.value
            pruned++
        }
        return pruned
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
