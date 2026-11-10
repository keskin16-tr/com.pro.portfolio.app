
package com.example.portfolio.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PriceRepository {

    suspend fun getBinancePrice(symbol: String): Double? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://api.binance.com/api/v3/ticker/price?symbol=${
                        symbol.uppercase()
                    }"
                )

                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"

                if (connection.responseCode != 200) {
                    return@withContext null
                }

                val response = connection.inputStream
                    .bufferedReader().use { it.readText() }

                JSONObject(response).getString("price").toDouble()
            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun getYahooPrice(ticker: String): Double? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://query1.finance.yahoo.com/v8/finance/chart/" +
                        "${ticker}?range=1d&interval=1m"
                )

                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0"
                )

                if (connection.responseCode != 200) {
                    return@withContext null
                }

                val response = connection.inputStream
                    .bufferedReader().use { it.readText() }

                val result = JSONObject(response)
                    .getJSONObject("chart")
                    .getJSONArray("result")
                    .getJSONObject(0)

                val meta = result.getJSONObject("meta")
                meta.optDouble("regularMarketPrice", Double.NaN)
                    .takeIf { it.isFinite() && it > 0 }

            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun refreshCrypto(symbol: String): Double? {
        return getBinancePrice(symbol)
    }
}
