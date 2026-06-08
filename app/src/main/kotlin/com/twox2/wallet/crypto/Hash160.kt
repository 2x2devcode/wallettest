package com.twox2.wallet.crypto

import java.security.MessageDigest

object Hash160 {
    fun hash(data: ByteArray): ByteArray {
        val sha = Sha256.hash(data)
        val ripemd = MessageDigest.getInstance("RIPEMD160")
        return ripemd.digest(sha)
    }
}
