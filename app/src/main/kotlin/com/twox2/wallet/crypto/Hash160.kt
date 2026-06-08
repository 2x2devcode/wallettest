package com.twox2.wallet.crypto

import org.bouncycastle.crypto.digests.RIPEMD160Digest

object Hash160 {
    fun hash(data: ByteArray): ByteArray {
        val sha = Sha256.hash(data)
        val ripemd = RIPEMD160Digest()
        ripemd.update(sha, 0, sha.size)
        val out = ByteArray(ripemd.digestSize)
        ripemd.doFinal(out, 0)
        return out
    }
}
