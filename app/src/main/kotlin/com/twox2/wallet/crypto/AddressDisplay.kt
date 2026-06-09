package com.twox2.wallet.crypto

import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.wallet.WalletInfo

/** Endereço Base58 P2PKH (versão 3, prefixo "2") para exibição e QR. */
fun WalletInfo.receiveDisplayAddress(): String = address

fun SavedAddressEntity.receiveDisplayAddress(): String =
    address.ifBlank { cashAddress }
