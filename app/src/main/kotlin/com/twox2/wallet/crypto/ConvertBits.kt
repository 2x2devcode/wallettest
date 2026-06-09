package com.twox2.wallet.crypto

object ConvertBits {
    fun convertBits(
        input: ByteArray,
        fromBits: Int,
        toBits: Int,
        pad: Boolean
    ): ByteArray {
        val maxv = (1 shl toBits) - 1
        val maxAcc = (1 shl (fromBits + toBits - 1)) - 1
        var acc = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (value in input) {
            val byte = value.toInt() and 0xFF
            acc = ((acc shl fromBits) or byte) and maxAcc
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) {
                out.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        } else {
            if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
                error("Bits restantes inválidos na conversão")
            }
        }
        return out.toByteArray()
    }
}
