package com.twox2.wallet.crypto

import java.security.MessageDigest

object Sha256 {
    private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

    fun hash(data: ByteArray): ByteArray {
        val md = digest.get()!!
        md.reset()
        return md.digest(data)
    }

    fun hashTwice(data: ByteArray): ByteArray = hash(hash(data))

    fun hashTwice(parts: List<ByteArray>): ByteArray {
        val md = digest.get()!!
        md.reset()
        parts.forEach { md.update(it) }
        return hash(md.digest())
    }
}
