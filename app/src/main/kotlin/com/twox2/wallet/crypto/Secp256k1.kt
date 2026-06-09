package com.twox2.wallet.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.SecureRandom

object Secp256k1 {
    private val curveParams = SECNamedCurves.getByName("secp256k1")
    val domain = ECDomainParameters(
        curveParams.curve,
        curveParams.g,
        curveParams.n,
        curveParams.h
    )

    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val generator = ECKeyPairGenerator()
        generator.init(ECKeyGenerationParameters(domain, SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val privateKey = normalize32((keyPair.private as ECPrivateKeyParameters).d.toByteArray())
        val publicKey = publicKeyFromPrivate(privateKey)
        return privateKey to publicKey
    }

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        val s = BigInteger(1, normalize32(privateKey))
        val q: ECPoint = domain.g.multiply(s).normalize()
        val x = normalize32(q.affineXCoord.toBigInteger().toByteArray())
        val y = q.affineYCoord.toBigInteger()
        val prefix = if (y.testBit(0)) 0x03 else 0x02
        return byteArrayOf(prefix.toByte()) + x
    }

    fun sign(privateKey: ByteArray, hash: ByteArray): ByteArray {
        val signer = ECDSASigner()
        val s = BigInteger(1, normalize32(privateKey))
        signer.init(true, ECPrivateKeyParameters(s, domain))
        val sig = signer.generateSignature(hash)
        val r = normalize32(sig[0].toByteArray())
        val sigS = normalize32(sig[1].toByteArray())
        return r + sigS
    }

    fun normalize32(bytes: ByteArray): ByteArray {
        val result = ByteArray(32)
        val src = if (bytes.size > 32) bytes.copyOfRange(bytes.size - 32, bytes.size) else bytes
        System.arraycopy(src, 0, result, 32 - src.size, src.size)
        return result
    }
}
