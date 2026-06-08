package com.twox2.wallet.sync

import android.content.Context
import android.util.Log
import com.twox2.wallet.chain.Block
import com.twox2.wallet.chain.BlockHeader
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.chain.ChainValidator
import com.twox2.wallet.chain.UInt256
import com.twox2.wallet.data.db.BlockHeaderEntity
import com.twox2.wallet.data.db.SyncStateEntity
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.network.ExplorerApi
import com.twox2.wallet.network.P2PClient
import com.twox2.wallet.network.P2PMessage
import com.twox2.wallet.network.PeerDiscovery
import com.twox2.wallet.wallet.WalletManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private val connectedPeers = linkedSetOf<String>()

    suspend fun startSync() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        try {
            var retries = 0
            while (running && retries < 8) {
                when (runSyncCycle()) {
                    SyncCycleResult.Completed -> return@withContext
                    SyncCycleResult.RetryLater -> {
                        retries++
                        delay(8_000)
                    }
                }
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

    suspend fun verifySync() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        val networkTip = resolveNetworkTip(null)
        enforceChainLimits(networkTip)
        val localHeight = blockDao.getTip()?.height ?: 0
        if (networkTip != null && localHeight >= networkTip) {
            updateProgress(100, isSyncing = false, height = localHeight)
            return@withContext
        }
        runSyncCycle()
    }

    private suspend fun runSyncCycle(): SyncCycleResult {
        updateProgress(0, isSyncing = true)
        if (!ensureGenesis()) {
            Log.e(TAG, "Genesis inválido — resetando cadeia")
            resetChain()
            ensureGenesis()
        }

        val peers = PeerDiscovery.discoverPeers()
        if (peers.isEmpty()) {
            updateProgress(0, isSyncing = true)
            return SyncCycleResult.RetryLater
        }

        var networkTip: Int? = null

        for (peer in peers) {
            if (!running) return SyncCycleResult.RetryLater
            val client = P2PClient(peer)

            if (!client.connect(blockDao.getTip()?.height ?: 0)) continue
            if (!client.handshake()) {
                client.disconnect()
                continue
            }

            networkTip = resolveNetworkTip(client.peerStartHeight)
            enforceChainLimits(networkTip)

            val localTip = blockDao.getTip()
            if (networkTip != null && localTip != null && localTip.height >= networkTip) {
                connectedPeers.add("$peer:${ChainParams.P2P_PORT}")
                updateProgress(100, isSyncing = false, peerHost = peer, height = localTip.height)
                client.disconnect()
                return SyncCycleResult.Completed
            }

            connectedPeers.add("$peer:${ChainParams.P2P_PORT}")
            updateProgress(5, isSyncing = true, peerHost = peer)

            when (val result = syncHeaders(client, networkTip)) {
                is HeaderSyncResult.Success -> {
                    syncBlocks(client, networkTip)
                    val tip = blockDao.getTip()
                    updateProgress(100, isSyncing = false, peerHost = peer, height = tip?.height ?: 0)
                    client.disconnect()
                    return SyncCycleResult.Completed
                }
                is HeaderSyncResult.AlreadySynced -> {
                    updateProgress(100, isSyncing = false, peerHost = peer, height = result.height)
                    client.disconnect()
                    return SyncCycleResult.Completed
                }
                is HeaderSyncResult.Failed -> {
                    Log.w(TAG, "Falha sync headers com $peer: ${result.reason}")
                    client.disconnect()
                }
            }
        }
        updateProgress(0, isSyncing = true)
        return SyncCycleResult.RetryLater
    }

    private suspend fun resolveNetworkTip(peerHeight: Int?): Int? {
        val explorerTip = ExplorerApi.getBlockCountWithRetry()
        return when {
            explorerTip != null && peerHeight != null && peerHeight > 0 ->
                minOf(explorerTip, peerHeight)
            explorerTip != null -> explorerTip
            peerHeight != null && peerHeight > 0 -> peerHeight
            else -> null
        }
    }

    private suspend fun enforceChainLimits(networkTip: Int?) {
        val localTip = blockDao.getTip() ?: return

        if (networkTip != null && localTip.height > networkTip) {
            Log.w(TAG, "Altura local (${localTip.height}) > rede ($networkTip), podando")
            blockDao.deleteAbove(networkTip)
        }

        if (networkTip != null && localTip.height > networkTip + 50) {
            Log.e(TAG, "Cadeia muito acima da rede — reset completo")
            resetChain()
            ensureGenesis()
            return
        }

        val checkHeight = minOf(blockDao.getTip()?.height ?: 0, networkTip ?: localTip.height)
        when (val result = ChainValidator.verifyStoredChain(
            getByHeight = { blockDao.getByHeight(it) },
            maxHeight = checkHeight
        )) {
            is ChainValidator.ValidationResult.Invalid -> {
                Log.e(TAG, "Cadeia inválida: ${result.reason} — reset")
                resetChain()
                ensureGenesis()
            }
            ChainValidator.ValidationResult.Valid -> Unit
        }
    }

    private suspend fun ensureGenesis(): Boolean {
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
            return true
        }
        val genesis = blockDao.getByHeight(0) ?: return false
        return ChainValidator.validateGenesis(genesis.hash)
    }

    private suspend fun resetChain() {
        blockDao.deleteAll()
        syncDao.insert(
            SyncStateEntity(
                bestHeight = 0,
                bestHash = ChainParams.GENESIS_HASH,
                isSyncing = false,
                progress = 0,
                peerHost = null,
                blockCount = 0
            )
        )
    }

    private suspend fun syncHeaders(client: P2PClient, networkTip: Int?): HeaderSyncResult {
        var tip = blockDao.getTip() ?: return HeaderSyncResult.Failed("Sem tip local")

        if (networkTip != null && tip.height >= networkTip) {
            return HeaderSyncResult.AlreadySynced(tip.height)
        }

        val hashStop = networkTip?.let { ExplorerApi.getBlockHash(it) }
            ?.let { UInt256.fromHex(it) }
            ?: UInt256.ZERO

        var locator = listOf(UInt256.fromHex(tip.hash))
        var downloadedInCycle = 0

        while (running) {
            client.requestHeaders(locator, hashStop)
            var done = false

            while (running) {
                val msg = client.readMessage() ?: break
                when (msg.command) {
                    "headers" -> {
                        val headers = P2PMessage.parseHeadersPayload(msg.payload)
                        if (ChainValidator.shouldStopHeadersBatch(headers.size)) {
                            done = true
                            break
                        }

                        var prevHash = tip.hash
                        for (header in headers) {
                            val nextHeight = tip.height + 1
                            when (val validation = ChainValidator.validateHeader(
                                header, prevHash, nextHeight, networkTip
                            )) {
                                is ChainValidator.ValidationResult.Invalid -> {
                                    Log.e(TAG, "Header rejeitado altura $nextHeight: ${validation.reason}")
                                    return HeaderSyncResult.Failed(validation.reason)
                                }
                                ChainValidator.ValidationResult.Valid -> Unit
                            }

                            val entity = BlockHeaderEntity(
                                height = nextHeight,
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
                            downloadedInCycle++

                            if (networkTip != null && nextHeight >= networkTip) {
                                done = true
                                break
                            }
                        }

                        val progress = if (networkTip != null && networkTip > 0) {
                            minOf(50, (tip.height * 50) / networkTip)
                        } else {
                            minOf(50, 5 + downloadedInCycle / 5)
                        }
                        updateProgress(progress, isSyncing = true, height = tip.height)
                        locator = listOf(UInt256.fromHex(tip.hash))

                        if (done || !ChainValidator.isFullBatch(headers.size)) {
                            done = true
                        }
                        break
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                    "inv" -> Unit
                }
            }
            if (done) break
        }

        val finalHeight = blockDao.getTip()?.height ?: 0
        return when {
            networkTip != null && finalHeight >= networkTip ->
                HeaderSyncResult.AlreadySynced(finalHeight)
            downloadedInCycle > 0 -> HeaderSyncResult.Success
            networkTip != null && finalHeight >= networkTip - 1 ->
                HeaderSyncResult.AlreadySynced(finalHeight)
            else -> HeaderSyncResult.Failed("Nenhum header novo recebido")
        }
    }

    private suspend fun syncBlocks(client: P2PClient, networkTip: Int?) {
        val watchScripts = walletManager.getAllReceiveScriptPubKeys().toSet()
        val tip = blockDao.getTip() ?: return
        val maxHeight = networkTip?.coerceAtMost(tip.height) ?: tip.height
        val batchSize = 10
        var current = 1

        while (current <= maxHeight && running) {
            val header = blockDao.getByHeight(current) ?: break
            client.requestBlocks(listOf(UInt256.fromHex(header.hash)))

            while (running) {
                val msg = client.readMessage() ?: break
                when (msg.command) {
                    "block" -> {
                        val block = Block.deserialize(msg.payload)
                        val blockHash = block.header.getHash().toHex()
                        if (!blockHash.equals(header.hash, ignoreCase = true)) {
                            Log.e(TAG, "Hash bloco $current inválido")
                            break
                        }
                        processBlock(block, current, watchScripts)
                        break
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                    "inv", "headers" -> Unit
                }
            }

            val progress = 50 + ((current.toFloat() / maxHeight.coerceAtLeast(1)) * 50).toInt()
            updateProgress(progress, isSyncing = true, height = current)
            current += batchSize
        }
    }

    private suspend fun processBlock(block: Block, height: Int, watchScripts: Set<String>) {
        block.transactions.forEach { tx ->
            if (tx.isCoinBase()) return@forEach
            val txHash = tx.getHash().toHex()

            tx.outputs.forEachIndexed { index, output ->
                val scriptHex = output.scriptPubKey.toHex()
                if (scriptHex in watchScripts) {
                    utxoDao.insert(
                        UtxoEntity(
                            txHash = txHash,
                            outputIndex = index,
                            value = output.value,
                            scriptPubKey = scriptHex,
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
        val tip = blockDao.getTip()
        val blockHeight = height ?: tip?.height ?: 0
        val peersList = connectedPeers.toList()
        _syncProgress.value = SyncProgress(
            height = blockHeight,
            progress = progress.coerceIn(0, 100),
            isSyncing = isSyncing,
            peer = peerHost,
            connectedPeers = peersList,
            blockCount = blockHeight
        )
        syncDao.insert(
            SyncStateEntity(
                bestHeight = tip?.height ?: blockHeight,
                bestHash = tip?.hash ?: ChainParams.GENESIS_HASH,
                isSyncing = isSyncing,
                progress = progress.coerceIn(0, 100),
                peerHost = peerHost,
                statusMessage = "",
                lastError = null,
                blockCount = tip?.height ?: blockHeight,
                connectedPeers = peersList.joinToString(", ")
            )
        )
    }

    private sealed class SyncCycleResult {
        data object Completed : SyncCycleResult()
        data object RetryLater : SyncCycleResult()
    }

    private sealed class HeaderSyncResult {
        data object Success : HeaderSyncResult()
        data class AlreadySynced(val height: Int) : HeaderSyncResult()
        data class Failed(val reason: String) : HeaderSyncResult()
    }

    companion object {
        private const val TAG = "TwoX2Sync"
    }
}

data class SyncProgress(
    val height: Int = 0,
    val progress: Int = 0,
    val isSyncing: Boolean = false,
    val peer: String? = null,
    val connectedPeers: List<String> = emptyList(),
    val blockCount: Int = 0
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
