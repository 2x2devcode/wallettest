package com.twox2.wallet.network

import com.twox2.wallet.chain.Block
import com.twox2.wallet.chain.BlockHeader
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.chain.Transaction
import com.twox2.wallet.chain.UInt256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class P2PClient(
    private val host: String,
    private val port: Int = ChainParams.P2P_PORT
) {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private val nonce = AtomicLong(Random.nextLong())

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val s = Socket()
            s.soTimeout = 30_000
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 15_000)
            socket = s
            input = BufferedInputStream(s.getInputStream())
            output = BufferedOutputStream(s.getOutputStream())
            sendMessage("version", P2PMessage.buildVersionPayload(nonce.get()))
            true
        }.getOrElse { false }
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    fun sendMessage(command: String, payload: ByteArray) {
        val message = P2PMessage.create(command, payload)
        output?.write(message)
        output?.flush()
    }

    fun sendVerack() = sendMessage("verack", ByteArray(0))

    fun requestHeaders(locator: List<UInt256>) {
        sendMessage("getheaders", P2PMessage.buildGetHeadersPayload(locator))
    }

    fun requestBlocks(hashes: List<UInt256>) {
        val items = hashes.map { InventoryItem(InventoryItem.MSG_BLOCK, it) }
        sendMessage("getdata", P2PMessage.buildGetDataPayload(items))
    }

    fun sendTransaction(tx: Transaction) {
        val items = listOf(InventoryItem(InventoryItem.MSG_TX, tx.getHash()))
        sendMessage("inv", P2PMessage.buildInvPayload(items))
        sendMessage("tx", tx.serialize())
    }

    suspend fun readMessage(): P2PMessageData? = withContext(Dispatchers.IO) {
        val ins = input ?: return@withContext null
        val header = ByteArray(P2PMessage.HEADER_SIZE)
        var read = 0
        while (read < header.size) {
            val r = ins.read(header, read, header.size - read)
            if (r < 0) return@withContext null
            read += r
        }
        val parsed = P2PMessage.parseHeader(header) ?: return@withContext null
        val (command, size, _) = parsed
        val payload = ByteArray(size)
        read = 0
        while (read < size) {
            val r = ins.read(payload, read, size - read)
            if (r < 0) return@withContext null
            read += r
        }
        P2PMessageData(command, payload)
    }

    suspend fun handshake(): Boolean {
        repeat(10) {
            val msg = readMessage() ?: return false
            when (msg.command) {
                "version" -> {
                    sendVerack()
                }
                "verack" -> return true
                "ping" -> sendMessage("pong", msg.payload)
            }
        }
        return false
    }
}

data class P2PMessageData(val command: String, val payload: ByteArray)
