package com.twox2.wallet.crypto

import com.twox2.wallet.chain.ChainParams

object CashAddr {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val PUBKEY_TYPE = 0

    fun encode(hash160: ByteArray): String {
        require(hash160.size == 20) { "Hash160 deve ter 20 bytes" }
        val versionByte = (PUBKEY_TYPE shl 3).toByte()
        val data = byteArrayOf(versionByte) + hash160
        val payload = ConvertBits.convertBits(data, fromBits = 8, toBits = 5, pad = true)
        val checksum = createChecksum(ChainParams.CASHADDR_PREFIX, payload)
        val combined = payload + checksum
        val sb = StringBuilder(ChainParams.CASHADDR_PREFIX).append(':')
        for (b in combined) {
            sb.append(CHARSET[b.toInt() and 0x1F])
        }
        return sb.toString()
    }

    fun decode(address: String): ByteArray {
        val parts = address.lowercase().split(':')
        require(parts.size == 2 && parts[0] == ChainParams.CASHADDR_PREFIX) { "Endereço cashaddr inválido" }
        val values = parts[1].map { c ->
            val idx = CHARSET.indexOf(c)
            require(idx >= 0) { "Caractere cashaddr inválido" }
            idx.toByte()
        }.toByteArray()
        require(verifyChecksum(parts[0], values)) { "Checksum cashaddr inválido" }
        val payload = values.copyOfRange(0, values.size - 8)
        val extrabits = (payload.size * 5) % 8
        require(extrabits < 5) { "Padding cashaddr inválido" }
        if (extrabits > 0) {
            val last = payload.last().toInt() and 0xFF
            val mask = (1 shl extrabits) - 1
            require(last and mask == 0) { "Bits de padding não zero" }
        }
        val data = ConvertBits.convertBits(payload, fromBits = 5, toBits = 8, pad = false)
        require(data.size == 21) { "Tamanho de payload inválido" }
        val version = data[0].toInt() and 0xFF
        require(version and 0x80 == 0) { "Versão reservada" }
        val type = (version shr 3) and 0x1F
        require(type == PUBKEY_TYPE) { "Tipo de endereço não suportado" }
        return data.copyOfRange(1, data.size)
    }

    private fun createChecksum(prefix: String, values: ByteArray): ByteArray {
        val enc = expandPrefix(prefix) + values + ByteArray(8)
        val mod = polymod(enc) xor 1L
        val ret = ByteArray(8)
        for (i in 0 until 8) {
            ret[i] = ((mod shr (5 * (7 - i))) and 31).toByte()
        }
        return ret
    }

    private fun verifyChecksum(prefix: String, values: ByteArray): Boolean {
        return polymod(expandPrefix(prefix) + values) == 1L
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
