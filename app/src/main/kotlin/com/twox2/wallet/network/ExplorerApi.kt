package com.twox2.wallet.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ExplorerApi {
    private const val TAG = "TwoX2Explorer"
    private const val BASE_URL = "https://newexplorer.2x2coin.com/api"

    suspend fun getBlockCount(): Int? = getBlockCountWithRetry(3)

    suspend fun getBlockCountWithRetry(attempts: Int = 3): Int? = withContext(Dispatchers.IO) {
        repeat(attempts) { attempt ->
            val value = fetchText("$BASE_URL/getblockcount")?.trim()?.toIntOrNull()
            if (value != null && value >= 0) return@withContext value
            if (attempt < attempts - 1) delay(2_000L * (attempt + 1))
        }
        Log.w(TAG, "Falha ao obter altura da rede via explorer")
        null
    }

    suspend fun getBlockHash(height: Int): String? = withContext(Dispatchers.IO) {
        fetchText("$BASE_URL/getblockhash?index=$height")?.trim()
    }

    suspend fun verifyBlockHash(height: Int, hash: String): Boolean = withContext(Dispatchers.IO) {
        val expected = getBlockHash(height) ?: return@withContext false
        hash.equals(expected, ignoreCase = true)
    }

    private fun fetchText(urlString: String): String? = runCatching {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
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
