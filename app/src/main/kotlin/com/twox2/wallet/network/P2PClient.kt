package com.twox2.wallet.network

import android.util.Log
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

    suspend fun connect(startHeight: Int = 0): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val s = Socket()
            s.soTimeout = 0
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port))
            socket = s
            input = BufferedInputStream(s.getInputStream())
            output = BufferedOutputStream(s.getOutputStream())
            sendMessage("version", P2PMessage.buildVersionPayload(nonce.get(), startHeight))
            Log.d(TAG, "Conectado a $host:$port")
            true
        }.onFailure {
            Log.w(TAG, "Falha ao conectar $host:$port - ${it.message}")
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

    fun sendSendHeaders() = sendMessage("sendheaders", ByteArray(0))

    fun requestHeaders(locator: List<UInt256>, hashStop: UInt256 = UInt256.ZERO) {
        sendMessage("getheaders", P2PMessage.buildGetHeadersPayload(locator, hashStop))
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
        if (size > 32 * 1024 * 1024) return@withContext null
        val payload = ByteArray(size)
        read = 0
        while (read < size) {
            val r = ins.read(payload, read, size - read)
            if (r < 0) return@withContext null
            read += r
        }
        P2PMessageData(command, payload)
    }

    var peerStartHeight: Int = 0
        private set

    suspend fun handshake(): Boolean {
        var sentVerack = false
        var gotVerack = false
        while (true) {
            val msg = readMessage() ?: return false
            Log.d(TAG, "[$host] recebeu: ${msg.command}")
            when (msg.command) {
                "version" -> {
                    P2PMessage.parseVersionStartHeight(msg.payload)?.let { peerStartHeight = it }
                    sendVerack()
                    sentVerack = true
                }
                "verack" -> gotVerack = true
                "ping" -> sendMessage("pong", msg.payload)
                "getheaders" -> sendMessage("headers", buildEmptyHeadersPayload())
                "sendheaders", "sendcmpct", "feefilter", "addr", "inv" -> Unit
            }
            if (sentVerack && gotVerack) {
                sendSendHeaders()
                Log.d(TAG, "[$host] handshake OK, peerHeight=$peerStartHeight")
                return true
            }
        }
    }

    private fun buildEmptyHeadersPayload(): ByteArray {
        return byteArrayOf(0x00) // varint 0 headers
    }

    companion object {
        private const val TAG = "TwoX2P2P"
    }
}

data class P2PMessageData(val command: String, val payload: ByteArray)
