package com.portfolio.app.data.remote
import com.portfolio.app.data.local.AssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
data class PriceQuote(val symbol:String,val price:Double,val currency:String,val source:String,val updatedAt:Long=System.currentTimeMillis())
class MarketDataRepository{
 suspend fun fetchQuote(asset:AssetEntity):PriceQuote?=withContext(Dispatchers.IO){
  runCatching{when(asset.category.uppercase()){
   "STOCK","BIST","HISSE"->fetchYahoo(asset)
   "CRYPTO","KRIPTO"->fetchCrypto(asset)
   "FX","DOVIZ","DÖVİZ"->fetchFx(asset)
   else->fetchYahoo(asset)
  }}.getOrNull()
 }
 private suspend fun fetchYahoo(asset:AssetEntity):PriceQuote?{
  val symbol=asset.marketSymbol.ifBlank{if(asset.category.uppercase() in listOf("STOCK","BIST","HISSE")) asset.ticker.uppercase().removeSuffix(".IS")+".IS" else asset.ticker.uppercase()}
  val meta=YahooFinanceApiService.instance.getStockPrice(symbol).chart?.result?.firstOrNull()?.meta?:return null
  val price=meta.regularMarketPrice?:return null
  return PriceQuote(symbol,price,meta.currency?:asset.currency,"Yahoo Finance")
 }
 private suspend fun fetchCrypto(asset:AssetEntity):PriceQuote?{
  val id=cryptoId(asset.ticker)?:return null
  val price=CoinGeckoApiService.instance.getCryptoPrices(id,"try")[id]?.get("try")?:return null
  return PriceQuote(asset.ticker,price,"TRY","CoinGecko REST")
 }
 private suspend fun fetchFx(asset:AssetEntity):PriceQuote?{
  val pair=asset.marketSymbol.ifBlank{asset.ticker}.uppercase().replace("/","_").replace("-","_")
  val parts=pair.split("_");val base=parts.getOrNull(0)?:return null;val quote=parts.getOrNull(1)?:"TRY"
  val price=FrankfurterApiService.instance.getExchangeRates(base,quote).rates[quote]?:return null
  return PriceQuote(pair,price,quote,"Frankfurter")
 }
 private fun cryptoId(ticker:String):String?=when(ticker.uppercase()){
  "BTC","BTCUSDT"->"bitcoin";"ETH","ETHUSDT"->"ethereum";"BNB"->"binancecoin";"SOL"->"solana";"XRP"->"ripple";"ADA"->"cardano";"DOGE"->"dogecoin";"AVAX"->"avalanche-2";"DOT"->"polkadot";"LINK"->"chainlink";"TRX"->"tron";else->null
 }
}
