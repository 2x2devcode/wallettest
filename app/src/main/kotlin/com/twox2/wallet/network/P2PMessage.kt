package com.twox2.wallet.network

import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.chain.UInt256
import com.twox2.wallet.crypto.Sha256
import com.twox2.wallet.serialization.BitcoinInput
import com.twox2.wallet.serialization.BitcoinOutput
import java.nio.ByteBuffer
import java.nio.ByteOrder

object P2PMessage {
    const val HEADER_SIZE = 24

    fun create(command: String, payload: ByteArray): ByteArray {
        val out = BitcoinOutput()
        out.writeBytes(ChainParams.MESSAGE_START)
        val cmd = ByteArray(12)
        command.toByteArray().copyInto(cmd)
        out.writeBytes(cmd)
        out.writeUInt32(payload.size.toLong())
        val checksum = Sha256.hashTwice(payload).copyOfRange(0, 4)
        out.writeBytes(checksum)
        out.writeBytes(payload)
        return out.toByteArray()
    }

    fun parseHeader(data: ByteArray): Triple<String, Int, Int>? {
        if (data.size < HEADER_SIZE) return null
        val magic = data.copyOfRange(0, 4)
        if (!magic.contentEquals(ChainParams.MESSAGE_START)) return null
        val command = String(data.copyOfRange(4, 16)).trim('\u0000')
        val size = ByteBuffer.wrap(data, 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return Triple(command, size, HEADER_SIZE)
    }

    /** CAddress serializado para protocolo >= 31402: time(4) + services(8) + ip(16) + port(2) = 30 bytes */
    fun buildNetworkAddress(port: Int = 0): ByteArray {
        val out = BitcoinOutput()
        out.writeUInt32(System.currentTimeMillis() / 1000)
        out.writeInt64(1) // NODE_NETWORK
        out.writeBytes(ByteArray(16)) // 0.0.0.0
        out.writeUInt16(port)
        return out.toByteArray()
    }

    fun buildVersionPayload(nonce: Long, startHeight: Int = 0): ByteArray {
        val out = BitcoinOutput()
        out.writeInt32(ChainParams.PROTOCOL_VERSION)
        out.writeInt64(1) // services: NODE_NETWORK
        out.writeInt64(System.currentTimeMillis() / 1000)
        out.writeBytes(buildNetworkAddress())
        out.writeBytes(buildNetworkAddress(ChainParams.P2P_PORT))
        out.writeVarBytes(ChainParams.USER_AGENT.toByteArray())
        out.writeInt32(startHeight)
        out.writeByte(1) // relay
        return out.toByteArray()
    }

    fun buildGetHeadersPayload(locator: List<UInt256>, hashStop: UInt256 = UInt256.ZERO): ByteArray {
        val out = BitcoinOutput()
        out.writeInt32(ChainParams.PROTOCOL_VERSION)
        out.writeVarInt(locator.size.toLong())
        locator.forEach { it.serialize(out) }
        hashStop.serialize(out)
        return out.toByteArray()
    }

    fun buildGetDataPayload(inventory: List<InventoryItem>): ByteArray {
        val out = BitcoinOutput()
        out.writeVarInt(inventory.size.toLong())
        inventory.forEach { item ->
            out.writeUInt32(item.type.toLong())
            item.hash.serialize(out)
        }
        return out.toByteArray()
    }

    fun buildInvPayload(items: List<InventoryItem>): ByteArray {
        val out = BitcoinOutput()
        out.writeVarInt(items.size.toLong())
        items.forEach { item ->
            out.writeUInt32(item.type.toLong())
            item.hash.serialize(out)
        }
        return out.toByteArray()
    }

    fun parseHeadersPayload(payload: ByteArray): List<com.twox2.wallet.chain.BlockHeader> {
        val input = BitcoinInput(payload)
        val count = input.readVarInt().toInt()
        return (0 until count).map {
            com.twox2.wallet.chain.BlockHeader.deserialize(input)
        }
    }

    fun parseInvPayload(payload: ByteArray): List<InventoryItem> {
        val input = BitcoinInput(payload)
        val count = input.readVarInt().toInt()
        return (0 until count).map {
            InventoryItem(
                type = input.readUInt32().toInt(),
                hash = UInt256(input.readUInt256())
            )
        }
    }
}

data class InventoryItem(val type: Int, val hash: UInt256) {
    companion object {
        const val MSG_TX = 1
        const val MSG_BLOCK = 2
        const val MSG_FILTERED_BLOCK = 3
    }
}
