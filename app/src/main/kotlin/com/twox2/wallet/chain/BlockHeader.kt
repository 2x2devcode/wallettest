package com.twox2.wallet.chain

import com.twox2.wallet.crypto.ScryptHash
import com.twox2.wallet.crypto.Sha256
import com.twox2.wallet.serialization.BitcoinInput
import com.twox2.wallet.serialization.BitcoinOutput

data class BlockHeader(
    val version: Int,
    val prevBlock: UInt256,
    val merkleRoot: UInt256,
    val time: Long,
    val bits: Long,
    val nonce: Long
) {
    fun serialize(): ByteArray {
        val out = BitcoinOutput()
        out.writeInt32(version)
        prevBlock.serialize(out)
        merkleRoot.serialize(out)
        out.writeUInt32(time)
        out.writeUInt32(bits)
        out.writeUInt32(nonce)
        return out.toByteArray()
    }

    fun getHash(): UInt256 {
        val serialized = serialize()
        val hash = if (version > 6) {
            Sha256.hashTwice(serialized)
        } else {
            ScryptHash.hashBlockHeader(serialized)
        }
        return UInt256(hash)
    }

    companion object {
        fun deserialize(input: BitcoinInput): BlockHeader {
            return BlockHeader(
                version = input.readInt32(),
                prevBlock = UInt256(input.readUInt256()),
                merkleRoot = UInt256(input.readUInt256()),
                time = input.readUInt32(),
                bits = input.readUInt32(),
                nonce = input.readUInt32()
            )
        }

        val GENESIS = BlockHeader(
            version = 1,
            prevBlock = UInt256.ZERO,
            merkleRoot = UInt256.fromHex("8ec923e8d644631a549ddbd51cd350f597e29ca665979f01a501456bbc77f16b"),
            time = 1769817600,
            bits = 0x1e0fffff,
            nonce = 184621
        )
    }
}
