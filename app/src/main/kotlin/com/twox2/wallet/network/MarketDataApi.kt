package com.twox2.wallet.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MarketDataApi {
    private const val TAG = "TwoX2MarketData"
    private const val MARKET_DATA_URL =
        "https://qutrade.io/api/v1/market_data/?pair=2x2_usdt"
    private const val PAIR = "2x2_usdt"

    suspend fun get2x2UsdtPrice(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetchText(MARKET_DATA_URL) ?: return@withContext null
            val root = JSONObject(body)
            if (root.optString("result") != "success") return@withContext null
            val pair = root.optJSONObject("list")?.optJSONObject(PAIR) ?: return@withContext null
            pair.optString("price").toDoubleOrNull()?.takeIf { it > 0 }
        }.onFailure {
            Log.w(TAG, "Falha ao obter preço 2X2/USDT", it)
        }.getOrNull()
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
