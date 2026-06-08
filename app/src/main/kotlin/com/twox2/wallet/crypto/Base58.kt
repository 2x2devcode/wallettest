package com.twox2.wallet.crypto

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

object Base58 {
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        var num = java.math.BigInteger(1, input)
        val sb = StringBuilder()
        while (num > java.math.BigInteger.ZERO) {
            val (quotient, remainder) = num.divideAndRemainder(java.math.BigInteger.valueOf(58))
            sb.append(ALPHABET[remainder.toInt()])
            num = quotient
        }
        input.forEach { if (it == 0.toByte()) sb.append(ALPHABET[0]) else return@forEach }
        return sb.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        var num = java.math.BigInteger.ZERO
        for (c in input) {
            val index = ALPHABET.indexOf(c)
            require(index >= 0) { "Caractere Base58 inválido: $c" }
            num = num.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(index.toLong()))
        }
        val leadingZeros = input.takeWhile { it == ALPHABET[0] }.count()
        val decoded = num.toByteArray()
        val trimmed = if (decoded.size > 1 && decoded[0] == 0.toByte()) decoded.copyOfRange(1, decoded.size) else decoded
        return ByteArray(leadingZeros) { 0 } + trimmed
    }

    fun encodeCheck(version: Byte, payload: ByteArray): String {
        val data = byteArrayOf(version) + payload
        val checksum = Sha256.hashTwice(data).copyOfRange(0, 4)
        return encode(data + checksum)
    }

    fun decodeCheck(input: String): Pair<Byte, ByteArray> {
        val decoded = decode(input)
        require(decoded.size >= 5) { "Endereço inválido" }
        val payload = decoded.copyOfRange(0, decoded.size - 4)
        val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
        val expected = Sha256.hashTwice(payload).copyOfRange(0, 4)
        require(checksum.contentEquals(expected)) { "Checksum inválido" }
        return payload[0] to payload.copyOfRange(1, payload.size)
    }
}
