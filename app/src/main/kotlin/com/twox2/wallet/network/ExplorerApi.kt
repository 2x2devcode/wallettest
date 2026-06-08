package com.twox2.wallet.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ExplorerApi {
    private const val BLOCK_COUNT_URL = "https://newexplorer.2x2coin.com/api/getblockcount"

    suspend fun getBlockCount(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(BLOCK_COUNT_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            connection.inputStream.bufferedReader().use { it.readText().trim().toInt() }
        }.getOrNull()
    }
}
