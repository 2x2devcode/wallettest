package com.twox2.wallet.chain

import com.twox2.wallet.crypto.Sha256
import com.twox2.wallet.serialization.BitcoinInput
import com.twox2.wallet.serialization.BitcoinOutput

data class TxOut(val value: Long, val scriptPubKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is TxOut && value == other.value && scriptPubKey.contentEquals(other.scriptPubKey)

    override fun hashCode(): Int = 31 * value.hashCode() + scriptPubKey.contentHashCode()
}

data class TxIn(
    val prevTxHash: UInt256,
    val prevIndex: Long,
    val scriptSig: ByteArray,
    val sequence: Long = 0xFFFFFFFFL
) {
    fun isCoinBase(): Boolean = prevTxHash == UInt256.ZERO && prevIndex == 0xFFFFFFFFL

    override fun equals(other: Any?): Boolean =
        other is TxIn && prevTxHash == other.prevTxHash && prevIndex == other.prevIndex &&
            scriptSig.contentEquals(other.scriptSig) && sequence == other.sequence

    override fun hashCode(): Int = prevTxHash.hashCode()
}

data class Transaction(
    val version: Int = 1,
    val time: Long = 0,
    val inputs: List<TxIn>,
    val outputs: List<TxOut>,
    val lockTime: Long = 0
) {
    fun isCoinBase(): Boolean = inputs.size == 1 && inputs[0].isCoinBase()

    fun serialize(forSignature: Boolean = false, inputIndex: Int = 0, scriptCode: ByteArray? = null): ByteArray {
        val out = BitcoinOutput()
        out.writeInt32(version)
        out.writeUInt32(time)
        out.writeVarInt(inputs.size.toLong())
        inputs.forEachIndexed { index, input ->
            input.prevTxHash.serialize(out)
            out.writeUInt32(input.prevIndex)
            val script = when {
                forSignature && index == inputIndex -> scriptCode ?: ByteArray(0)
                forSignature -> ByteArray(0)
                else -> input.scriptSig
            }
            out.writeVarBytes(script)
            out.writeUInt32(input.sequence)
        }
        out.writeVarInt(outputs.size.toLong())
        outputs.forEach { txOut ->
            out.writeInt64(txOut.value)
            out.writeVarBytes(txOut.scriptPubKey)
        }
        out.writeUInt32(lockTime)
        return out.toByteArray()
    }

    fun getHash(): UInt256 = UInt256(Sha256.hashTwice(serialize()))

    companion object {
        fun deserialize(data: ByteArray): Transaction {
            val input = BitcoinInput(data)
            val version = input.readInt32()
            val time = input.readUInt32()
            val inCount = input.readVarInt().toInt()
            val inputs = (0 until inCount).map {
                TxIn(
                    prevTxHash = UInt256(input.readUInt256()),
                    prevIndex = input.readUInt32(),
                    scriptSig = input.readVarBytes(),
                    sequence = input.readUInt32()
                )
            }
            val outCount = input.readVarInt().toInt()
            val outputs = (0 until outCount).map {
                TxOut(
                    value = input.readInt64(),
                    scriptPubKey = input.readVarBytes()
                )
            }
            val lockTime = input.readUInt32()
            return Transaction(version, time, inputs, outputs, lockTime)
        }
    }
}

data class Block(
    val header: BlockHeader,
    val transactions: List<Transaction>,
    val blockSig: ByteArray = ByteArray(0)
) {
    fun serialize(): ByteArray {
        val out = BitcoinOutput()
        out.writeBytes(header.serialize())
        out.writeVarInt(transactions.size.toLong())
        transactions.forEach { out.writeBytes(it.serialize()) }
        out.writeVarBytes(blockSig)
        return out.toByteArray()
    }

    companion object {
        fun deserialize(data: ByteArray): Block {
            val input = BitcoinInput(data)
            val header = BlockHeader.deserialize(input)
            val txCount = input.readVarInt().toInt()
            val txs = mutableListOf<Transaction>()
            repeat(txCount) {
                val offset = data.size - input.remaining()
                val tx = Transaction.deserialize(data.copyOfRange(offset, data.size))
                val txBytes = tx.serialize()
                repeat(txBytes.size) { input.readByte() }
                txs.add(tx)
            }
            val sig = if (input.remaining() > 0) input.readVarBytes() else ByteArray(0)
            return Block(header, txs, sig)
        }
    }
}
