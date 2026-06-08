package com.twox2.wallet.network

import android.util.Log
import com.twox2.wallet.chain.ChainParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object PeerDiscovery {
    private const val TAG = "TwoX2P2P"

    suspend fun discoverPeers(maxPeers: Int = 12): List<String> = withContext(Dispatchers.IO) {
        val peers = linkedSetOf<String>()

        // IPs fixos da mainnet 2x2Coin (fallback se DNS falhar)
        peers.addAll(ChainParams.DNS_SEEDS.filter { it.firstOrNull()?.isDigit() == true })

        for (seed in ChainParams.DNS_SEEDS) {
            if (peers.size >= maxPeers) break
            runCatching {
                val addresses = InetAddress.getAllByName(seed)
                addresses.mapNotNull { it.hostAddress }.forEach { peers.add(it) }
            }.onFailure {
                Log.w(TAG, "DNS falhou para $seed: ${it.message}")
            }
        }

        Log.d(TAG, "Peers descobertos: ${peers.take(maxPeers)}")
        peers.take(maxPeers)
    }
}
