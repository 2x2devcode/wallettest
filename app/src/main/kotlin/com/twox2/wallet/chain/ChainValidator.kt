package com.twox2.wallet.chain

import android.util.Log
import com.twox2.wallet.data.db.BlockHeaderEntity

object ChainValidator {
    private const val TAG = "TwoX2Chain"
    private const val MAX_HEADERS_BATCH = 2000

    val CHECKPOINTS = mapOf(
        0 to ChainParams.GENESIS_HASH,
        1 to "ddb63b53401b9288b6d6233d97c218e276a6ccb7e1a427ba64b24cb46918eb85"
    )

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

    fun validateHeader(
        header: BlockHeader,
        parent: BlockHeaderEntity?,
        nextHeight: Int,
        networkTipHeight: Int?,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): ValidationResult {
        val headerHash = header.getHash().toHex()

        if (parent == null && nextHeight != 0) {
            return ValidationResult.Invalid("Bloco pai ausente na altura $nextHeight")
        }

        if (parent != null) {
            val parentInternal = UInt256.fromHex(parent.hash).bytes
            if (!header.prevBlock.bytes.contentEquals(parentInternal)) {
                return ValidationResult.Invalid(
                    "prevBlock inválido na altura $nextHeight"
                )
            }

            val pastTimeLimit = if (isProtocolV2(parent.time)) parent.time else parent.time
            if (header.time <= pastTimeLimit) {
                return ValidationResult.Invalid(
                    "Timestamp muito antigo na altura $nextHeight"
                )
            }
        }

        CHECKPOINTS[nextHeight]?.let { expected ->
            if (!headerHash.equals(expected, ignoreCase = true)) {
                return ValidationResult.Invalid(
                    "Checkpoint altura $nextHeight inválido"
                )
            }
        }

        // CheckBlockHeader: Protocol V2 exige nVersion >= 7
        if (isProtocolV2(header.time) && header.version < 7) {
            return ValidationResult.Invalid(
                "Versão ${header.version} rejeitada após Protocol V2"
            )
        }

        // ContextualCheckBlockHeader: PoS timestamp mask
        if (nextHeight > ChainParams.LAST_POW_BLOCK) {
            if ((header.time and ChainParams.STAKE_TIMESTAMP_MASK) != 0L) {
                return ValidationResult.Invalid(
                    "Timestamp PoS inválido na altura $nextHeight"
                )
            }
        }

        // FutureDrift
        val maxFuture = if (isProtocolV2(header.time)) {
            ChainParams.FUTURE_DRIFT_V2
        } else {
            ChainParams.FUTURE_DRIFT_V1
        }
        if (header.time > nowSeconds + maxFuture) {
            return ValidationResult.Invalid(
                "Timestamp no futuro na altura $nextHeight"
            )
        }

        // PoW check para blocos PoW (core usa fCheckPOW em blocos completos; aplicamos em headers <= 110k)
        if (nextHeight in 1..ChainParams.LAST_POW_BLOCK) {
            if (!PowValidator.checkProofOfWork(header)) {
                return ValidationResult.Invalid(
                    "Proof-of-Work falhou na altura $nextHeight"
                )
            }
        }

        networkTipHeight?.let { tip ->
            if (nextHeight > tip) {
                return ValidationResult.Invalid(
                    "Altura $nextHeight excede tip da rede ($tip)"
                )
            }
        }

        return ValidationResult.Valid
    }

    fun shouldStopHeadersBatch(count: Int): Boolean = count == 0

    fun isFullBatch(count: Int): Boolean = count >= MAX_HEADERS_BATCH

    suspend fun verifyStoredChain(
        getByHeight: suspend (Int) -> BlockHeaderEntity?,
        maxHeight: Int
    ): ValidationResult {
        var prev: BlockHeaderEntity? = null
        for (height in 0..maxHeight) {
            val entity = getByHeight(height) ?: return ValidationResult.Invalid(
                "Bloco ausente na altura $height"
            )

            val computed = recomputeHash(entity)
            if (!computed.equals(entity.hash, ignoreCase = true)) {
                return ValidationResult.Invalid("Hash incorreto na altura $height")
            }

            CHECKPOINTS[height]?.let { expected ->
                if (!entity.hash.equals(expected, ignoreCase = true)) {
                    return ValidationResult.Invalid("Checkpoint $height incorreto")
                }
            }

            if (prev != null && !entity.prevHash.equals(prev.hash, ignoreCase = true)) {
                return ValidationResult.Invalid("Cadeia quebrada na altura $height")
            }

            val header = entityToHeader(entity)
            when (val v = validateHeader(header, prev, height, null)) {
                is ValidationResult.Invalid -> return v
                ValidationResult.Valid -> Unit
            }

            if (height == 0 && !validateGenesis(entity.hash)) {
                return ValidationResult.Invalid("Genesis inválido")
            }
            prev = entity
        }
        return ValidationResult.Valid
    }

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}
