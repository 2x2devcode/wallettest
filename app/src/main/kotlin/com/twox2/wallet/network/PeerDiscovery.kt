package com.twox2.wallet.network

import android.util.Log
import com.twox2.wallet.chain.ChainParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object PeerDiscovery {
    private const val TAG = "TwoX2P2P"

    suspend fun discoverPeers(maxPeers: Int = 40): List<String> = withContext(Dispatchers.IO) {
        val peers = linkedSetOf<String>()

        ChainParams.FIXED_PEERS
            .map(::normalizePeerHost)
            .forEach { peers.add(it) }

        for (seed in ChainParams.DNS_SEEDS) {
            if (peers.size >= maxPeers) break
            runCatching {
                val addresses = InetAddress.getAllByName(seed)
                addresses.mapNotNull { it.hostAddress?.let(::normalizePeerHost) }.forEach { peers.add(it) }
            }.onFailure {
                Log.w(TAG, "DNS falhou para $seed: ${it.message}")
            }
        }

        Log.d(TAG, "Peers descobertos: ${peers.take(maxPeers)}")
        peers.take(maxPeers)
    }

    private fun normalizePeerHost(host: String): String {
        val trimmed = host.trim()
        return if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }
}
