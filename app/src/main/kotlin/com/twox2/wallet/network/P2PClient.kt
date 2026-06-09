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
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS
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

    sealed class SendResult {
        data object Relayed : SendResult()
        data class Rejected(val reason: String) : SendResult()
        data object Failed : SendResult()
    }

    fun sendTransaction(tx: Transaction): SendResult {
        val txHash = tx.getHash()
        val txBytes = tx.serialize()
        sendMessage("inv", P2PMessage.buildInvPayload(listOf(InventoryItem(InventoryItem.MSG_TX, txHash))))

        var relayedViaGetData = false
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val msg = readMessageBlocking() ?: break
            when (msg.command) {
                "getdata" -> {
                    val items = P2PMessage.parseInvPayload(msg.payload)
                    if (items.any { it.type == InventoryItem.MSG_TX && it.hash == txHash }) {
                        sendMessage("tx", txBytes)
                        relayedViaGetData = true
                        Log.d(TAG, "[$host] tx enviada após getdata")
                    }
                }
                "reject" -> {
                    val reason = parseRejectReason(msg.payload)
                    Log.w(TAG, "[$host] tx rejeitada: $reason")
                    return SendResult.Rejected(reason)
                }
                "ping" -> sendMessage("pong", msg.payload)
                "inv", "headers", "addr" -> Unit
            }
            if (relayedViaGetData) {
                val ackDeadline = System.currentTimeMillis() + 3_000
                while (System.currentTimeMillis() < ackDeadline) {
                    val ack = readMessageBlocking() ?: break
                    when (ack.command) {
                        "reject" -> {
                            val reason = parseRejectReason(ack.payload)
                            Log.w(TAG, "[$host] tx rejeitada após relay: $reason")
                            return SendResult.Rejected(reason)
                        }
                        "ping" -> sendMessage("pong", ack.payload)
                        else -> Unit
                    }
                }
                return SendResult.Relayed
            }
        }

        if (!relayedViaGetData) {
            sendMessage("tx", txBytes)
            Log.d(TAG, "[$host] tx enviada (unsolicited)")
            val rejectDeadline = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < rejectDeadline) {
                val msg = readMessageBlocking() ?: break
                when (msg.command) {
                    "reject" -> {
                        val reason = parseRejectReason(msg.payload)
                        Log.w(TAG, "[$host] tx rejeitada (unsolicited): $reason")
                        return SendResult.Rejected(reason)
                    }
                    "ping" -> sendMessage("pong", msg.payload)
                    else -> Unit
                }
            }
        }

        return if (relayedViaGetData) SendResult.Relayed else SendResult.Failed
    }

    private fun parseRejectReason(payload: ByteArray): String {
        return runCatching {
            val input = com.twox2.wallet.serialization.BitcoinInput(payload)
            val message = input.readVarString()
            val code = input.readByte().toInt() and 0xFF
            val reason = input.readVarString()
            "$message (code=$code): $reason"
        }.getOrElse { "rejeitada pelo peer" }
    }

    private fun readMessageBlocking(): P2PMessageData? {
        val ins = input ?: return null
        return runCatching {
            val header = ByteArray(P2PMessage.HEADER_SIZE)
            var read = 0
            while (read < header.size) {
                val r = ins.read(header, read, header.size - read)
                if (r < 0) return null
                read += r
            }
            val parsed = P2PMessage.parseHeader(header) ?: return null
            val (command, size, _) = parsed
            if (size > 32 * 1024 * 1024) return null
            val payload = ByteArray(size)
            read = 0
            while (read < size) {
                val r = ins.read(payload, read, size - read)
                if (r < 0) return null
                read += r
            }
            P2PMessageData(command, payload)
        }.getOrNull()
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
        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
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
        return false
    }

    private fun buildEmptyHeadersPayload(): ByteArray {
        return byteArrayOf(0x00)
    }

    companion object {
        private const val TAG = "TwoX2P2P"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val HANDSHAKE_TIMEOUT_MS = 12_000
    }
}

data class P2PMessageData(val command: String, val payload: ByteArray)
