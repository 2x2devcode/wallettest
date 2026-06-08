package com.twox2.wallet.network

import com.twox2.wallet.chain.ChainParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object PeerDiscovery {
    suspend fun discoverPeers(maxPeers: Int = 8): List<String> = withContext(Dispatchers.IO) {
        val peers = linkedSetOf<String>()
        for (seed in ChainParams.DNS_SEEDS) {
            if (peers.size >= maxPeers) break
            runCatching {
                val addresses = InetAddress.getAllByName(seed)
                addresses.mapNotNull { it.hostAddress }.forEach { peers.add(it) }
            }
        }
        peers.take(maxPeers)
    }
}
