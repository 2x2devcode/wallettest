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
        var r = normalize32(sig[0].toByteArray())
        var sigS = BigInteger(1, normalize32(sig[1].toByteArray()))
        if (sigS > HALF_CURVE_ORDER) {
            sigS = CURVE_ORDER - sigS
        }
        return r + normalize32(sigS.toByteArray())
    }

    fun verify(publicKey: ByteArray, hash: ByteArray, compactSig: ByteArray): Boolean = runCatching {
        val signer = ECDSASigner()
        val q = domain.curve.decodePoint(publicKey)
        signer.init(false, org.bouncycastle.crypto.params.ECPublicKeyParameters(q, domain))
        val r = BigInteger(1, compactSig.copyOfRange(0, 32))
        val s = BigInteger(1, compactSig.copyOfRange(32, 64))
        signer.verifySignature(hash, r, s)
    }.getOrDefault(false)

    private val CURVE_ORDER = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    )
    private val HALF_CURVE_ORDER = CURVE_ORDER.shiftRight(1)

    fun normalize32(bytes: ByteArray): ByteArray {
        val result = ByteArray(32)
        val src = if (bytes.size > 32) bytes.copyOfRange(bytes.size - 32, bytes.size) else bytes
        System.arraycopy(src, 0, result, 32 - src.size, src.size)
        return result
    }
}
