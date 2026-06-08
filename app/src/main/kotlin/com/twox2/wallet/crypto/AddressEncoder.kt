package com.twox2.wallet.crypto

import com.twox2.wallet.chain.ChainParams

object AddressEncoder {
    fun publicKeyToAddress(publicKey: ByteArray): String {
        val hash160 = Hash160.hash(publicKey)
        return Base58.encodeCheck(ChainParams.ADDRESS_VERSION, hash160)
    }

    fun publicKeyToCashAddress(publicKey: ByteArray): String {
        val hash160 = Hash160.hash(publicKey)
        return CashAddr.encode(hash160)
    }

    fun isValidAddress(address: String): Boolean = runCatching {
        if (address.contains(':')) {
            CashAddr.decode(address)
        } else {
            val (version, _) = Base58.decodeCheck(address)
            require(version == ChainParams.ADDRESS_VERSION) { "Versão inválida" }
        }
        true
    }.getOrDefault(false)

    fun addressToScriptPubKey(address: String): ByteArray {
        val hash160 = if (address.contains(':')) {
            CashAddr.decode(address)
        } else {
            val (version, payload) = Base58.decodeCheck(address)
            require(version == ChainParams.ADDRESS_VERSION)
            payload
        }
        return byteArrayOf(0x76, 0xA9.toByte(), 0x14) + hash160 + byteArrayOf(0x88.toByte(), 0xAC.toByte())
    }

    fun privateKeyToWif(privateKey: ByteArray, compressed: Boolean = true): String {
        val payload = if (compressed) {
            byteArrayOf(ChainParams.WIF_VERSION) + privateKey + byteArrayOf(0x01)
        } else {
            byteArrayOf(ChainParams.WIF_VERSION) + privateKey
        }
        val checksum = Sha256.hashTwice(payload).copyOfRange(0, 4)
        return Base58.encode(payload + checksum)
    }
}
