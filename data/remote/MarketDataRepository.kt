package com.portfolio.app.data.remote

import com.portfolio.app.data.local.AssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PriceQuote(val symbol:String,val price:Double,val currency:String,val source:String,val updatedAt:Long=System.currentTimeMillis())

class MarketDataRepository {
    suspend fun fetchQuote(asset:AssetEntity):PriceQuote? = withContext(Dispatchers.IO) {
        when(asset.priceSource.uppercase()) {
            "MANUAL" -> null
            "YAHOO_FINANCE" -> runCatching {
                when {
                    asset.category.equals("FX", true) || asset.ticker.uppercase() in setOf("USD", "EUR", "GBP", "JPY", "RUB") -> fetchYahoo(asset, true)
                    asset.category.equals("CRYPTO", true) && asset.marketSymbol.contains("/") -> fetchCrypto(asset)
                    asset.category.equals("COMMODITY", true) || asset.category.equals("GOLD", true) -> fetchMetal(asset)
                    else -> fetchYahoo(asset)
                }
            }.getOrNull()
            "COINGECKO" -> runCatching { fetchCrypto(asset) }.getOrNull()
            "FRANKFURTER" -> runCatching { fetchFx(asset) }.getOrNull()
            else -> runCatching {
                when(asset.category.uppercase()) {
                    "CRYPTO","KRIPTO" -> fetchCrypto(asset)
                    "FX","DOVIZ","DÖVİZ" -> fetchFx(asset)
                    "COMMODITY","GOLD" -> fetchMetal(asset)
                    else -> fetchYahoo(asset)
                }
            }.getOrNull()
        }
    }

    private suspend fun fetchYahoo(asset:AssetEntity, forceFxPair:Boolean=false):PriceQuote? {
        val rawSymbol=asset.marketSymbol.ifBlank {
            when(asset.category.uppercase()) {
                "STOCK","BIST","HISSE" -> asset.ticker.uppercase().removeSuffix(".IS")+".IS"
                else -> asset.ticker.uppercase()
            }
        }
        val isFx = forceFxPair || asset.category.equals("FX", true) ||
            (asset.ticker.uppercase() in setOf("USD", "EUR", "GBP", "JPY", "RUB") && asset.currency.equals("TRY", true))
        val symbol = if (isFx && !rawSymbol.endsWith("=X") && !rawSymbol.contains("=")) rawSymbol + asset.currency.uppercase() + "=X" else rawSymbol
        val meta=YahooFinanceApiService.instance.getStockPrice(symbol).chart?.result?.firstOrNull()?.meta?:return null
        val price=meta.regularMarketPrice?:return null
        val quoteCurrency=meta.currency?:asset.currency
        if(!quoteCurrency.equals(asset.currency,true)) return null
        return PriceQuote(symbol,price,quoteCurrency,"Yahoo Finance")
    }

    private suspend fun fetchMetal(asset:AssetEntity):PriceQuote? {
        val metalText = (asset.ticker + " " + asset.name).uppercase()
        val silver = metalText.contains("AG") || metalText.contains("GUMUS") || metalText.contains("SILVER")
        val symbol = if (silver) "SI=F" else "GC=F"
        val metalMeta = YahooFinanceApiService.instance.getStockPrice(symbol).chart?.result?.firstOrNull()?.meta ?: return null
        val ouncePrice = metalMeta.regularMarketPrice ?: return null
        val quoteCurrency = metalMeta.currency ?: "USD"
        val gramPrice = ouncePrice / 31.1034768
        val finalPrice = when (asset.currency.uppercase()) {
            "USD", "USDT" -> gramPrice
            "TRY" -> {
                val fxMeta = YahooFinanceApiService.instance.getStockPrice("USDTRY=X").chart?.result?.firstOrNull()?.meta ?: return null
                gramPrice * (fxMeta.regularMarketPrice ?: return null)
            }
            else -> return null
        }
        return PriceQuote(asset.marketSymbol.ifBlank { symbol }, finalPrice, asset.currency, "Yahoo Finance")
    }

    private suspend fun fetchCrypto(asset:AssetEntity):PriceQuote? {
        val marketBase = asset.marketSymbol.substringBefore("/").substringBefore("-").trim()
        val id=cryptoId(asset.ticker) ?: cryptoId(marketBase) ?: asset.marketSymbol.takeIf { it.isNotBlank() && !it.contains("/") }
            ?: return null
        val vs=if (asset.currency.equals("USDT", true)) "usd" else asset.currency.lowercase()
        val price=CoinGeckoApiService.instance.getCryptoPrices(id,vs)[id]?.get(vs)?:return null
        return PriceQuote(asset.ticker,price,asset.currency,"CoinGecko")
    }

    private suspend fun fetchFx(asset:AssetEntity):PriceQuote? {
        val pair=asset.marketSymbol.ifBlank{asset.ticker}.uppercase().replace("/","_").replace("-","_")
        val parts=pair.split("_");val base=parts.getOrNull(0)?:return null;val quote=parts.getOrNull(1)?:asset.currency
        if(!quote.equals(asset.currency,true)) return null
        val price=FrankfurterApiService.instance.getExchangeRates(base,quote).rates[quote]?:return null
        return PriceQuote(pair,price,quote,"Frankfurter")
    }

    private fun cryptoId(ticker:String):String?=when(ticker.trim().uppercase()) {
        "BTC","BTCUSDT","BITCOIN"->"bitcoin";"ETH","ETHUSDT","ETHEREUM"->"ethereum";"BNB","BINANCECOIN"->"binancecoin"
        "SOL"->"solana";"XRP","RIPPLE"->"ripple";"ADA"->"cardano";"DOGE","DOGECOIN"->"dogecoin"
        "AVAX"->"avalanche-2";"DOT"->"polkadot";"LINK"->"chainlink";"TRX","TRON"->"tron"
        "LTC"->"litecoin";"SHIB"->"shiba-inu";"TON"->"the-open-network";"USDT","TETHER"->"tether"
        "PAXG"->"pax-gold";"TWT"->"trust-wallet-token";"MANA"->"decentraland";"POL"->"polygon-ecosystem-token"
        "PEPE"->"pepe";"RVN"->"ravencoin";"NOT"->"notcoin";"FDUSD"->"first-digital-usd"
        "CHZ"->"chiliz";"BEL"->"bella-protocol";"CYBER"->"cyberconnect";"SLP"->"smooth-love-potion"
        "LUNA"->"terra-luna";"ZEC"->"zcash";"CRO"->"crypto-com-chain";"HMSTR"->"hamster-kombat"
        "SXP","SOLAR"->"solar";"DOGS"->"dogs-2";"GRAM"->"gram";else->null
    }
}
