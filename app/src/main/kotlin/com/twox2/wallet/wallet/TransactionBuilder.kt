package com.twox2.wallet.wallet

import com.twox2.wallet.chain.Transaction
import com.twox2.wallet.chain.TxIn
import com.twox2.wallet.chain.TxOut
import com.twox2.wallet.chain.UInt256
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.crypto.Secp256k1
import com.twox2.wallet.crypto.Sha256
import com.twox2.wallet.data.db.UtxoEntity
import com.twox2.wallet.serialization.BitcoinOutput
import java.math.BigInteger

object TransactionBuilder {
    const val DUST_THRESHOLD = 546L
    private const val DUST = DUST_THRESHOLD

    data class SigningKey(val privateKey: ByteArray, val publicKey: ByteArray)

    data class BuildResult(val transaction: Transaction, val fee: Long)

    fun estimateFee(inputCount: Int, outputCount: Int, feePerByte: Long): Long {
        val size = estimateSize(inputCount, outputCount)
        return size * feePerByte
    }

    fun buildAndSign(
        utxos: List<UtxoEntity>,
        utxoKeys: Map<Long, SigningKey>,
        toAddress: String,
        amount: Long,
        changeAddress: String,
        feePerByte: Long = 10L,
        networkTime: Long = System.currentTimeMillis() / 1000
    ): BuildResult {
        require(AddressEncoder.isValidAddress(toAddress)) { "Endereço de destino inválido" }
        require(amount > DUST) { "Valor abaixo do mínimo" }

        val selected = selectUtxos(utxos, amount, feePerByte)
        val totalInput = selected.sumOf { it.value }

        var outputCount = 2
        var fee = estimateFee(selected.size, outputCount, feePerByte)
        var change = totalInput - amount - fee
        if (change <= DUST) {
            outputCount = 1
            fee = estimateFee(selected.size, outputCount, feePerByte)
            change = totalInput - amount - fee
        }
        require(totalInput >= amount + fee) { "Saldo insuficiente" }

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
            time = networkTime,
            inputs = inputs,
            outputs = outputs
        )

        val actualSize = tx.serialize().size
        val actualFee = actualSize * feePerByte
        val actualChange = totalInput - amount - actualFee
        if (actualFee != fee || (actualChange > DUST) != (change > DUST)) {
            fee = actualFee
            val rebuiltOutputs = mutableListOf(TxOut(amount, AddressEncoder.addressToScriptPubKey(toAddress)))
            if (actualChange > DUST) {
                rebuiltOutputs.add(TxOut(actualChange, AddressEncoder.addressToScriptPubKey(changeAddress)))
            }
            require(totalInput >= amount + fee) { "Saldo insuficiente para valor + taxa" }
            tx = tx.copy(outputs = rebuiltOutputs)
        }

        val signedInputs = inputs.mapIndexed { index, input ->
            val utxo = selected[index]
            val signingKey = utxoKeys[utxo.id]
                ?: error("Chave de assinatura não encontrada para UTXO ${utxo.txHash}:${utxo.outputIndex}")
            val scriptCode = utxo.scriptPubKey.hexToBytes()
            require(scriptCode.isNotEmpty()) { "scriptPubKey inválido para UTXO ${utxo.txHash}:${utxo.outputIndex}" }
            val sighash = buildSignatureHash(tx, index, scriptCode)
            val signature = Secp256k1.sign(signingKey.privateKey, sighash)
            require(Secp256k1.verify(signingKey.publicKey, sighash, signature)) {
                "Assinatura inválida para input $index"
            }
            val scriptSig = encodeScriptSig(signature, signingKey.publicKey)
            input.copy(scriptSig = scriptSig)
        }

        val signedTx = tx.copy(inputs = signedInputs)
        val finalFee = totalInput - signedTx.outputs.sumOf { it.value }
        return BuildResult(signedTx, finalFee)
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
            var outputCount = 2
            var fee = estimateFee(selected.size, outputCount, feePerByte)
            var change = total - target - fee
            if (change <= DUST) {
                outputCount = 1
                fee = estimateFee(selected.size, outputCount, feePerByte)
            }
            if (total >= target + fee) break
        }
        var outputCount = 2
        var finalFee = estimateFee(selected.size, outputCount, feePerByte)
        if (total - target - finalFee <= DUST) {
            outputCount = 1
            finalFee = estimateFee(selected.size, outputCount, feePerByte)
        }
        require(total >= target + finalFee) { "Saldo insuficiente para valor + taxa" }
        return selected
    }

    private fun estimateSize(inputCount: Int, outputCount: Int): Int {
        return 10 + inputCount * 148 + outputCount * 34
    }

    private fun buildSignatureHash(tx: Transaction, inputIndex: Int, scriptCode: ByteArray): ByteArray {
        val serialized = tx.serialize(forSignature = true, inputIndex = inputIndex, scriptCode = scriptCode)
        val withHashType = serialized + int32Le(SIGHASH_ALL)
        return Sha256.hashTwice(withHashType)
    }

    private fun int32Le(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun encodeScriptPush(data: ByteArray): ByteArray {
        val out = BitcoinOutput()
        when {
            data.size < 0x4C -> out.writeByte(data.size.toByte())
            data.size <= 0xFF -> {
                out.writeByte(0x4C)
                out.writeByte(data.size.toByte())
            }
            else -> {
                out.writeByte(0x4D)
                out.writeUInt16(data.size)
            }
        }
        out.writeBytes(data)
        return out.toByteArray()
    }

    private const val SIGHASH_ALL = 1

    private fun encodeScriptSig(signature: ByteArray, publicKey: ByteArray): ByteArray {
        val derSig = encodeDerSignature(signature) + byteArrayOf(SIGHASH_ALL.toByte())
        return encodeScriptPush(derSig) + encodeScriptPush(publicKey)
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

    private fun String.hexToBytes(): ByteArray {
        val normalized = lowercase()
        require(normalized.length % 2 == 0) { "hex inválido" }
        return ByteArray(normalized.length / 2) { i ->
            normalized.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
