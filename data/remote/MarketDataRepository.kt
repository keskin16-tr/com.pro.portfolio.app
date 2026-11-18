package com.portfolio.app.data.remote

import com.portfolio.app.data.local.AssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PriceQuote(val symbol:String,val price:Double,val currency:String,val source:String,val updatedAt:Long=System.currentTimeMillis())

class MarketDataRepository {
    suspend fun fetchQuote(asset:AssetEntity):PriceQuote? = withContext(Dispatchers.IO) {
        when(asset.priceSource.uppercase()) {
            "MANUAL" -> null
            "YAHOO_FINANCE" -> runCatching { fetchYahoo(asset) }.getOrNull()
            "COINGECKO" -> runCatching { fetchCrypto(asset) }.getOrNull()
            "FRANKFURTER" -> runCatching { fetchFx(asset) }.getOrNull()
            else -> runCatching {
                when(asset.category.uppercase()) {
                    "CRYPTO","KRIPTO" -> fetchCrypto(asset)
                    "FX","DOVIZ","DÖVİZ" -> fetchFx(asset)
                    else -> fetchYahoo(asset)
                }
            }.getOrNull()
        }
    }

    private suspend fun fetchYahoo(asset:AssetEntity):PriceQuote? {
        val symbol=asset.marketSymbol.ifBlank {
            when(asset.category.uppercase()) {
                "STOCK","BIST","HISSE" -> asset.ticker.uppercase().removeSuffix(".IS")+".IS"
                else -> asset.ticker.uppercase()
            }
        }
        val meta=YahooFinanceApiService.instance.getStockPrice(symbol).chart?.result?.firstOrNull()?.meta?:return null
        val price=meta.regularMarketPrice?:return null
        return PriceQuote(symbol,price,meta.currency?:asset.currency,"Yahoo Finance")
    }

    private suspend fun fetchCrypto(asset:AssetEntity):PriceQuote? {
        val id=cryptoId(asset.ticker)?:return null
        val vs=asset.currency.lowercase()
        val price=CoinGeckoApiService.instance.getCryptoPrices(id,vs)[id]?.get(vs)?:return null
        return PriceQuote(asset.ticker,price,asset.currency,"CoinGecko")
    }

    private suspend fun fetchFx(asset:AssetEntity):PriceQuote? {
        val pair=asset.marketSymbol.ifBlank{asset.ticker}.uppercase().replace("/","_").replace("-","_")
        val parts=pair.split("_");val base=parts.getOrNull(0)?:return null;val quote=parts.getOrNull(1)?:asset.currency
        val price=FrankfurterApiService.instance.getExchangeRates(base,quote).rates[quote]?:return null
        return PriceQuote(pair,price,quote,"Frankfurter")
    }

    private fun cryptoId(ticker:String):String?=when(ticker.uppercase()) {
        "BTC","BTCUSDT"->"bitcoin";"ETH","ETHUSDT"->"ethereum";"BNB"->"binancecoin"
        "SOL"->"solana";"XRP"->"ripple";"ADA"->"cardano";"DOGE"->"dogecoin"
        "AVAX"->"avalanche-2";"DOT"->"polkadot";"LINK"->"chainlink";"TRX"->"tron"
        "LTC"->"litecoin";"SHIB"->"shiba-inu";"TON"->"the-open-network";else->null
    }
}
