package com.twox2.wallet.wallet

import com.twox2.wallet.chain.Transaction
import com.twox2.wallet.chain.TxIn
import com.twox2.wallet.chain.TxOut
import com.twox2.wallet.chain.UInt256
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.crypto.Hash160
import com.twox2.wallet.crypto.Secp256k1
import com.twox2.wallet.crypto.Sha256
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.serialization.BitcoinOutput
import java.math.BigInteger

object TransactionBuilder {
    private const val DUST = 546L

    data class SigningKey(val privateKey: ByteArray, val publicKey: ByteArray)

    fun buildAndSign(
        utxos: List<UtxoEntity>,
        utxoKeys: Map<Long, SigningKey>,
        toAddress: String,
        amount: Long,
        changeAddress: String,
        feePerByte: Long = 10L
    ): Transaction {
        require(AddressEncoder.isValidAddress(toAddress)) { "Endereço de destino inválido" }
        require(amount > DUST) { "Valor abaixo do mínimo" }

        val selected = selectUtxos(utxos, amount, feePerByte)
        val totalInput = selected.sumOf { it.value }
        val estimatedSize = 10 + selected.size * 148 + 2 * 34
        val fee = estimatedSize * feePerByte
        require(totalInput >= amount + fee) { "Saldo insuficiente" }

        val change = totalInput - amount - fee
        val outputs = mutableListOf(TxOut(amount, AddressEncoder.addressToScriptPubKey(toAddress)))
        if (change > DUST) {
            outputs.add(TxOut(change, AddressEncoder.addressToScriptPubKey(changeAddress)))
        }

        val inputs = selected.map { utxo ->
            TxIn(
                prevTxHash = UInt256.fromHex(utxo.txHash),
                prevIndex = utxo.outputIndex.toLong(),
                scriptSig = ByteArray(0)
            )
        }

        var tx = Transaction(
            version = 1,
            time = System.currentTimeMillis() / 1000,
            inputs = inputs,
            outputs = outputs
        )

        val signedInputs = inputs.mapIndexed { index, input ->
            val utxo = selected[index]
            val signingKey = utxoKeys[utxo.id]
                ?: error("Chave de assinatura não encontrada para UTXO ${utxo.txHash}:${utxo.outputIndex}")
            val pubKeyHash = Hash160.hash(signingKey.publicKey)
            val scriptCode = byteArrayOf(0x76, 0xA9.toByte(), 0x14) + pubKeyHash + byteArrayOf(0x88.toByte(), 0xAC.toByte())
            val sighash = buildSignatureHash(tx, index, scriptCode)
            val signature = Secp256k1.sign(signingKey.privateKey, sighash)
            val scriptSig = encodeScriptSig(signature, signingKey.publicKey)
            input.copy(scriptSig = scriptSig)
        }

        return tx.copy(inputs = signedInputs)
    }

    private fun selectUtxos(
        utxos: List<UtxoEntity>,
        target: Long,
        feePerByte: Long
    ): List<UtxoEntity> {
        val sorted = utxos.sortedByDescending { it.value }
        val selected = mutableListOf<UtxoEntity>()
        var total = 0L
        for (utxo in sorted) {
            selected.add(utxo)
            total += utxo.value
            val estimatedSize = 10 + selected.size * 148 + 2 * 34
            val fee = estimatedSize * feePerByte
            if (total >= target + fee) break
        }
        val finalFee = (10 + selected.size * 148 + 2 * 34) * feePerByte
        require(total >= target + finalFee) { "Saldo insuficiente para valor + taxa" }
        return selected
    }

    private fun buildSignatureHash(tx: Transaction, inputIndex: Int, scriptCode: ByteArray): ByteArray {
        val serialized = tx.serialize(forSignature = true, inputIndex = inputIndex, scriptCode = scriptCode)
        val withHashType = serialized + byteArrayOf(0x01)
        return Sha256.hashTwice(withHashType)
    }

    private fun encodeScriptSig(signature: ByteArray, publicKey: ByteArray): ByteArray {
        val derSig = encodeDerSignature(signature) + byteArrayOf(0x01)
        val out = BitcoinOutput()
        out.writeVarBytes(derSig)
        out.writeVarBytes(publicKey)
        return out.toByteArray()
    }

    private fun encodeDerSignature(compact: ByteArray): ByteArray {
        val r = BigInteger(1, compact.copyOfRange(0, 32))
        val s = BigInteger(1, compact.copyOfRange(32, 64))
        val rBytes = stripLeadingZeros(r.toByteArray())
        val sBytes = stripLeadingZeros(s.toByteArray())
        val totalLen = 4 + rBytes.size + sBytes.size
        return byteArrayOf(0x30, totalLen.toByte(), 0x02, rBytes.size.toByte()) +
            rBytes + byteArrayOf(0x02, sBytes.size.toByte()) + sBytes
    }

    private fun stripLeadingZeros(bytes: ByteArray): ByteArray {
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
        var result = bytes.copyOfRange(start, bytes.size)
        if (result.isNotEmpty() && (result[0].toInt() and 0x80) != 0) {
            result = byteArrayOf(0) + result
        }
        return result
    }
}
