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

    @Volatile
    private var synced = false

    private val connectedPeers = linkedSetOf<String>()

    suspend fun startSync() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        try {
            runSyncOnce()
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

    /** Verificação periódica: não baixa headers se já sincronizado com a mainnet. */
    suspend fun verifySync() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        val networkTip = ExplorerApi.getBlockCountWithRetry() ?: return@withContext
        enforceChainIntegrity(networkTip)

        val localTip = blockDao.getTip() ?: return@withContext
        if (isSyncedWithMainnet(localTip, networkTip)) {
            synced = true
            updateProgress(100, isSyncing = false, height = localTip.height)
            return@withContext
        }

        if (localTip.height < networkTip) {
            runSyncOnce()
        }
    }

    private suspend fun runSyncOnce() {
        updateProgress(0, isSyncing = true)
        synced = false

        if (!ensureGenesis()) {
            resetChain()
            ensureGenesis()
        }

        val networkTip = ExplorerApi.getBlockCountWithRetry()
        if (networkTip == null) {
            Log.w(TAG, "Explorer indisponível — sync de headers bloqueada")
            updateProgress(0, isSyncing = true)
            return
        }

        enforceChainIntegrity(networkTip)

        val localTip = blockDao.getTip()
        if (localTip != null && isSyncedWithMainnet(localTip, networkTip)) {
            synced = true
            updateProgress(100, isSyncing = false, height = localTip.height)
            return
        }

        val peers = PeerDiscovery.discoverPeers()
        if (peers.isEmpty()) {
            updateProgress(0, isSyncing = true)
            return
        }

        for (peer in peers) {
            if (!running) return
            val client = P2PClient(peer)
            if (!client.connect(localTip?.height ?: 0)) continue
            if (!client.handshake()) {
                client.disconnect()
                continue
            }

            connectedPeers.add("$peer:${ChainParams.P2P_PORT}")
            updateProgress(5, isSyncing = true, peerHost = peer)

            when (val result = syncHeaders(client, networkTip)) {
                HeaderSyncResult.Success,
                is HeaderSyncResult.AlreadySynced -> {
                    val tip = blockDao.getTip()
                    val refreshedTip = ExplorerApi.getBlockCount() ?: networkTip
                    if (tip != null && isSyncedWithMainnet(tip, refreshedTip)) {
                        synced = true
                        syncBlocks(client, refreshedTip)
                        updateProgress(100, isSyncing = false, peerHost = peer, height = tip.height)
                    } else {
                        Log.w(TAG, "Tip local não confere com explorer após sync")
                        resetChain()
                        ensureGenesis()
                    }
                    client.disconnect()
                    return
                }
                is HeaderSyncResult.Failed -> {
                    Log.w(TAG, "Falha sync com $peer: ${result.reason}")
                    client.disconnect()
                }
            }
            client.disconnect()
        }
        updateProgress(0, isSyncing = true)
    }

    private suspend fun isSyncedWithMainnet(tip: BlockHeaderEntity, networkTip: Int): Boolean {
        if (tip.height < networkTip) return false
        if (tip.height > networkTip) return false
        return ExplorerApi.verifyBlockHash(tip.height, tip.hash)
    }

    private suspend fun enforceChainIntegrity(networkTip: Int) {
        val localTip = blockDao.getTip() ?: return

        if (localTip.height > networkTip) {
            Log.w(TAG, "Podando altura ${localTip.height} > rede $networkTip")
            blockDao.deleteAbove(networkTip)
        }

        val current = blockDao.getTip() ?: return
        if (current.height > 0 && !ExplorerApi.verifyBlockHash(1, ChainValidator.CHECKPOINTS[1]!!)) {
            Log.e(TAG, "Checkpoint 1 inválido no explorer")
        }

        if (current.height >= networkTip && !ExplorerApi.verifyBlockHash(current.height, current.hash)) {
            Log.e(TAG, "Tip hash diverge do explorer — resetando cadeia")
            resetChain()
            ensureGenesis()
            return
        }

        val checkHeight = minOf(current.height, networkTip)
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
        return ChainValidator.validateGenesis(blockDao.getByHeight(0)?.hash ?: return false)
    }

    private suspend fun resetChain() {
        blockDao.deleteAll()
        synced = false
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

    private suspend fun syncHeaders(client: P2PClient, initialNetworkTip: Int): HeaderSyncResult {
        var networkTip = initialNetworkTip
        var tip = blockDao.getTip() ?: return HeaderSyncResult.Failed("Sem tip local")

        if (tip.height >= networkTip) {
            return if (ExplorerApi.verifyBlockHash(tip.height, tip.hash)) {
                HeaderSyncResult.AlreadySynced(tip.height)
            } else {
                HeaderSyncResult.Failed("Tip local não confere com mainnet")
            }
        }

        val hashStop = ExplorerApi.getBlockHash(networkTip)
            ?.let { UInt256.fromHex(it) }
            ?: UInt256.ZERO

        var locator = listOf(UInt256.fromHex(tip.hash))
        var batchesWithoutProgress = 0

        while (running) {
            networkTip = ExplorerApi.getBlockCount() ?: networkTip
            if (tip.height >= networkTip) break

            client.requestHeaders(locator, hashStop)
            var receivedHeaders = false

            while (running) {
                val msg = client.readMessage() ?: break
                when (msg.command) {
                    "headers" -> {
                        val headers = P2PMessage.parseHeadersPayload(msg.payload)
                        if (ChainValidator.shouldStopHeadersBatch(headers.size)) {
                            return HeaderSyncResult.AlreadySynced(tip.height)
                        }

                        var inserted = 0
                        var parent = tip
                        for (header in headers) {
                            val nextHeight = parent.height + 1
                            if (nextHeight > networkTip) {
                                return HeaderSyncResult.AlreadySynced(networkTip)
                            }

                            when (val validation = ChainValidator.validateHeader(
                                header, parent, nextHeight, networkTip
                            )) {
                                is ChainValidator.ValidationResult.Invalid -> {
                                    Log.e(TAG, "Header $nextHeight rejeitado: ${validation.reason}")
                                    return HeaderSyncResult.Failed(validation.reason)
                                }
                                ChainValidator.ValidationResult.Valid -> Unit
                            }

                            val entity = BlockHeaderEntity(
                                height = nextHeight,
                                hash = header.getHash().toHex(),
                                prevHash = parent.hash,
                                merkleRoot = header.merkleRoot.toHex(),
                                time = header.time,
                                bits = header.bits,
                                nonce = header.nonce,
                                version = header.version
                            )
                            blockDao.insert(entity)
                            parent = entity
                            tip = entity
                            inserted++
                        }

                        if (inserted == 0) {
                            batchesWithoutProgress++
                            if (batchesWithoutProgress >= 2) {
                                return HeaderSyncResult.AlreadySynced(tip.height)
                            }
                        } else {
                            batchesWithoutProgress = 0
                            receivedHeaders = true
                        }

                        val progress = minOf(50, (tip.height * 50) / networkTip.coerceAtLeast(1))
                        updateProgress(progress, isSyncing = true, height = tip.height)
                        locator = listOf(UInt256.fromHex(tip.hash))

                        if (!ChainValidator.isFullBatch(headers.size) || tip.height >= networkTip) {
                            if (tip.height >= networkTip) {
                                return HeaderSyncResult.AlreadySynced(tip.height)
                            }
                        }
                        break
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                    "inv" -> Unit
                }
            }

            if (!receivedHeaders) break
            if (tip.height >= networkTip) break
        }

        val finalTip = blockDao.getTip()?.height ?: 0
        return when {
            finalTip >= networkTip -> HeaderSyncResult.AlreadySynced(finalTip)
            finalTip > 0 -> HeaderSyncResult.Success
            else -> HeaderSyncResult.Failed("Nenhum header válido")
        }
    }

    private suspend fun syncBlocks(client: P2PClient, networkTip: Int) {
        val watchScripts = walletManager.getAllReceiveScriptPubKeys().toSet()
        val maxHeight = minOf(blockDao.getTip()?.height ?: 0, networkTip)
        var current = 1
        val batchSize = 10

        while (current <= maxHeight && running) {
            val header = blockDao.getByHeight(current) ?: break
            client.requestBlocks(listOf(UInt256.fromHex(header.hash)))

            while (running) {
                val msg = client.readMessage() ?: break
                when (msg.command) {
                    "block" -> {
                        val block = Block.deserialize(msg.payload)
                        if (!block.header.getHash().toHex().equals(header.hash, ignoreCase = true)) break
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
        val syncing = isSyncing && !synced
        _syncProgress.value = SyncProgress(
            height = blockHeight,
            progress = progress.coerceIn(0, 100),
            isSyncing = syncing,
            peer = peerHost,
            connectedPeers = peersList,
            blockCount = blockHeight
        )
        syncDao.insert(
            SyncStateEntity(
                bestHeight = tip?.height ?: blockHeight,
                bestHash = tip?.hash ?: ChainParams.GENESIS_HASH,
                isSyncing = syncing,
                progress = progress.coerceIn(0, 100),
                peerHost = peerHost,
                statusMessage = if (synced) "Sincronizado" else "",
                lastError = null,
                blockCount = tip?.height ?: blockHeight,
                connectedPeers = peersList.joinToString(", ")
            )
        )
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
