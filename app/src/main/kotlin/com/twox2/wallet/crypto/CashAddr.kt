package com.twox2.wallet.crypto

import com.twox2.wallet.chain.ChainParams

object CashAddr {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun encode(hash160: ByteArray): String {
        val payload = byteArrayOf(0) + hash160
        val checksum = polymod(expandPrefix(ChainParams.CASHADDR_PREFIX) + payload + ByteArray(8)) xor 1L
        val checksumBytes = ByteArray(8)
        for (i in 0 until 8) {
            checksumBytes[i] = ((checksum shr (5 * (7 - i))) and 31).toByte()
        }
        val combined = payload + checksumBytes
        val sb = StringBuilder(ChainParams.CASHADDR_PREFIX).append(':')
        for (b in combined) {
            sb.append(CHARSET[b.toInt() and 0x1F])
        }
        return sb.toString()
    }

    fun decode(address: String): ByteArray {
        val parts = address.lowercase().split(':')
        require(parts.size == 2 && parts[0] == ChainParams.CASHADDR_PREFIX) { "Endereço cashaddr inválido" }
        val data = parts[1].map { c ->
            val idx = CHARSET.indexOf(c)
            require(idx >= 0) { "Caractere cashaddr inválido" }
            idx.toByte()
        }.toByteArray()
        val payload = data.copyOfRange(0, data.size - 8)
        val checksum = polymod(expandPrefix(parts[0]) + payload + ByteArray(8)) xor 1L
        require(checksum == 0L) { "Checksum cashaddr inválido" }
        require(payload[0] == 0.toByte()) { "Tipo de endereço não suportado" }
        return payload.copyOfRange(1, payload.size)
    }

    private fun expandPrefix(prefix: String): ByteArray {
        val result = ByteArray(prefix.length + 1)
        prefix.forEachIndexed { i, c -> result[i] = (c.code and 0x1F).toByte() }
        result[prefix.length] = 0
        return result
    }

    private fun polymod(values: ByteArray): Long {
        var c = 1L
        for (value in values) {
            val c0 = c ushr 35
            c = ((c and 0x07FFFFFFFFL) shl 5) xor value.toLong()
            if ((c0 and 0x01) != 0L) c = c xor 0x98F2BC8E61L
            if ((c0 and 0x02) != 0L) c = c xor 0x79B76D99E2L
            if ((c0 and 0x04) != 0L) c = c xor 0xF33E5FB3C4L
            if ((c0 and 0x08) != 0L) c = c xor 0xAE2EABE2A8L
            if ((c0 and 0x10) != 0L) c = c xor 0x1E4F43E470L
        }
        return c
    }
}
