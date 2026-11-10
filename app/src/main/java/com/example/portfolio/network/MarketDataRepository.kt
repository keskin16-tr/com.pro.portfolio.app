
package com.example.portfolio.network

import com.example.portfolio.model.Candle
import com.example.portfolio.model.PricePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MarketDataRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun get(url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PortfolioManager/2.1")
                    .build()

                client.newCall(request).execute().use {
                    if (!it.isSuccessful) return@withContext null
                    it.body?.string()
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun binancePrice(symbol: String): Double? {
        val json = get(
            "https://api.binance.com/api/v3/ticker/price?symbol=$symbol"
        ) ?: return null

        return try {
            JSONObject(json).getString("price").toDouble()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun yahooPrice(symbol: String): Double? {
        val json = get(
            "https://query1.finance.yahoo.com/v8/finance/chart/" +
                "$symbol?range=1d&interval=5m"
        ) ?: return null

        return try {
            JSONObject(json)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")
                .getDouble("regularMarketPrice")
        } catch (_: Exception) {
            null
        }
    }

    suspend fun binanceCandles(
        symbol: String,
        interval: String = "1d",
        limit: Int = 200
    ): List<Candle> {

        val safeIntervals = setOf(
            "1m", "5m", "15m", "30m",
            "1h", "4h", "1d", "1w", "1M"
        )

        if (interval !in safeIntervals || limit !in 1..1000) {
            return emptyList()
        }

        val json = get(
            "https://api.binance.com/api/v3/klines" +
                "?symbol=$symbol&interval=$interval&limit=$limit"
        ) ?: return emptyList()

        return try {
            val array = org.json.JSONArray(json)

            buildList {
                for (i in 0 until array.length()) {
                    val c = array.getJSONArray(i)

                    add(
                        Candle(
                            timestamp = c.getLong(0),
                            open = c.getString(1).toDouble(),
                            high = c.getString(2).toDouble(),
                            low = c.getString(3).toDouble(),
                            close = c.getString(4).toDouble(),
                            volume = c.getString(5).toDouble()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun yahooCandles(
        symbol: String,
        range: String = "1y",
        interval: String = "1d"
    ): List<Candle> {

        val safeRanges = setOf(
            "1d", "5d", "1mo", "3mo",
            "6mo", "1y", "2y", "5y", "10y", "max"
        )

        val safeIntervals = setOf(
            "1m", "5m", "15m", "30m",
            "1h", "1d", "1wk", "1mo"
        )

        if (range !in safeRanges || interval !in safeIntervals) {
            return emptyList()
        }

        val json = get(
            "https://query1.finance.yahoo.com/v8/finance/chart/" +
                "$symbol?range=$range&interval=$interval"
        ) ?: return emptyList()

        return try {
            val result = JSONObject(json)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)

            val timestamps = result.getJSONArray("timestamp")

            val quote = result.getJSONObject("indicators")
                .getJSONArray("quote")
                .getJSONObject(0)

            val opens = quote.getJSONArray("open")
            val highs = quote.getJSONArray("high")
            val lows = quote.getJSONArray("low")
            val closes = quote.getJSONArray("close")
            val volumes = quote.getJSONArray("volume")

            buildList {
                for (i in 0 until timestamps.length()) {
                    if (opens.isNull(i) ||
                        highs.isNull(i) ||
                        lows.isNull(i) ||
                        closes.isNull(i)
                    ) continue

                    add(
                        Candle(
                            timestamp = timestamps.getLong(i) * 1000,
                            open = opens.getDouble(i),
                            high = highs.getDouble(i),
                            low = lows.getDouble(i),
                            close = closes.getDouble(i),
                            volume = if (volumes.isNull(i)) {
                                0.0
                            } else {
                                volumes.getDouble(i)
                            }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun exchangeRate(
        base: String,
        target: String
    ): Double? {
        if (base == target) return 1.0

        val json = get(
            "https://api.frankfurter.dev/v1/latest" +
                "?base=$base&symbols=$target"
        ) ?: return null

        return try {
            JSONObject(json)
                .getJSONObject("rates")
                .getDouble(target)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun binanceHistory(
        symbol: String,
        interval: String = "1d",
        limit: Int = 200
    ): List<PricePoint> {
        return binanceCandles(symbol, interval, limit).map {
            PricePoint(it.timestamp, it.close)
        }
    }

    suspend fun yahooHistory(
        symbol: String,
        range: String = "1y",
        interval: String = "1d"
    ): List<PricePoint> {
        return yahooCandles(symbol, range, interval).map {
            PricePoint(it.timestamp, it.close)
        }
    }
}
