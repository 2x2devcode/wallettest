package com.twox2.wallet.serialization

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BitcoinInput(private val data: ByteArray) {
    private var offset = 0

    fun readByte(): Byte {
        require(offset < data.size)
        return data[offset++]
    }

    fun readBytes(count: Int): ByteArray {
        require(offset + count <= data.size)
        val result = data.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    fun readUInt16(): Int {
        val bytes = readBytes(2)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    fun readUInt32(): Long {
        val bytes = readBytes(4)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    fun readInt32(): Int {
        val bytes = readBytes(4)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun readInt64(): Long {
        val bytes = readBytes(8)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    fun readVarInt(): Long {
        val first = readByte().toInt() and 0xFF
        return when {
            first < 0xFD -> first.toLong()
            first == 0xFD -> readUInt16().toLong()
            first == 0xFE -> readUInt32()
            else -> readInt64()
        }
    }

    fun readVarBytes(): ByteArray {
        val len = readVarInt().toInt()
        return readBytes(len)
    }

    fun readUInt256(): ByteArray = readBytes(32)

    fun remaining(): Int = data.size - offset
}
