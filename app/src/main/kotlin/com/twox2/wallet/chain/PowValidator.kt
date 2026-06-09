package com.twox2.wallet.chain

import java.math.BigInteger

object PowValidator {
    private val POW_LIMIT = BigInteger(
        "00000fffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16
    )

    fun getPoWHash(header: BlockHeader): UInt256 {
        return UInt256(ScryptHashCompat.hash(header.serialize()))
    }

    fun checkProofOfWork(header: BlockHeader): Boolean {
        val target = compactToTarget(header.bits) ?: return false
        if (target > POW_LIMIT) return false
        val hash = getPoWHash(header)
        val hashInt = BigInteger(1, hash.bytes.reversedArray())
        return hashInt <= target
    }

    fun compactToTarget(bits: Long): BigInteger? {
        val compact = bits.toInt()
        val size = compact ushr 24
        val word = compact and 0x007fffff
        if (word == 0) return null
        val target = when {
            size <= 3 -> BigInteger.valueOf(word.toLong()).shiftRight(8 * (3 - size))
            size > 34 -> return null
            else -> BigInteger.valueOf(word.toLong()).shiftLeft(8 * (size - 3))
        }
        return if (target <= BigInteger.ZERO) null else target
    }
}

// Avoid circular import - inline scrypt call
private object ScryptHashCompat {
    fun hash(headerBytes: ByteArray): ByteArray {
        return com.twox2.wallet.crypto.ScryptHash.hashBlockHeader(headerBytes)
    }
}
