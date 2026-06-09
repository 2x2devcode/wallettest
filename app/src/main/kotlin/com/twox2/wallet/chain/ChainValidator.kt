package com.twox2.wallet.chain

import android.util.Log
import com.twox2.wallet.data.db.BlockHeaderEntity
import com.twox2.wallet.network.ExplorerApi

object ChainValidator {
    private const val TAG = "TwoX2Chain"
    private const val MAX_HEADERS_BATCH = 2000
    private const val CHAIN_TAIL_VERIFY = 200

    val CHECKPOINTS = mapOf(
        0 to ChainParams.GENESIS_HASH,
        1 to "ddb63b53401b9288b6d6233d97c218e276a6ccb7e1a427ba64b24cb46918eb85",
        50_000 to "6d9db6456cd75858c051f9c29555464ac934b1e352ab3a8674626d2a9ca22d4a",
        110_000 to "c218586782fefd981447794a1b10ebc18c2056f45c0cb79918e903b1b4f285b5"
    )

    val EXPLORER_VERIFY_HEIGHTS = setOf(1, 50_000, 110_000)

    fun isProtocolV2(time: Long): Boolean =
        time > ChainParams.PROTOCOL_V2_TIME && time != ChainParams.PROTOCOL_V2_TIME

    fun validateGenesis(storedHash: String): Boolean {
        val expected = BlockHeader.GENESIS.getHash().toHex()
        val valid = storedHash.equals(expected, ignoreCase = true) &&
            expected.equals(ChainParams.GENESIS_HASH, ignoreCase = true)
        if (!valid) {
            Log.e(TAG, "Genesis inválido. Esperado: $expected, armazenado: $storedHash")
        }
        return valid
    }

    fun recomputeHash(entity: BlockHeaderEntity): String =
        entityToHeader(entity).getHash().toHex()

    fun entityToHeader(entity: BlockHeaderEntity): BlockHeader = BlockHeader(
        version = entity.version,
        prevBlock = UInt256.fromHex(entity.prevHash),
        merkleRoot = UInt256.fromHex(entity.merkleRoot),
        time = entity.time,
        bits = entity.bits,
        nonce = entity.nonce
    )

    /**
     * Validação de header durante sync P2P.
     * Alinhada ao core 2x2Coin: headers não exigem PoW (AcceptBlockHeader com fCheckPOW=false).
     * A confiança na mainnet vem dos checkpoints locais + verificação via explorer.
     */
    fun validateHeaderForSync(
        header: BlockHeader,
        parent: BlockHeaderEntity?,
        nextHeight: Int,
        networkTipHeight: Int,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): ValidationResult {
        if (nextHeight > networkTipHeight) {
            return ValidationResult.Invalid(
                "Altura $nextHeight excede tip da rede ($networkTipHeight)"
            )
        }

        val headerHash = header.getHash().toHex()

        if (parent == null && nextHeight != 0) {
            return ValidationResult.Invalid("Bloco pai ausente na altura $nextHeight")
        }

        if (parent != null) {
            val parentInternal = UInt256.fromHex(parent.hash).bytes
            if (!header.prevBlock.bytes.contentEquals(parentInternal)) {
                return ValidationResult.Invalid("prevBlock inválido na altura $nextHeight")
            }

            val pastTimeLimit = getPastTimeLimit(parent)
            if (header.time <= pastTimeLimit) {
                return ValidationResult.Invalid("Timestamp muito antigo na altura $nextHeight")
            }
        }

        CHECKPOINTS[nextHeight]?.let { expected ->
            if (!headerHash.equals(expected, ignoreCase = true)) {
                return ValidationResult.Invalid("Checkpoint altura $nextHeight inválido")
            }
        }

        if (isProtocolV2(header.time) && header.version < 7) {
            return ValidationResult.Invalid(
                "Versão ${header.version} rejeitada após Protocol V2"
            )
        }

        if (nextHeight > ChainParams.LAST_POW_BLOCK) {
            if ((header.time and ChainParams.STAKE_TIMESTAMP_MASK) != 0L) {
                return ValidationResult.Invalid("Timestamp PoS inválido na altura $nextHeight")
            }
        }

        val maxFuture = if (isProtocolV2(header.time)) {
            ChainParams.FUTURE_DRIFT_V2
        } else {
            ChainParams.FUTURE_DRIFT_V1
        }
        if (header.time > nowSeconds + maxFuture) {
            return ValidationResult.Invalid("Timestamp no futuro na altura $nextHeight")
        }

        return ValidationResult.Valid
    }

  suspend fun verifyExplorerCheckpoint(height: Int, hash: String): Boolean {
        if (height !in EXPLORER_VERIFY_HEIGHTS) return true
        return ExplorerApi.verifyBlockHash(height, hash)
    }

    suspend fun verifyTipAgainstExplorer(height: Int, hash: String): Boolean {
        return ExplorerApi.verifyBlockHash(height, hash)
    }

    /**
     * Encontra o último bloco válido na mainnet via explorer e poda a cadeia acima dele.
     * Retorna a altura válida ou -1 se nem o genesis confere.
     */
    suspend fun findValidForkHeight(
        getByHeight: suspend (Int) -> BlockHeaderEntity?,
        maxHeight: Int
    ): Int {
        for (height in maxHeight downTo 0) {
            val entity = getByHeight(height) ?: continue
            if (ExplorerApi.verifyBlockHash(height, entity.hash)) {
                return height
            }
        }
        return -1
    }

    suspend fun verifyStoredChain(
        getByHeight: suspend (Int) -> BlockHeaderEntity?,
        maxHeight: Int
    ): ValidationResult {
        val genesis = getByHeight(0) ?: return ValidationResult.Invalid("Genesis ausente")
        if (!validateGenesis(genesis.hash)) {
            return ValidationResult.Invalid("Genesis inválido")
        }

        for ((height, expected) in CHECKPOINTS) {
            if (height == 0 || height > maxHeight) continue
            val entity = getByHeight(height) ?: return ValidationResult.Invalid(
                "Checkpoint ausente na altura $height"
            )
            if (!entity.hash.equals(expected, ignoreCase = true)) {
                return ValidationResult.Invalid("Checkpoint $height incorreto")
            }
            val computed = recomputeHash(entity)
            if (!computed.equals(entity.hash, ignoreCase = true)) {
                return ValidationResult.Invalid("Hash incorreto no checkpoint $height")
            }
        }

        val tailStart = maxOf(1, maxHeight - CHAIN_TAIL_VERIFY)
        var prev = getByHeight(tailStart - 1)
        for (height in tailStart..maxHeight) {
            val entity = getByHeight(height) ?: return ValidationResult.Invalid(
                "Bloco ausente na altura $height"
            )
            val computed = recomputeHash(entity)
            if (!computed.equals(entity.hash, ignoreCase = true)) {
                return ValidationResult.Invalid("Hash incorreto na altura $height")
            }
            if (prev != null && !entity.prevHash.equals(prev.hash, ignoreCase = true)) {
                return ValidationResult.Invalid("Cadeia quebrada na altura $height")
            }
            val header = entityToHeader(entity)
            when (val v = validateHeaderForSync(header, prev, height, maxHeight)) {
                is ValidationResult.Invalid -> return v
                ValidationResult.Valid -> Unit
            }
            prev = entity
        }
        return ValidationResult.Valid
    }

    private fun getPastTimeLimit(parent: BlockHeaderEntity): Long {
        return if (isProtocolV2(parent.time)) {
            parent.time
        } else {
            parent.time
        }
    }

    fun shouldStopHeadersBatch(count: Int): Boolean = count == 0

    fun isFullBatch(count: Int): Boolean = count >= MAX_HEADERS_BATCH

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}
