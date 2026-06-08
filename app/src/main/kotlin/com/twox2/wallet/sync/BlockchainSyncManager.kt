package com.twox2.wallet.sync

import android.content.Context
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

    suspend fun startSync() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        try {
            updateSyncState(isSyncing = true, progress = 0)
            ensureGenesis()

            val peers = PeerDiscovery.discoverPeers()
            if (peers.isEmpty()) {
                _syncProgress.value = SyncProgress(error = "Nenhum peer encontrado")
                return@withContext
            }

            for (peer in peers) {
                if (!running) break
                val client = P2PClient(peer)
                if (!client.connect()) continue
                if (!client.handshake()) {
                    client.disconnect()
                    continue
                }

                _syncProgress.value = _syncProgress.value.copy(peer = peer, status = "Conectado a $peer")
                updateSyncState(isSyncing = true, peerHost = peer)

                if (syncHeaders(client)) {
                    syncBlocks(client)
                    client.disconnect()
                    break
                }
                client.disconnect()
            }

            updateSyncState(isSyncing = false, progress = 100)
            _syncProgress.value = _syncProgress.value.copy(
                status = "Sincronização concluída",
                progress = 100,
                isSyncing = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _syncProgress.value = _syncProgress.value.copy(error = e.message, isSyncing = false)
            updateSyncState(isSyncing = false, progress = _syncProgress.value.progress)
        } finally {
            running = false
        }
    }

    fun stopSync() {
        running = false
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

        repeat(500) {
            if (!running) return false
            client.requestHeaders(locator)
            repeat(20) {
                val msg = client.readMessage() ?: return@repeat
                when (msg.command) {
                    "headers" -> {
                        val headers = P2PMessage.parseHeadersPayload(msg.payload)
                        if (headers.isEmpty()) return true
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
                        val progress = minOf(50, totalDownloaded / 10)
                        _syncProgress.value = _syncProgress.value.copy(
                            height = tip.height,
                            progress = progress,
                            status = "Baixando cabeçalhos: bloco ${tip.height}"
                        )
                        updateSyncState(isSyncing = true, progress = progress)
                        locator = listOf(UInt256.fromHex(tip.hash))
                        return@repeat
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                }
            }
            delay(100)
        }
        return true
    }

    private suspend fun syncBlocks(client: P2PClient) {
        val watchScript = walletManager.getReceiveScriptPubKey().toHex()
        val tip = blockDao.getTip() ?: return
        val batchSize = 20
        var current = maxOf(0, tip.height - 200)

        while (current <= tip.height && running) {
            val header = blockDao.getByHeight(current) ?: break
            client.requestBlocks(listOf(UInt256.fromHex(header.hash)))

            repeat(30) {
                val msg = client.readMessage() ?: return@repeat
                when (msg.command) {
                    "block" -> {
                        val block = Block.deserialize(msg.payload)
                        processBlock(block, current, watchScript)
                    }
                    "ping" -> client.sendMessage("pong", msg.payload)
                }
            }

            val progress = 50 + ((current.toFloat() / tip.height) * 50).toInt()
            _syncProgress.value = _syncProgress.value.copy(
                height = current,
                progress = progress,
                status = "Baixando blocos: $current / ${tip.height}"
            )
            updateSyncState(isSyncing = true, progress = progress)
            current += batchSize
            delay(50)
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
                            type = "deposit",
                            address = "",
                            confirmations = 0
                        )
                    )
                }
            }

            tx.inputs.forEach { input ->
                val prevHash = input.prevTxHash.toHex()
                utxoDao.markSpent(prevHash, input.prevIndex.toInt())
            }
        }
    }

    private suspend fun updateSyncState(isSyncing: Boolean, progress: Int = 0, peerHost: String? = null) {
        val tip = blockDao.getTip()
        syncDao.insert(
            SyncStateEntity(
                bestHeight = tip?.height ?: 0,
                bestHash = tip?.hash ?: ChainParams.GENESIS_HASH,
                isSyncing = isSyncing,
                progress = progress,
                peerHost = peerHost
            )
        )
    }
}

data class SyncProgress(
    val height: Int = 0,
    val progress: Int = 0,
    val status: String = "Aguardando",
    val peer: String? = null,
    val isSyncing: Boolean = false,
    val error: String? = null
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
