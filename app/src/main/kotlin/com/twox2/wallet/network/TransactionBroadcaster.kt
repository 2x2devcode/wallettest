package com.twox2.wallet.network

import android.util.Log
import com.twox2.wallet.chain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object TransactionBroadcaster {
    private const val TAG = "TwoX2Broadcast"
    private const val PEER_TIMEOUT_MS = 20_000L
    private const val MAX_PEERS = 15
    private const val VERIFY_ATTEMPTS = 6
    private const val VERIFY_DELAY_MS = 5_000L

    sealed class BroadcastResult {
        data object Success : BroadcastResult()
        data class Rejected(val reason: String) : BroadcastResult()
        data object Failed : BroadcastResult()
    }

    suspend fun broadcast(tx: Transaction): BroadcastResult = withContext(Dispatchers.IO) {
        val txHash = tx.getHash().toHex()
        val peers = PeerDiscovery.discoverPeers(MAX_PEERS)
        var relayed = 0
        var lastReject: String? = null

        for (peer in peers) {
            val result = withTimeoutOrNull(PEER_TIMEOUT_MS) {
                val client = P2PClient(peer)
                try {
                    if (!client.connect()) return@withTimeoutOrNull null
                    if (!client.handshake()) return@withTimeoutOrNull null
                    when (val sendResult = client.sendTransaction(tx)) {
                        is P2PClient.SendResult.Relayed -> {
                            Log.i(TAG, "Transação $txHash relayed via $peer")
                            BroadcastResult.Success
                        }
                        is P2PClient.SendResult.Rejected -> {
                            Log.w(TAG, "Transação $txHash rejeitada por $peer: ${sendResult.reason}")
                            BroadcastResult.Rejected(sendResult.reason)
                        }
                        is P2PClient.SendResult.Failed -> null
                    }
                } finally {
                    client.disconnect()
                }
            }
            when (result) {
                is BroadcastResult.Success -> {
                    relayed++
                    if (relayed >= 2) return@withContext verifyOrSuccess(txHash)
                }
                is BroadcastResult.Rejected -> lastReject = result.reason
                else -> Unit
            }
        }

        if (relayed > 0) return@withContext verifyOrSuccess(txHash)
        if (lastReject != null) return@withContext BroadcastResult.Rejected(lastReject!!)
        Log.w(TAG, "Falha ao transmitir $txHash para ${peers.size} peers")
        BroadcastResult.Failed
    }

    private suspend fun verifyOrSuccess(txHash: String): BroadcastResult {
        repeat(VERIFY_ATTEMPTS) { attempt ->
            if (ExplorerApi.txExists(txHash)) {
                Log.i(TAG, "Transação $txHash confirmada no explorer")
                return BroadcastResult.Success
            }
            if (attempt < VERIFY_ATTEMPTS - 1) delay(VERIFY_DELAY_MS)
        }
        Log.w(TAG, "Transação $txHash transmitida mas ainda não visível no explorer")
        return BroadcastResult.Success
    }
}
