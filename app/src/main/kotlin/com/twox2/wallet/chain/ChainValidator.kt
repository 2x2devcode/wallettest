package com.twox2.wallet.chain

import android.util.Log
import com.twox2.wallet.data.db.BlockHeaderEntity

object ChainValidator {
    private const val TAG = "TwoX2Chain"
    private const val PROTOCOL_V2_TIME = 1769817601L
    private const val MAX_HEADERS_BATCH = 2000

    val CHECKPOINTS = mapOf(
        0 to ChainParams.GENESIS_HASH,
        1 to "ddb63b53401b9288b6d6233d97c218e276a6ccb7e1a427ba64b24cb46918eb85"
    )

    fun validateGenesis(storedHash: String): Boolean {
        val expected = BlockHeader.GENESIS.getHash().toHex()
        val valid = storedHash.equals(expected, ignoreCase = true) &&
            expected.equals(ChainParams.GENESIS_HASH, ignoreCase = true)
        if (!valid) {
            Log.e(TAG, "Genesis inválido. Esperado: $expected, armazenado: $storedHash")
        }
        return valid
    }

    fun recomputeHash(entity: BlockHeaderEntity): String {
        return entityToHeader(entity).getHash().toHex()
    }

    fun entityToHeader(entity: BlockHeaderEntity): BlockHeader {
        return BlockHeader(
            version = entity.version,
            prevBlock = UInt256.fromHex(entity.prevHash),
            merkleRoot = UInt256.fromHex(entity.merkleRoot),
            time = entity.time,
            bits = entity.bits,
            nonce = entity.nonce
        )
    }

    fun validateHeader(
        header: BlockHeader,
        parentHash: String,
        nextHeight: Int,
        networkTipHeight: Int?
    ): ValidationResult {
        val headerHash = header.getHash().toHex()
        val parentInternal = UInt256.fromHex(parentHash).bytes
        if (!header.prevBlock.bytes.contentEquals(parentInternal)) {
            return ValidationResult.Invalid(
                "prevBlock não coincide com o bloco pai (altura $nextHeight, hash $headerHash)"
            )
        }

        CHECKPOINTS[nextHeight]?.let { expected ->
            if (!headerHash.equals(expected, ignoreCase = true)) {
                return ValidationResult.Invalid(
                    "Checkpoint altura $nextHeight inválido: esperado $expected, obtido $headerHash"
                )
            }
        }

        if (header.time >= PROTOCOL_V2_TIME && header.version < 7) {
            return ValidationResult.Invalid(
                "Versão ${header.version} inválida após Protocol V2 (time=${header.time})"
            )
        }

        networkTipHeight?.let { tip ->
            if (nextHeight > tip) {
                return ValidationResult.Invalid(
                    "Altura $nextHeight excede a altura da rede ($tip)"
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
        var prevHash: String? = null
        for (height in 0..maxHeight) {
            val entity = getByHeight(height) ?: return ValidationResult.Invalid(
                "Bloco ausente na altura $height"
            )

            val computed = recomputeHash(entity)
            if (!computed.equals(entity.hash, ignoreCase = true)) {
                return ValidationResult.Invalid(
                    "Hash incorreto na altura $height: armazenado=${entity.hash}, calculado=$computed"
                )
            }

            CHECKPOINTS[height]?.let { expected ->
                if (!entity.hash.equals(expected, ignoreCase = true)) {
                    return ValidationResult.Invalid(
                        "Checkpoint altura $height não confere com a mainnet"
                    )
                }
            }

            if (prevHash != null && !entity.prevHash.equals(prevHash, ignoreCase = true)) {
                return ValidationResult.Invalid(
                    "Cadeia quebrada na altura $height: prevHash=${entity.prevHash}, esperado=$prevHash"
                )
            }
            if (height == 0 && !validateGenesis(entity.hash)) {
                return ValidationResult.Invalid("Genesis inválido na cadeia armazenada")
            }
            prevHash = entity.hash
        }
        return ValidationResult.Valid
    }

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}
