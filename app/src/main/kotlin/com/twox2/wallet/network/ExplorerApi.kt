package com.twox2.wallet.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ExplorerApi {
    private const val TAG = "TwoX2Explorer"
    private const val BASE_URL = "https://newexplorer.2x2coin.com/api"
    private const val EXT_URL = "https://newexplorer.2x2coin.com/ext"

    suspend fun getBlockCount(): Int? = getBlockCountWithRetry(3)

    suspend fun getBlockCountWithRetry(attempts: Int = 3): Int? = withContext(Dispatchers.IO) {
        getSummary()?.blockCount ?: run {
            repeat(attempts) { attempt ->
                val value = fetchText("$BASE_URL/getblockcount")?.trim()?.toIntOrNull()
                if (value != null && value >= 0) return@withContext value
                if (attempt < attempts - 1) delay(2_000L * (attempt + 1))
            }
            Log.w(TAG, "Falha ao obter altura da rede via explorer")
            null
        }
    }

    suspend fun getSummary(): ExplorerSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetchText("$EXT_URL/getsummary") ?: return@withContext null
            val json = JSONObject(body)
            ExplorerSummary(
                blockCount = json.optInt("blockcount", -1).takeIf { it >= 0 }
                    ?: return@withContext null
            )
        }.onFailure {
            Log.w(TAG, "Falha ao obter summary do explorer", it)
        }.getOrNull()
    }

    suspend fun getAddressBalance(address: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetchText("$EXT_URL/getbalance/$address")?.trim() ?: return@withContext null
            val coins = body.toDoubleOrNull() ?: return@withContext null
            (coins * com.twox2.wallet.chain.ChainParams.COIN).toLong()
        }.onFailure {
            Log.w(TAG, "Falha ao obter saldo de $address", it)
        }.getOrNull()
    }

    suspend fun getAddressTxs(
        address: String,
        start: Int = 0,
        length: Int = 50
    ): List<ExplorerAddressTx> = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetchText("$EXT_URL/getaddresstxs/$address/$start/$length") ?: return@withContext emptyList()
            val array = JSONArray(body)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ExplorerAddressTx(
                            txid = item.optString("txid"),
                            timestamp = item.optLong("timestamp"),
                            sent = item.optDouble("sent"),
                            received = item.optDouble("received"),
                            balance = item.optDouble("balance")
                        )
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "Falha ao obter transações de $address", it)
        }.getOrDefault(emptyList())
    }

    suspend fun getTx(txid: String): ExplorerTxDetail? = withContext(Dispatchers.IO) {
        fetchTxDetail(txid, fast = false)
    }

    suspend fun getTxFast(txid: String): ExplorerTxDetail? = withContext(Dispatchers.IO) {
        fetchTxDetail(txid, fast = true)
    }

    suspend fun txExists(txid: String): Boolean = getTx(txid) != null

    suspend fun txExistsFast(txid: String): Boolean = getTxFast(txid) != null

    suspend fun getNetworkTime(): Long? = withContext(Dispatchers.IO) {
        val blockTime = getLatestBlockTime() ?: return@withContext null
        val now = System.currentTimeMillis() / 1000
        val futureDrift = 15L
        minOf(now, blockTime + futureDrift)
    }

    private fun fetchTxDetail(txid: String, fast: Boolean): ExplorerTxDetail? = runCatching {
        val body = fetchText("$EXT_URL/gettx/$txid", fast = fast) ?: return@runCatching null
        val root = JSONObject(body)
        val tx = root.optJSONObject("tx") ?: return@runCatching null
        val vouts = tx.optJSONArray("vout") ?: JSONArray()
        val outputs = buildList {
            for (i in 0 until vouts.length()) {
                val vout = vouts.getJSONObject(i)
                add(
                    ExplorerTxOutput(
                        address = vout.optString("addresses"),
                        amount = vout.optLong("amount")
                    )
                )
            }
        }
        ExplorerTxDetail(
            txid = tx.optString("txid", txid),
            blockHeight = tx.optInt("blockindex", -1),
            timestamp = tx.optLong("timestamp"),
            fee = tx.optLong("fee"),
            outputs = outputs
        )
    }.onFailure {
        if (!fast) Log.w(TAG, "Falha ao obter tx $txid", it)
    }.getOrNull()

    suspend fun getLatestBlockTime(): Long? = withContext(Dispatchers.IO) {
        val height = getBlockCount() ?: return@withContext null
        getBlockAtHeight(height)?.time
    }

    suspend fun getBlockHash(height: Int): String? = withContext(Dispatchers.IO) {
        fetchText("$BASE_URL/getblockhash?index=$height")?.trim()
    }

    suspend fun getBlockAtHeight(height: Int): ExplorerBlock? = withContext(Dispatchers.IO) {
        val hash = getBlockHash(height) ?: return@withContext null
        getBlock(hash)
    }

    suspend fun getBlock(hash: String): ExplorerBlock? = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetchText("$BASE_URL/getblock?hash=$hash") ?: return@withContext null
            val json = JSONObject(body)
            ExplorerBlock(
                hash = json.getString("hash"),
                height = json.getInt("height"),
                previousBlockHash = json.optString("previousblockhash", ""),
                merkleRoot = json.getString("merkleroot"),
                time = json.getLong("time"),
                bitsHex = json.getString("bits"),
                nonce = json.getLong("nonce"),
                version = json.getInt("version")
            )
        }.onFailure {
            Log.w(TAG, "Falha ao obter bloco $hash", it)
        }.getOrNull()
    }

    suspend fun verifyBlockHash(height: Int, hash: String): Boolean = withContext(Dispatchers.IO) {
        val expected = getBlockHash(height) ?: return@withContext false
        hash.equals(expected, ignoreCase = true)
    }

    private fun fetchText(urlString: String, fast: Boolean = false): String? = runCatching {
        val timeout = if (fast) 6_000 else 12_000
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            requestMethod = "GET"
            setRequestProperty("User-Agent", "2X2Wallet/1.0")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "HTTP ${connection.responseCode} para $urlString")
            return@runCatching null
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}

data class ExplorerSummary(val blockCount: Int)

data class ExplorerAddressTx(
    val txid: String,
    val timestamp: Long,
    val sent: Double,
    val received: Double,
    val balance: Double
)

data class ExplorerTxDetail(
    val txid: String,
    val blockHeight: Int,
    val timestamp: Long,
    val fee: Long = 0,
    val outputs: List<ExplorerTxOutput>
)

data class ExplorerTxOutput(
    val address: String,
    val amount: Long
)

data class ExplorerBlock(
    val hash: String,
    val height: Int,
    val previousBlockHash: String,
    val merkleRoot: String,
    val time: Long,
    val bitsHex: String,
    val nonce: Long,
    val version: Int
)
