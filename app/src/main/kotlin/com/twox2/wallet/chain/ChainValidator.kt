package com.twox2.wallet.chain

import android.util.Log

object ChainValidator {
    private const val TAG = "TwoX2Chain"
    private const val PROTOCOL_V2_TIME = 1769817601L
    private const val MAX_HEADERS_BATCH = 2000

    fun validateGenesis(storedHash: String): Boolean {
        val expected = BlockHeader.GENESIS.getHash().toHex()
        val valid = storedHash.equals(expected, ignoreCase = true) &&
            expected.equals(ChainParams.GENESIS_HASH, ignoreCase = true)
        if (!valid) {
            Log.e(TAG, "Genesis inválido. Esperado: $expected, armazenado: $storedHash")
        }
        return valid
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

        if (nextHeight == 0) {
            if (!headerHash.equals(ChainParams.GENESIS_HASH, ignoreCase = true)) {
                return ValidationResult.Invalid("Hash do genesis incorreto: $headerHash")
            }
            return ValidationResult.Valid
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
        getByHeight: suspend (Int) -> com.twox2.wallet.data.db.BlockHeaderEntity?,
        maxHeight: Int
    ): ValidationResult {
        var prevHash: String? = null
        for (height in 0..maxHeight) {
            val entity = getByHeight(height) ?: return ValidationResult.Invalid(
                "Bloco ausente na altura $height"
            )
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
