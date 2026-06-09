package com.twox2.wallet.sync

import android.content.Context
import android.util.Log
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.ExplorerApi
import com.twox2.wallet.wallet.WalletManager

object ExplorerWalletSync {
    private const val TAG = "TwoX2ExplorerWallet"
    private const val TX_PAGE_SIZE = 50
    private const val MAX_TX_PAGES = 20

    suspend fun sync(context: Context) {
        val walletManager = WalletManager.get(context)
        val db = walletManager.getDatabase()
        val utxoDao = db.utxoDao()
        val txDao = db.walletTransactionDao()

        val addresses = walletManager.getAllReceiveLegacyAddresses()
        if (addresses.isEmpty()) return

        val addressSet = addresses.toSet()
        var importedUtxos = 0
        var importedTxs = 0

        for (address in addresses) {
            var start = 0
            repeat(MAX_TX_PAGES) {
                val page = ExplorerApi.getAddressTxs(address, start, TX_PAGE_SIZE)
                if (page.isEmpty()) return@repeat

                for (summary in page) {
                    if (summary.received <= 0 || summary.txid.isBlank()) continue
                    val detail = ExplorerApi.getTx(summary.txid) ?: continue
                    val ourOutputs = detail.outputs.withIndex()
                        .filter { it.value.address in addressSet && it.value.amount > 0 }

                    if (ourOutputs.isEmpty()) continue

                    if (!txDao.exists(summary.txid)) {
                        val totalReceived = ourOutputs.sumOf { it.value.amount }
                        val primaryAddress = ourOutputs.first().value.address
                        txDao.insert(
                            WalletTransactionEntity(
                                txHash = summary.txid,
                                blockHeight = detail.blockHeight,
                                timestamp = detail.timestamp,
                                amount = totalReceived,
                                fee = 0,
                                type = "received",
                                address = primaryAddress,
                                confirmations = 0
                            )
                        )
                        importedTxs++
                    }

                    for ((index, output) in ourOutputs) {
                        val scriptHex = AddressEncoder.addressToScriptPubKey(output.address).toHex()
                        if (utxoDao.findByOutpoint(summary.txid, index) == null) {
                            utxoDao.insert(
                                UtxoEntity(
                                    txHash = summary.txid,
                                    outputIndex = index,
                                    value = output.amount,
                                    scriptPubKey = scriptHex,
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

        if (importedUtxos > 0 || importedTxs > 0) {
            Log.i(TAG, "Explorer sync: +$importedUtxos UTXOs, +$importedTxs transações")
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
