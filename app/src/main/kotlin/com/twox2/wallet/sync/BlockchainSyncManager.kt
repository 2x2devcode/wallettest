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

    private val connectedPeers = linkedSetOf<String>()

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

    suspend fun verifySync() {
        if (running) return
        runSyncCycle()
    }

    private suspend fun runSyncCycle(): Boolean {
        updateProgress(0, isSyncing = true)
        if (!ensureGenesis()) {
            Log.e(TAG, "Cadeia corrompida no genesis — resetando blocos")
            resetChain()
            ensureGenesis()
        }

        val networkTip = ExplorerApi.getBlockCount()
        pruneInvalidBlocks(networkTip)

        val localTip = blockDao.getTip()
        if (networkTip != null && localTip != null && localTip.height >= networkTip) {
            Log.d(TAG, "Já sincronizado: local=${localTip.height}, rede=$networkTip")
            updateProgress(100, isSyncing = false, height = localTip.height)
            return true
        }

        val startHeight = localTip?.height ?: 0
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

            connectedPeers.add("$peer:${ChainParams.P2P_PORT}")
            updateProgress(5, isSyncing = true, peerHost = peer)

            when (val result = syncHeaders(client, networkTip)) {
                is HeaderSyncResult.Success -> {
                    syncBlocks(client, networkTip)
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
                is HeaderSyncResult.AlreadySynced -> {
                    updateProgress(100, isSyncing = false, peerHost = peer, height = result.height)
                    client.disconnect()
                    return true
                }
                is HeaderSyncResult.Failed -> {
                    Log.w(TAG, "Falha sync headers com $peer: ${result.reason}")
                    client.disconnect()
                }
            }
        }
        updateProgress(0, isSyncing = true)
        return false
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

    private suspend fun pruneInvalidBlocks(networkTip: Int?) {
        val localTip = blockDao.getTip() ?: return
        val maxHeight = networkTip ?: localTip.height

        if (networkTip != null && localTip.height > networkTip) {
            Log.w(TAG, "Podando blocos acima da rede: local=${localTip.height}, rede=$networkTip")
            blockDao.deleteAbove(networkTip)
        }

        val checkHeight = minOf(blockDao.getTip()?.height ?: 0, maxHeight)
        when (val result = ChainValidator.verifyStoredChain(
            getByHeight = { blockDao.getByHeight(it) },
            maxHeight = checkHeight
        )) {
            is ChainValidator.ValidationResult.Invalid -> {
                Log.e(TAG, "Cadeia inválida: ${result.reason} — resetando")
                resetChain()
            }
            ChainValidator.ValidationResult.Valid -> Unit
        }
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
                                    Log.e(TAG, "Header rejeitado na altura $nextHeight: ${validation.reason}")
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
                            totalDownloaded++

                            if (networkTip != null && nextHeight >= networkTip) {
                                done = true
                                break
                            }
                        }

                        val progress = if (networkTip != null && networkTip > 0) {
                            minOf(50, (tip.height * 50) / networkTip)
                        } else {
                            minOf(50, 5 + totalDownloaded / 5)
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

        val finalTip = blockDao.getTip()?.height ?: 0
        return if (finalTip > 0) {
            HeaderSyncResult.Success
        } else {
            HeaderSyncResult.Failed("Nenhum header válido recebido")
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
                            Log.e(TAG, "Hash do bloco $current não coincide: esperado ${header.hash}, recebido $blockHash")
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
        val blocks = blockDao.count()
        val peersList = connectedPeers.toList()
        _syncProgress.value = SyncProgress(
            height = blockHeight,
            progress = progress.coerceIn(0, 100),
            isSyncing = isSyncing,
            peer = peerHost,
            connectedPeers = peersList,
            blockCount = blocks
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
                blockCount = blocks,
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
