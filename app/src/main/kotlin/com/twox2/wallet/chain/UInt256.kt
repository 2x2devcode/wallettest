package com.twox2.wallet.chain

import com.twox2.wallet.serialization.BitcoinOutput
import java.math.BigInteger

data class UInt256(val bytes: ByteArray) {
    companion object {
        val ZERO = UInt256(ByteArray(32))

        fun fromHex(hex: String): UInt256 {
            val clean = hex.removePrefix("0x")
            val padded = clean.padStart(64, '0')
            val bytes = ByteArray(32)
            for (i in 0 until 32) {
                bytes[31 - i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return UInt256(bytes)
        }

        fun fromReversedHex(hex: String): UInt256 {
            val clean = hex.removePrefix("0x")
            val bytes = ByteArray(32)
            for (i in 0 until minOf(32, clean.length / 2)) {
                bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return UInt256(bytes)
        }
    }

    fun toHex(): String = bytes.reversedArray().joinToString("") { "%02x".format(it) }

    fun toInternalHex(): String = bytes.joinToString("") { "%02x".format(it) }

    fun serialize(output: BitcoinOutput) = output.writeUInt256(bytes)

    fun compareTo(other: UInt256): Int {
        val a = BigInteger(1, bytes.reversedArray())
        val b = BigInteger(1, other.bytes.reversedArray())
        return a.compareTo(b)
    }

    override fun equals(other: Any?): Boolean =
        other is UInt256 && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}
