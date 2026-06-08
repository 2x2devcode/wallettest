package com.twox2.wallet.sync

import android.content.Context
import android.util.Log
import com.twox2.wallet.chain.Block
import com.twox2.wallet.chain.BlockHeader
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.chain.UInt256
import com.twox2.wallet.data.db.BlockHeaderEntity
import com.twox2.wallet.data.db.SyncStateEntity
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.P2PClient
import com.twox2.wallet.network.P2PMessage
import com.twox2.wallet.network.PeerDiscovery
import com.twox2.wallet.wallet.WalletManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class BlockchainSyncManager(context: Context) {
    private val walletManager = WalletManager.get(context)
    private val db = walletManager.getDatabase()
    private val blockDao = db.blockHeaderDao()
    private val utxoDao = db.utxoDao()
    private val txDao = db.walletTransactionDao()
    private val syncDao = db.syncStateDao()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress

    @Volatile
    private var running = false

    suspend fun startSync() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        try {
            while (running) {
                val success = runSyncCycle()
                if (success) return@withContext
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronização", e)
            updateProgress(0, isSyncing = true)
        } finally {
            running = false
        }
    }

    fun stopSync() {
        running = false
    }

    private suspend fun runSyncCycle(): Boolean {
        updateProgress(0, isSyncing = true)
        ensureGenesis()
        val startHeight = blockDao.getTip()?.height ?: 0

        val peers = PeerDiscovery.discoverPeers()
        if (peers.isEmpty()) {
            updateProgress(0, isSyncing = true)
            return false
        }

        for (peer in peers) {
            if (!running) return false
            val client = P2PClient(peer)
            updateProgress(2, isSyncing = true)

            if (!client.connect(startHeight)) continue
            if (!client.handshake()) {
                client.disconnect()
                continue
            }

            updateProgress(5, isSyncing = true, peerHost = peer)

            if (syncHeaders(client)) {
                syncBlocks(client)
                val tip = blockDao.getTip()
                updateProgress(
                    100,
                    isSyncing = false,
                    peerHost = peer,
                    height = tip?.height ?: 0
                )
                client.disconnect()
                return true
            }
            client.disconnect()
        }
        updateProgress(0, isSyncing = true)
        return false
    }

    private suspend fun ensureGenesis() {
        if (blockDao.count() == 0) {
            val genesis = BlockHeader.GENESIS
            blockDao.insert(
                BlockHeaderEntity(
                    height = 0,
                    hash = genesis.getHash().toHex(),
                    prevHash = genesis.prevBlock.toHex(),
                    merkleRoot = genesis.merkleRoot.toHex(),
                    time = genesis.time,
                    bits = genesis.bits,
                    nonce = genesis.nonce,
                    version = genesis.version
                )
            )
        }
    }

    private suspend fun syncHeaders(client: P2PClient): Boolean {
        var tip = blockDao.getTip() ?: return false
        var locator = listOf(UInt256.fromHex(tip.hash))
        var totalDownloaded = 0

        while (running) {
            client.requestHeaders(locator)
            var done = false

            while (running) {
                val msg = client.readMessage() ?: break
                when (msg.command) {
                    "headers" -> {
                        val headers = P2PMessage.parseHeadersPayload(msg.payload)
                        if (headers.isEmpty()) {
                            done = true
                            break
                        }
                        var prevHash = tip.hash
                        headers.forEach { header ->
                            val entity = BlockHeaderEntity(
                                height = tip.height + 1,
                                hash = header.getHash().toHex(),
                                prevHash = prevHash,
                                merkleRoot = header.merkleRoot.toHex(),
                                time = header.time,
                                bits = header.bits,
                                nonce = header.nonce,
                                version = header.version
                            )
                            blockDao.insert(entity)
                            tip = entity
                            prevHash = entity.hash
                            totalDownloaded++
                        }
                        val progress = minOf(50, 5 + totalDownloaded / 5)
                        updateProgress(progress, isSyncing = true, height = tip.height)
                        locator = listOf(UInt256.fromHex(tip.hash))
                        break
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                    "inv" -> Unit
                }
            }
            if (done) break
        }
        return totalDownloaded > 0 || (blockDao.getTip()?.height ?: 0) > 0
    }

    private suspend fun syncBlocks(client: P2PClient) {
        val watchScript = walletManager.getReceiveScriptPubKey().toHex()
        val tip = blockDao.getTip() ?: return
        val batchSize = 10
        var current = 1

        while (current <= tip.height && running) {
            val header = blockDao.getByHeight(current) ?: break
            client.requestBlocks(listOf(UInt256.fromHex(header.hash)))

            while (running) {
                val msg = client.readMessage() ?: break
                when (msg.command) {
                    "block" -> {
                        val block = Block.deserialize(msg.payload)
                        processBlock(block, current, watchScript)
                        break
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                    "inv", "headers" -> Unit
                }
            }

            val progress = 50 + ((current.toFloat() / tip.height.coerceAtLeast(1)) * 50).toInt()
            updateProgress(progress, isSyncing = true, height = current)
            current += batchSize
        }
    }

    private suspend fun processBlock(block: Block, height: Int, watchScript: String) {
        block.transactions.forEach { tx ->
            if (tx.isCoinBase()) return@forEach
            val txHash = tx.getHash().toHex()

            tx.outputs.forEachIndexed { index, output ->
                if (output.scriptPubKey.toHex() == watchScript) {
                    utxoDao.insert(
                        UtxoEntity(
                            txHash = txHash,
                            outputIndex = index,
                            value = output.value,
                            scriptPubKey = watchScript,
                            blockHeight = height
                        )
                    )
                    txDao.insert(
                        WalletTransactionEntity(
                            txHash = txHash,
                            blockHeight = height,
                            timestamp = block.header.time,
                            amount = output.value,
                            fee = 0,
                            type = "received",
                            address = "",
                            confirmations = 0
                        )
                    )
                }
            }

            tx.inputs.forEach { input ->
                utxoDao.markSpent(input.prevTxHash.toHex(), input.prevIndex.toInt())
            }
        }
    }

    private suspend fun updateProgress(
        progress: Int,
        isSyncing: Boolean,
        peerHost: String? = null,
        height: Int? = null
    ) {
        val blockHeight = height ?: blockDao.getTip()?.height ?: 0
        _syncProgress.value = SyncProgress(
            height = blockHeight,
            progress = progress.coerceIn(0, 100),
            isSyncing = isSyncing,
            peer = peerHost
        )
        val tip = blockDao.getTip()
        syncDao.insert(
            SyncStateEntity(
                bestHeight = tip?.height ?: blockHeight,
                bestHash = tip?.hash ?: ChainParams.GENESIS_HASH,
                isSyncing = isSyncing,
                progress = progress.coerceIn(0, 100),
                peerHost = peerHost,
                statusMessage = "",
                lastError = null
            )
        )
    }

    companion object {
        private const val TAG = "TwoX2Sync"
    }
}

data class SyncProgress(
    val height: Int = 0,
    val progress: Int = 0,
    val isSyncing: Boolean = false,
    val peer: String? = null
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
