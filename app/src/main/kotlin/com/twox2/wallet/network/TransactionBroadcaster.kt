package com.twox2.wallet.network

import android.content.Context
import android.util.Log
import com.twox2.wallet.chain.Transaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

object TransactionBroadcaster {
    private const val TAG = "TwoX2Broadcast"
    private const val TOTAL_TIMEOUT_MS = 28_000L
    private const val PEER_RELAY_TIMEOUT_MS = 8_000L
    private const val PARALLEL_PEERS = 8
    private const val QUICK_VERIFY_ATTEMPTS = 2
    private const val QUICK_VERIFY_DELAY_MS = 2_000L
    private const val PREFS = "broadcast_prefs"
    private const val KEY_GOOD_PEERS = "good_peers"

    sealed class BroadcastResult {
        data object Success : BroadcastResult()
        data class Rejected(val reason: String) : BroadcastResult()
        data object Failed : BroadcastResult()
    }

    suspend fun broadcast(tx: Transaction, context: Context? = null): BroadcastResult =
        withContext(Dispatchers.IO) {
            val txHash = tx.getHash().toHex()
            val peers = orderPeers(context, PeerDiscovery.discoverPeers(12))
            Log.i(TAG, "Transmitindo $txHash para até $PARALLEL_PEERS peers em paralelo")

            val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                relayParallel(tx, peers.take(PARALLEL_PEERS), context)
            }

            when (result) {
                is BroadcastResult.Success -> quickVerify(txHash)
                is BroadcastResult.Rejected -> result
                is BroadcastResult.Failed -> result
                null -> {
                    Log.w(TAG, "Timeout ao transmitir $txHash")
                    BroadcastResult.Failed
                }
            }
        }

    private suspend fun relayParallel(
        tx: Transaction,
        peers: List<String>,
        context: Context?
    ): BroadcastResult = coroutineScope {
        val outcome = CompletableDeferred<BroadcastResult>()
        val lastReject = AtomicReference<String?>(null)

        val jobs = peers.map { peer ->
            launch(Dispatchers.IO) {
                if (outcome.isCompleted) return@launch
                val relay = withTimeoutOrNull(PEER_RELAY_TIMEOUT_MS) {
                    relayToPeer(peer, tx, context)
                }
                when (relay) {
                    RelayOutcome.Relayed -> outcome.complete(BroadcastResult.Success)
                    is RelayOutcome.Rejected -> {
                        lastReject.compareAndSet(null, relay.reason)
                    }
                    RelayOutcome.Failed, null -> Unit
                }
            }
        }

        launch {
            jobs.joinAll()
            if (!outcome.isCompleted) {
                val reject = lastReject.get()
                outcome.complete(
                    if (reject != null) BroadcastResult.Rejected(reject)
                    else BroadcastResult.Failed
                )
            }
        }

        outcome.await()
    }

    private suspend fun relayToPeer(peer: String, tx: Transaction, context: Context?): RelayOutcome {
        val client = P2PClient(peer)
        return try {
            if (!client.connectForBroadcast()) return RelayOutcome.Failed
            if (!client.handshakeForBroadcast()) return RelayOutcome.Failed
            when (val sendResult = client.sendTransactionFast(tx)) {
                is P2PClient.SendResult.Relayed -> {
                    rememberGoodPeer(peer, context)
                    RelayOutcome.Relayed
                }
                is P2PClient.SendResult.Rejected -> RelayOutcome.Rejected(sendResult.reason)
                is P2PClient.SendResult.Failed -> RelayOutcome.Failed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha com peer $peer: ${e.message}")
            RelayOutcome.Failed
        } finally {
            client.disconnect()
        }
    }

    private suspend fun quickVerify(txHash: String): BroadcastResult {
        repeat(QUICK_VERIFY_ATTEMPTS) { attempt ->
            if (ExplorerApi.txExistsFast(txHash)) {
                Log.i(TAG, "TX $txHash visível no explorer")
                return BroadcastResult.Success
            }
            if (attempt < QUICK_VERIFY_ATTEMPTS - 1) delay(QUICK_VERIFY_DELAY_MS)
        }
        Log.d(TAG, "TX $txHash relayed (explorer ainda não indexou)")
        return BroadcastResult.Success
    }

    private fun orderPeers(context: Context?, peers: List<String>): List<String> {
        val good = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getStringSet(KEY_GOOD_PEERS, emptySet())
            ?.orEmpty()
            ?.toList()
            ?: emptyList()
        val preferred = good.filter { it in peers }
        val rest = peers.filter { it !in preferred }.shuffled()
        return (preferred + rest).distinct()
    }

    private fun rememberGoodPeer(peer: String, context: Context?) {
        val prefs = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val current = prefs.getStringSet(KEY_GOOD_PEERS, linkedSetOf())?.toMutableSet() ?: mutableSetOf()
        current.add(peer)
        while (current.size > 10) {
            current.remove(current.first())
        }
        prefs.edit().putStringSet(KEY_GOOD_PEERS, current).apply()
    }

    private sealed class RelayOutcome {
        data object Relayed : RelayOutcome()
        data class Rejected(val reason: String) : RelayOutcome()
        data object Failed : RelayOutcome()
    }
}
