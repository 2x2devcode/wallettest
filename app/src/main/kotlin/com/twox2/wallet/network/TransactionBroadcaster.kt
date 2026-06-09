package com.twox2.wallet.network

import android.util.Log
import com.twox2.wallet.chain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object TransactionBroadcaster {
    private const val TAG = "TwoX2Broadcast"
    private const val PEER_TIMEOUT_MS = 12_000L
    private const val MAX_PEERS = 12

    suspend fun broadcast(tx: Transaction): Boolean = withContext(Dispatchers.IO) {
        val peers = PeerDiscovery.discoverPeers(MAX_PEERS)
        for (peer in peers) {
            val sent = withTimeoutOrNull(PEER_TIMEOUT_MS) {
                val client = P2PClient(peer)
                try {
                    if (!client.connect()) return@withTimeoutOrNull false
                    if (!client.handshake()) return@withTimeoutOrNull false
                    client.sendTransaction(tx)
                    Log.i(TAG, "Transação enviada via $peer")
                    true
                } finally {
                    client.disconnect()
                }
            }
            if (sent == true) return@withContext true
        }
        Log.w(TAG, "Falha ao transmitir transação para ${peers.size} peers")
        false
    }
}
