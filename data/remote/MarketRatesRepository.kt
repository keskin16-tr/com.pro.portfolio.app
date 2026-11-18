package com.portfolio.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MarketRate(val title:String,val value:Double,val currency:String,val dailyChangePercent:Double?,val source:String,val updatedAt:Long)
class MarketRatesRepository {
    suspend fun fetchRates():List<MarketRate> = withContext(Dispatchers.IO) {
        val service=YahooFinanceApiService.instance
        suspend fun meta(symbol:String)=service.getStockPrice(symbol).chart?.result?.firstOrNull()?.meta
        val usd=runCatching{meta("USDTRY=X")}.getOrNull()
        val eur=runCatching{meta("EURTRY=X")}.getOrNull()
        val gold=runCatching{meta("GC=F")}.getOrNull()
        val silver=runCatching{meta("SI=F")}.getOrNull()
        val usdNow=usd?.regularMarketPrice
        val eurNow=eur?.regularMarketPrice
        val usdPrev=usd?.previousClose
        val eurPrev=eur?.previousClose
        val goldNow=gold?.regularMarketPrice
        val goldPrev=gold?.previousClose
        val silverNow=silver?.regularMarketPrice
        val silverPrev=silver?.previousClose
        val now=System.currentTimeMillis()
        val rows=mutableListOf<MarketRate>()
        if(usdNow!=null) rows += MarketRate("Dolar / TL",usdNow,"TRY",percent(usdNow,usdPrev),"Yahoo Finance",now)
        if(eurNow!=null) rows += MarketRate("Euro / TL",eurNow,"TRY",percent(eurNow,eurPrev),"Yahoo Finance",now)
        if(usdNow!=null&&goldNow!=null) {
            val gram=goldNow*usdNow/31.1034768
            val previous=if(goldPrev!=null&&usdPrev!=null) goldPrev*usdPrev/31.1034768 else null
            rows += MarketRate("Gram Altın",gram,"TRY",percent(gram,previous),"Yahoo Finance • GC=F + USD/TRY",now)
        }
        if(usdNow!=null&&silverNow!=null) {
            val gram=silverNow*usdNow/31.1034768
            val previous=if(silverPrev!=null&&usdPrev!=null) silverPrev*usdPrev/31.1034768 else null
            rows += MarketRate("Gram Gümüş",gram,"TRY",percent(gram,previous),"Yahoo Finance • SI=F + USD/TRY",now)
        }
        rows
    }
    private fun percent(current:Double,previous:Double?):Double? =
        if(previous==null||previous==0.0) null else (current/previous-1.0)*100.0
}
