package com.twox2.wallet.network

import android.util.Log
import com.twox2.wallet.chain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object TransactionBroadcaster {
    private const val TAG = "TwoX2Broadcast"
    private const val PEER_TIMEOUT_MS = 15_000L
    private const val MAX_PEERS = 15

    suspend fun broadcast(tx: Transaction): Boolean = withContext(Dispatchers.IO) {
        val txHash = tx.getHash().toHex()
        val peers = PeerDiscovery.discoverPeers(MAX_PEERS)
        var relayed = 0

        for (peer in peers) {
            val sent = withTimeoutOrNull(PEER_TIMEOUT_MS) {
                val client = P2PClient(peer)
                try {
                    if (!client.connect()) return@withTimeoutOrNull false
                    if (!client.handshake()) return@withTimeoutOrNull false
                    if (client.sendTransaction(tx)) {
                        Log.i(TAG, "Transação $txHash relayed via $peer")
                        true
                    } else {
                        false
                    }
                } finally {
                    client.disconnect()
                }
            }
            if (sent == true) relayed++
            if (relayed >= 2) return@withContext true
        }

        if (relayed > 0) return@withContext true
        Log.w(TAG, "Falha ao transmitir $txHash para ${peers.size} peers")
        false
    }
}
