package com.twox2.wallet.serialization

import java.nio.ByteBuffer
import java.nio.ByteOrder

class BitcoinOutput {
    private val buffer = ArrayList<Byte>()

    fun writeByte(value: Byte) {
        buffer.add(value)
    }

    fun writeBytes(data: ByteArray) {
        buffer.addAll(data.toList())
    }

    fun writeUInt16(value: Int) {
        writeByte((value shr 8).toByte())
        writeByte((value and 0xFF).toByte())
    }

    fun writeUInt32(value: Long) {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(value.toInt())
        writeBytes(bb.array())
    }

    fun writeInt32(value: Int) {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(value)
        writeBytes(bb.array())
    }

    fun writeInt64(value: Long) {
        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(value)
        writeBytes(bb.array())
    }

    fun writeVarInt(value: Long) {
        when {
            value < 0xFD -> writeByte(value.toByte())
            value <= 0xFFFF -> {
                writeByte(0xFD.toByte())
                writeUInt32(value)
            }
            value <= 0xFFFFFFFFL -> {
                writeByte(0xFE.toByte())
                writeUInt32(value)
            }
            else -> {
                writeByte(0xFF.toByte())
                writeInt64(value)
            }
        }
    }

    fun writeVarBytes(data: ByteArray) {
        writeVarInt(data.size.toLong())
        writeBytes(data)
    }

    fun writeUInt256(hash: ByteArray) {
        require(hash.size == 32)
        writeBytes(hash)
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}
