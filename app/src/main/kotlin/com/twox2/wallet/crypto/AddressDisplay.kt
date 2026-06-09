package com.twox2.wallet.crypto

import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.wallet.WalletInfo

/** Endereço Base58 P2PKH legado (versão 3, prefixo "2"). */
fun WalletInfo.receiveDisplayAddress(): String =
    resolveLegacyAddress(address, cashAddress, publicKey.toHex())

fun SavedAddressEntity.receiveDisplayAddress(): String =
    resolveLegacyAddress(address, cashAddress, publicKeyHex)

fun resolveLegacyAddress(
    address: String,
    cashAddress: String = "",
    publicKeyHex: String? = null
): String {
    if (isLegacyBase58Address(address)) return address

    publicKeyHex?.hexToBytesOrNull()?.let { publicKey ->
        val derived = AddressEncoder.publicKeyToAddress(publicKey)
        if (isLegacyBase58Address(derived)) return derived
    }

    for (candidate in listOf(address, cashAddress)) {
        AddressEncoder.cashAddrToLegacyAddress(candidate)?.let { legacy ->
            if (isLegacyBase58Address(legacy)) return legacy
        }
    }

    return address
        .substringAfter(':', address)
        .takeIf { isLegacyBase58Address(it) }
        ?: ""
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun isLegacyBase58Address(address: String): Boolean {
    if (address.isBlank() || address.contains(':')) return false
    if (!address.startsWith('2')) return false
    return AddressEncoder.isValidAddress(address)
}

private fun String.hexToBytesOrNull(): ByteArray? = runCatching {
    require(length % 2 == 0)
    ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}.getOrNull()
