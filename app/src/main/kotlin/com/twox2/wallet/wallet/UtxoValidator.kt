package com.twox2.wallet.wallet

import android.util.Log
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.network.ExplorerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object UtxoValidator {
    private const val TAG = "TwoX2UtxoValidator"

    suspend fun filterSpendable(
        utxos: List<UtxoEntity>,
        addressSet: Set<String>
    ): List<UtxoEntity> = withContext(Dispatchers.IO) {
        if (utxos.isEmpty()) return@withContext emptyList()
        coroutineScope {
            utxos.map { utxo ->
                async {
                    if (isOutputAvailable(utxo, addressSet)) utxo else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun isOutputAvailable(utxo: UtxoEntity, addressSet: Set<String>): Boolean {
        val detail = ExplorerApi.getTxFast(utxo.txHash) ?: run {
            Log.w(TAG, "TX ${utxo.txHash} não encontrada no explorer")
            return false
        }
        val output = detail.outputs.getOrNull(utxo.outputIndex) ?: run {
            Log.w(TAG, "Output ${utxo.txHash}:${utxo.outputIndex} não existe — provavelmente gasto")
            return false
        }
        if (output.amount != utxo.value) {
            Log.w(TAG, "Valor divergente em ${utxo.txHash}:${utxo.outputIndex}")
            return false
        }
        if (output.address !in addressSet) {
            Log.w(TAG, "Endereço do output não pertence à carteira")
            return false
        }
        return true
    }
}
