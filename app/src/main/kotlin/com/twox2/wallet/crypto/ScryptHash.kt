package com.twox2.wallet.crypto

import com.lambdaworks.crypto.SCrypt

object ScryptHash {
    fun hashBlockHeader(headerBytes: ByteArray): ByteArray {
        return SCrypt.scrypt(headerBytes, headerBytes, 1024, 1, 1, 32)
    }
}
