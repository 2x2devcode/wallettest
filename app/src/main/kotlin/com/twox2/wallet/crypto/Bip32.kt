package com.twox2.wallet.crypto

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.math.BigInteger
import java.security.SecureRandom

data class ExtKey(
    val privateKey: ByteArray,
    val chainCode: ByteArray,
    val depth: Int = 0,
    val childNumber: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtKey) return false
        return privateKey.contentEquals(other.privateKey) &&
            chainCode.contentEquals(other.chainCode) &&
            depth == other.depth &&
            childNumber == other.childNumber
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + depth
        result = 31 * result + childNumber
        return result
    }
}

object Bip32 {
    const val HARDENED: Int = 1 shl 31
    private val curveOrder = Secp256k1.domain.n

    fun generateSeed(): ByteArray {
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)
        return seed
    }

    fun masterFromSeed(seed: ByteArray): ExtKey {
        val mac = HMac(SHA512Digest())
        mac.init(KeyParameter("Bitcoin seed".toByteArray(Charsets.UTF_8)))
        val i = ByteArray(64)
        mac.update(seed, 0, seed.size)
        mac.doFinal(i, 0)
        val privateKey = Secp256k1.normalize32(i.copyOfRange(0, 32))
        val chainCode = i.copyOfRange(32, 64)
        require(BigInteger(1, privateKey) < curveOrder) { "Chave mestra inválida" }
        return ExtKey(privateKey, chainCode)
    }

    fun derive(parent: ExtKey, index: Int): ExtKey {
        val mac = HMac(SHA512Digest())
        mac.init(KeyParameter(parent.chainCode))
        val data = ByteArray(37)
        if (index and HARDENED != 0) {
            data[0] = 0
            System.arraycopy(parent.privateKey, 0, data, 1, 32)
        } else {
            val pub = Secp256k1.publicKeyFromPrivate(parent.privateKey)
            System.arraycopy(pub, 0, data, 0, 33)
        }
        data[33] = (index ushr 24).toByte()
        data[34] = (index ushr 16).toByte()
        data[35] = (index ushr 8).toByte()
        data[36] = index.toByte()
        val i = ByteArray(64)
        mac.update(data, 0, 37)
        mac.doFinal(i, 0)
        val il = BigInteger(1, i.copyOfRange(0, 32))
        val childKey = Secp256k1.normalize32(
            il.add(BigInteger(1, parent.privateKey)).mod(curveOrder).toByteArray()
        )
        return ExtKey(
            privateKey = childKey,
            chainCode = i.copyOfRange(32, 64),
            depth = parent.depth + 1,
            childNumber = index
        )
    }

    /**
     * Deriva chave de recebimento conforme carteira oficial 2x2Coin: m/0'/0'/index'
     */
    fun deriveReceiveKey(masterSeed: ByteArray, index: Int): ExtKey {
        require(index >= 0) { "Índice HD inválido" }
        val master = masterFromSeed(masterSeed)
        val account = derive(master, HARDENED)
        val external = derive(account, HARDENED)
        return derive(external, HARDENED or index)
    }
}
