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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockchainSyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val syncPrefs = appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val walletManager = WalletManager.get(context)
    private val db = walletManager.getDatabase()
    private val blockDao = db.blockHeaderDao()
    private val utxoDao = db.utxoDao()
    private val txDao = db.walletTransactionDao()
    private val syncDao = db.syncStateDao()
    private val blockSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress

    @Volatile
    private var running = false

    @Volatile
    private var synced = false

    @Volatile
    private var blockDownloadRunning = false

    private var followUpJob: Job? = null

    private val connectedPeers = linkedSetOf<String>()

    suspend fun startSync() = withContext(Dispatchers.IO) {
        startFollowUpSync()
        if (running) return@withContext
        restoreSyncedStateIfNeeded()
        if (synced) {
            syncNewHeadersIfNeeded()
            return@withContext
        }
        executeSyncLoop()
    }

    fun stopSync() {
        running = false
        followUpJob?.cancel()
        followUpJob = null
    }

    suspend fun verifySync() = withContext(Dispatchers.IO) {
        followUpTick()
    }

    private fun startFollowUpSync() {
        if (followUpJob?.isActive == true) return
        followUpJob = blockSyncScope.launch {
            while (isActive) {
                if (!running) {
                    runCatching { followUpTick() }
                        .onFailure { Log.w(TAG, "Follow-up sync falhou", it) }
                }
                delay(FOLLOW_UP_INTERVAL_MS)
            }
        }
    }

    private suspend fun followUpTick() {
        ExplorerWalletSync.sync(appContext)
        syncNewHeadersIfNeeded()
        scanWalletBlocksIfNeeded()
        refreshNetworkHeight()
    }

    private suspend fun refreshNetworkHeight() {
        val networkTip = ExplorerApi.getBlockCountWithRetry() ?: return
        val localTip = blockDao.getTip() ?: return
        if (localTip.height >= networkTip && isSyncedWithMainnet(localTip, networkTip)) {
            synced = true
            updateProgress(100, isSyncing = false, height = localTip.height)
            return
        }
        if (localTip.height < networkTip) {
            _syncProgress.value = _syncProgress.value.copy(
                height = localTip.height,
                blockCount = localTip.height,
                networkHeight = networkTip
            )
        }
    }

    private suspend fun syncNewHeadersIfNeeded() {
        if (running) return
        val networkTip = ExplorerApi.getBlockCountWithRetry() ?: return
        pruneAbove(networkTip)

        val localTip = blockDao.getTip() ?: return
        if (localTip.height < networkTip) {
            val inserted = ExplorerHeaderSync.syncToHeight(blockDao, networkTip)
            val updatedTip = blockDao.getTip()
            if (inserted > 0 && updatedTip != null) {
                updateProgress(
                    progress = minOf(100, (updatedTip.height * 100) / networkTip.coerceAtLeast(1)),
                    isSyncing = inserted < (networkTip - localTip.height),
                    height = updatedTip.height
                )
                if (updatedTip.height >= networkTip &&
                    isSyncedWithMainnet(updatedTip, networkTip)
                ) {
                    completeHeaderSync(updatedTip, networkTip)
                    return
                }
                if (updatedTip.height >= networkTip) {
                    completeHeaderSync(updatedTip, networkTip)
                    return
                }
            }
            if (!repairChainToExplorer(networkTip)) return
            Log.i(TAG, "Novos blocos via P2P: local=${localTip.height}, rede=$networkTip")
            executeSyncLoop()
            return
        }

        if (isSyncedWithMainnet(localTip, networkTip)) {
            synced = true
            updateProgress(100, isSyncing = false, height = localTip.height)
            return
        }

        if (!repairChainToExplorer(networkTip)) return
        Log.i(TAG, "Reparando cadeia: local=${localTip.height}, rede=$networkTip")
        executeSyncLoop()
    }

    private suspend fun scanWalletBlocksIfNeeded() {
        val networkTip = ExplorerApi.getBlockCountWithRetry() ?: return
        val maxHeight = minOf(blockDao.getTip()?.height ?: 0, networkTip)
        if (maxHeight <= 0) return
        val lastScanned = syncPrefs.getInt(KEY_LAST_SCANNED_HEIGHT, 0)
        if (lastScanned < maxHeight) {
            startBlockDownloadIfNeeded(networkTip)
        }
    }

    private suspend fun executeSyncLoop() {
        if (running) return
        running = true
        try {
            runSyncLoop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronização", e)
            updateProgress(0, isSyncing = true)
        } finally {
            running = false
        }
    }

    private suspend fun restoreSyncedStateIfNeeded() {
        val networkTip = ExplorerApi.getBlockCountWithRetry() ?: return
        val localTip = blockDao.getTip() ?: return
        if (isSyncedWithMainnet(localTip, networkTip)) {
            synced = true
            updateProgress(100, isSyncing = false, height = localTip.height)
        }
    }

    private suspend fun runSyncLoop() {
        updateProgress(0, isSyncing = true)
        synced = false

        if (!ensureGenesis()) {
            resetChain()
            ensureGenesis()
        }

        while (running) {
            val networkTip = ExplorerApi.getBlockCountWithRetry() ?: run {
                Log.w(TAG, "Explorer indisponível — aguardando")
                updateProgress(0, isSyncing = true)
                return
            }

            pruneAbove(networkTip)
            repairChainToExplorer(networkTip)

            val localTip = blockDao.getTip()
            if (localTip != null && isSyncedWithMainnet(localTip, networkTip)) {
                completeHeaderSync(localTip, networkTip)
                return
            }

            val peers = PeerDiscovery.discoverPeers()
            if (peers.isEmpty()) {
                updateProgress(0, isSyncing = true)
                return
            }

            var madeProgress = false
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
                        madeProgress = true
                        val tip = blockDao.getTip()
                        val refreshedTip = ExplorerApi.getBlockCount() ?: networkTip
                        pruneAbove(refreshedTip)

                        if (tip != null && isSyncedWithMainnet(tip, refreshedTip)) {
                            client.disconnect()
                            completeHeaderSync(tip, refreshedTip)
                            return
                        }

                        if (tip != null && tip.height >= refreshedTip) {
                            val repaired = repairChainToExplorer(refreshedTip)
                            val repairedTip = blockDao.getTip()
                            if (repaired && repairedTip != null &&
                                isSyncedWithMainnet(repairedTip, refreshedTip)
                            ) {
                                client.disconnect()
                                completeHeaderSync(repairedTip, refreshedTip)
                                return
                            }
                        }
                    }
                    is HeaderSyncResult.Failed -> {
                        Log.w(TAG, "Falha sync com $peer: ${result.reason}")
                    }
                }
                client.disconnect()
            }

            val currentTip = blockDao.getTip()
            val refreshedTip = ExplorerApi.getBlockCount() ?: return
            if (currentTip != null && isSyncedWithMainnet(currentTip, refreshedTip)) {
                completeHeaderSync(currentTip, refreshedTip)
                return
            }

            if (!madeProgress) {
                Log.w(TAG, "Nenhum progresso com peers disponíveis na altura ${currentTip?.height ?: 0}")
                updateProgress(
                    minOf(49, ((currentTip?.height ?: 0) * 50) / refreshedTip.coerceAtLeast(1)),
                    isSyncing = true,
                    height = currentTip?.height
                )
                return
            }
        }
    }

    private suspend fun completeHeaderSync(tip: BlockHeaderEntity, networkTip: Int) {
        synced = true
        updateProgress(100, isSyncing = false, height = tip.height)
        startBlockDownloadIfNeeded(networkTip)
        startFollowUpSync()
    }

    private fun startBlockDownloadIfNeeded(networkTip: Int) {
        if (blockDownloadRunning) return
        blockSyncScope.launch {
            blockDownloadRunning = true
            try {
                downloadBlocks(networkTip)
                val refreshedTip = ExplorerApi.getBlockCount() ?: networkTip
                if (syncPrefs.getInt(KEY_LAST_SCANNED_HEIGHT, 0) < minOf(
                        blockDao.getTip()?.height ?: 0,
                        refreshedTip
                    )
                ) {
                    downloadBlocks(refreshedTip)
                }
            } finally {
                blockDownloadRunning = false
            }
        }
    }

    private suspend fun isSyncedWithMainnet(tip: BlockHeaderEntity, networkTip: Int): Boolean {
        if (tip.height != networkTip) return false
        return ChainValidator.verifyTipAgainstExplorer(tip.height, tip.hash)
    }

    private suspend fun pruneAbove(networkTip: Int) {
        val localTip = blockDao.getTip() ?: return
        if (localTip.height > networkTip) {
            Log.w(TAG, "Podando altura ${localTip.height} > rede $networkTip")
            blockDao.deleteAbove(networkTip)
        }
    }

    /**
     * Caminha da ponta até o genesis procurando o último bloco que o explorer confirma.
     * Podará forks inválidos sem apagar toda a cadeia.
     */
    private suspend fun repairChainToExplorer(networkTip: Int): Boolean {
        val tip = blockDao.getTip() ?: return true
        val checkHeight = minOf(tip.height, networkTip)
        if (checkHeight <= 0) return true

        when (val result = ChainValidator.verifyStoredChain(
            getByHeight = { blockDao.getByHeight(it) },
            maxHeight = checkHeight
        )) {
            ChainValidator.ValidationResult.Valid -> return true
            is ChainValidator.ValidationResult.Invalid -> {
                Log.w(TAG, "Cadeia local inválida: ${result.reason}")
            }
        }

        val validHeight = ChainValidator.findValidForkHeight(
            getByHeight = { blockDao.getByHeight(it) },
            maxHeight = checkHeight
        )

        return when {
            validHeight < 0 -> {
                Log.e(TAG, "Nenhum bloco confere com explorer — resetando")
                resetChain()
                ensureGenesis()
                false
            }
            validHeight < checkHeight -> {
                Log.w(TAG, "Podando fork inválido acima da altura $validHeight")
                blockDao.deleteAbove(validHeight)
                true
            }
            else -> true
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
        resetLastScannedHeight()
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
            return if (ChainValidator.verifyTipAgainstExplorer(tip.height, tip.hash)) {
                HeaderSyncResult.AlreadySynced(tip.height)
            } else {
                HeaderSyncResult.Failed("Tip local não confere com mainnet")
            }
        }

        val stopHashHex = ExplorerApi.getBlockHash(networkTip)
        if (stopHashHex.isNullOrBlank()) {
            return HeaderSyncResult.Failed("Não foi possível obter hash do bloco $networkTip")
        }
        val hashStop = UInt256.fromHex(stopHashHex)

        var locator = listOf(UInt256.fromHex(tip.hash))
        var batchesWithoutProgress = 0
        var insertedTotal = 0

        while (running) {
            networkTip = ExplorerApi.getBlockCount() ?: networkTip
            if (tip.height >= networkTip) break

            client.requestHeaders(locator, hashStop)
            var receivedHeaders = false

            val deadline = System.currentTimeMillis() + 30_000
            while (running && System.currentTimeMillis() < deadline) {
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

                            when (val validation = ChainValidator.validateHeaderForSync(
                                header, parent, nextHeight, networkTip
                            )) {
                                is ChainValidator.ValidationResult.Invalid -> {
                                    Log.e(TAG, "Header $nextHeight rejeitado: ${validation.reason}")
                                    return HeaderSyncResult.Failed(validation.reason)
                                }
                                ChainValidator.ValidationResult.Valid -> Unit
                            }

                            val headerHash = header.getHash().toHex()
                            if (!ChainValidator.verifyExplorerCheckpoint(nextHeight, headerHash)) {
                                Log.e(TAG, "Checkpoint explorer falhou na altura $nextHeight")
                                return HeaderSyncResult.Failed("Fork detectado na altura $nextHeight")
                            }

                            val entity = BlockHeaderEntity(
                                height = nextHeight,
                                hash = headerHash,
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
                            insertedTotal++
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

                        if (tip.height >= networkTip) {
                            return if (ChainValidator.verifyTipAgainstExplorer(tip.height, tip.hash)) {
                                HeaderSyncResult.AlreadySynced(tip.height)
                            } else {
                                HeaderSyncResult.Failed("Hash do tip não confere com explorer")
                            }
                        }

                        if (!ChainValidator.isFullBatch(headers.size)) {
                            return HeaderSyncResult.AlreadySynced(tip.height)
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
            finalTip >= networkTip && blockDao.getTip()?.let {
                ChainValidator.verifyTipAgainstExplorer(it.height, it.hash)
            } == true -> HeaderSyncResult.AlreadySynced(finalTip)
            insertedTotal > 0 -> HeaderSyncResult.Success
            finalTip > 0 -> HeaderSyncResult.AlreadySynced(finalTip)
            else -> HeaderSyncResult.Failed("Nenhum header válido")
        }
    }

    private suspend fun downloadBlocks(networkTip: Int) {
        val watchScripts = walletManager.getAllReceiveScriptPubKeys()
            .map { it.lowercase() }
            .toSet()
        if (watchScripts.isEmpty()) return

        val maxHeight = minOf(blockDao.getTip()?.height ?: 0, networkTip)
        val lastScanned = syncPrefs.getInt(KEY_LAST_SCANNED_HEIGHT, 0)
        val startHeight = (lastScanned + 1).coerceAtLeast(1)
        if (startHeight > maxHeight) return

        val peers = PeerDiscovery.discoverPeers(2)
        if (peers.isEmpty()) return

        val client = P2PClient(peers.first())
        if (!client.connect(maxHeight) || !client.handshake()) {
            client.disconnect()
            return
        }

        try {
            var current = startHeight
            while (current <= maxHeight && currentCoroutineContext().isActive) {
                val header = blockDao.getByHeight(current) ?: break
                client.requestBlocks(listOf(UInt256.fromHex(header.hash)))

                val deadline = System.currentTimeMillis() + 20_000
                while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
                    val msg = client.readMessage() ?: break
                    when (msg.command) {
                        "block" -> {
                            val block = Block.deserialize(msg.payload)
                            if (block.header.getHash().toHex()
                                    .equals(header.hash, ignoreCase = true)
                            ) {
                                processBlock(block, current, watchScripts)
                            }
                            break
                        }
                        "ping" -> client.sendMessage("pong", msg.payload)
                        "inv", "headers" -> Unit
                    }
                }
                syncPrefs.edit().putInt(KEY_LAST_SCANNED_HEIGHT, current).apply()
                current++
            }
        } finally {
            client.disconnect()
        }
    }

    private fun resetLastScannedHeight() {
        syncPrefs.edit().remove(KEY_LAST_SCANNED_HEIGHT).apply()
    }

    private suspend fun processBlock(block: Block, height: Int, watchScripts: Set<String>) {
        block.transactions.forEach { tx ->
            if (tx.isCoinBase()) return@forEach
            val txHash = tx.getHash().toHex()
            tx.outputs.forEachIndexed { index, output ->
                val scriptHex = output.scriptPubKey.toHex().lowercase()
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
            isSynced = synced,
            peer = peerHost,
            connectedPeers = peersList,
            blockCount = blockHeight,
            networkHeight = maxOf(_syncProgress.value.networkHeight, blockHeight)
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
        private const val FOLLOW_UP_INTERVAL_MS = 15_000L
        private const val KEY_LAST_SCANNED_HEIGHT = "last_scanned_block_height"
    }
}

data class SyncProgress(
    val height: Int = 0,
    val progress: Int = 0,
    val isSyncing: Boolean = false,
    val isSynced: Boolean = false,
    val peer: String? = null,
    val connectedPeers: List<String> = emptyList(),
    val blockCount: Int = 0,
    val networkHeight: Int = 0
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
