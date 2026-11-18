package com.portfolio.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class MarketQuote(val price: Double, val previousClose: Double?, val currency: String, val source: String) {
    val changePercent: Double? get() = previousClose?.takeIf { it != 0.0 }?.let { (price - it) / it * 100.0 }
}

class MarketDataRepository {
    suspend fun quote(ticker: String, category: String): Result<MarketQuote> = withContext(Dispatchers.IO) {
        runCatching {
            val key = ticker.trim().uppercase(Locale.ROOT)
            when (category.uppercase(Locale.ROOT)) {
                "FX", "DÖVİZ", "DOVIZ" -> {
                    val base = when (key) { "USD", "USDTRY" -> "USD"; "EUR", "EURTRY" -> "EUR"; "GBP", "GBPTRY" -> "GBP"; else -> key }
                    val response = FrankfurterApiService.instance.getExchangeRates(base, "TRY")
                    MarketQuote(response.rates["TRY"] ?: error("Kur bulunamadı"), null, "TRY", "Frankfurter")
                }
                "CRYPTO", "KRİPTO", "KRIPTO" -> {
                    val id = when (key) { "BTC", "BITCOIN" -> "bitcoin"; "ETH", "ETHEREUM" -> "ethereum"; "SOL" -> "solana"; "BNB" -> "binancecoin"; else -> key.lowercase(Locale.ROOT) }
                    val response = CoinGeckoApiService.instance.getCryptoPrices(id, "try")
                    MarketQuote(response[id]?.get("try") ?: error("Kripto fiyatı bulunamadı"), null, "TRY", "CoinGecko")
                }
                "GOLD", "ALTIN" -> {
                    val result = YahooFinanceApiService.instance.getStockPrice("GC=F").chart?.result?.firstOrNull()?.meta
                    val ounceUsd = result?.regularMarketPrice ?: error("Altın fiyatı bulunamadı")
                    val fx = FrankfurterApiService.instance.getExchangeRates("USD", "TRY").rates["TRY"] ?: error("USD/TRY kuru bulunamadı")
                    MarketQuote(ounceUsd * fx / 31.1034768, null, "TRY", "Yahoo Finance + Frankfurter")
                }
                else -> {
                    val symbol = when {
                        key.endsWith(".IS") -> key
                        key.matches(Regex("[A-Z0-9]{3,6}")) -> "$key.IS"
                        else -> key
                    }
                    val meta = YahooFinanceApiService.instance.getStockPrice(symbol).chart?.result?.firstOrNull()?.meta
                        ?: error("Piyasa verisi bulunamadı")
                    MarketQuote(meta.regularMarketPrice ?: error("Fiyat yok"), meta.previousClose, meta.currency ?: "TRY", "Yahoo Finance")
                }
            }
        }
    }
}