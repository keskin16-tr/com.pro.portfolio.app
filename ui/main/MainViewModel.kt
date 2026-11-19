package com.portfolio.app.ui.main
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.data.local.PortfolioSnapshotEntity
import com.portfolio.app.data.local.AssetPriceSnapshotEntity
import com.portfolio.app.data.importer.ImportedHolding
import com.portfolio.app.data.remote.MarketDataRepository
import com.portfolio.app.data.remote.MarketRatesRepository
import com.portfolio.app.data.remote.MarketRate
import com.portfolio.app.data.repository.PortfolioRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
class MainViewModel(application:Application):AndroidViewModel(application){
 private val database=AppDatabase.getDatabase(application)
 private val repository=PortfolioRepository(database)
 private val marketData=MarketDataRepository()
 private val marketRates=MarketRatesRepository()
 private val _rates= kotlinx.coroutines.flow.MutableStateFlow<List<MarketRate>>(emptyList())
 val rates:StateFlow<List<MarketRate>> = _rates
 private val _priceSnapshots=kotlinx.coroutines.flow.MutableStateFlow<List<AssetPriceSnapshotEntity>>(emptyList())
 val priceSnapshots:StateFlow<List<AssetPriceSnapshotEntity>> = _priceSnapshots
 private val _dailyPnlTry=kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)
 val dailyPnlTry:StateFlow<Double?> = _dailyPnlTry
 private val _dailyPnlUsd=kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)
 val dailyPnlUsd:StateFlow<Double?> = _dailyPnlUsd
 val assets:StateFlow<List<AssetEntity>> = repository.allAssets.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val trades:StateFlow<List<TradeEntity>> = repository.allTrades.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val tradeCount:StateFlow<Int> = repository.tradeCount.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),0)
 val dividends:StateFlow<List<DividendEntity>> = repository.allDividends.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val totalDividends:StateFlow<Double> = repository.totalNetDividends.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),0.0)
 val snapshots:StateFlow<List<PortfolioSnapshotEntity>> = repository.allSnapshots.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 init{
  viewModelScope.launch{
   while(isActive){
    val latestAssets=runCatching { database.assetDao().getAssetsOnce() }.getOrDefault(emptyList())
    latestAssets.forEach{asset->
     val quote=runCatching { marketData.fetchQuote(asset) }.getOrNull()
     if(quote!=null) runCatching { repository.updatePrice(asset.id,quote.price,quote.updatedAt) }
     else if(asset.priceSource=="MANUAL") runCatching { repository.recordCurrentPriceSnapshot(asset.id) }
    }
    runCatching { repository.recordDailySnapshot() }
    runCatching { _priceSnapshots.value = database.assetPriceSnapshotDao().getAll() }
    _dailyPnlTry.value=runCatching{repository.dailyProfitLoss("TRY")}.getOrNull()
    _dailyPnlUsd.value=runCatching{repository.dailyProfitLoss("USD")}.getOrNull()
    runCatching{_rates.value=marketRates.fetchRates()}
    delay(60_000L)
   }
  }
 }
 fun refreshRates(){viewModelScope.launch{runCatching{_rates.value=marketRates.fetchRates()}}}
 fun addAsset(name:String,ticker:String,quantity:Double,costPrice:Double,category:String,broker:String,currency:String,priceSource:String,manualPrice:Double?){viewModelScope.launch{runCatching{repository.recordBuy(name,ticker,quantity,costPrice,category=category,currency=currency,broker=broker,priceSource=priceSource,manualPrice=manualPrice)}}}
 fun importHoldings(rows:List<ImportedHolding>,onResult:(Result<Pair<Int,Int>>)->Unit){viewModelScope.launch{onResult(runCatching{repository.importHoldings(rows)})}}
 fun classifyAsset(assetId:Long,category:String,broker:String,onResult:(Result<Unit>)->Unit){viewModelScope.launch{onResult(runCatching{repository.classifyAsset(assetId,category,broker)})}}
 fun addDividend(ticker:String,grossAmount:Double,taxPercent:Double,currency:String){viewModelScope.launch{runCatching{repository.recordDividend(ticker,grossAmount,taxPercent,currency)}}}
 fun sellAsset(assetId:Long,quantity:Double,unitPrice:Double,fee:Double,onResult:(Result<Unit>)->Unit){
  viewModelScope.launch{onResult(runCatching{repository.recordSell(assetId,quantity,unitPrice,fee)})}
 }
 fun updateAssetDetails(assetId:Long,name:String,ticker:String,category:String,broker:String,quantity:Double?,costPrice:Double?,currency:String,priceSource:String,manualPrice:Double?,onResult:(Result<Unit>)->Unit){viewModelScope.launch{onResult(runCatching{repository.updateAssetDetails(assetId,name,ticker,category,broker,quantity,costPrice,currency,priceSource,manualPrice)})}}
 fun editTrade(tradeId:Long,action:String,quantity:Double,unitPrice:Double,fee:Double,note:String,executedAt:Long,onResult:(Result<Unit>)->Unit){viewModelScope.launch{onResult(runCatching{repository.editTrade(tradeId,action,quantity,unitPrice,fee,note,executedAt)})}}
 fun deleteTrade(tradeId:Long,onResult:(Result<Unit>)->Unit){viewModelScope.launch{onResult(runCatching{repository.deleteTrade(tradeId)})}}
 fun deleteAsset(asset:AssetEntity){viewModelScope.launch{repository.delete(asset)}}
}