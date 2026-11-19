package com.portfolio.app.ui.main
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.data.remote.MarketDataRepository
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
 val assets:StateFlow<List<AssetEntity>> = repository.allAssets.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val trades:StateFlow<List<TradeEntity>> = repository.allTrades.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val tradeCount:StateFlow<Int> = repository.tradeCount.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),0)
 val dividends:StateFlow<List<DividendEntity>> = repository.allDividends.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val totalDividends:StateFlow<Double> = repository.totalNetDividends.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),0.0)
 init{
  viewModelScope.launch{
   while(isActive){
    database.assetDao().getAssetsOnce().forEach{asset->marketData.fetchQuote(asset)?.let{repository.updatePrice(asset.id,it.price,it.updatedAt)}}
    delay(60_000L)
   }
  }
 }
 fun addAsset(name:String,ticker:String,quantity:Double,costPrice:Double){viewModelScope.launch{runCatching{repository.recordBuy(name,ticker,quantity,costPrice)}}}
 fun addDividend(ticker:String,grossAmount:Double,taxPercent:Double){viewModelScope.launch{runCatching{repository.recordDividend(ticker,grossAmount,taxPercent)}}}
 fun sellAsset(assetId:Long,quantity:Double,unitPrice:Double,fee:Double,onResult:(Result<Unit>)->Unit){
  viewModelScope.launch{onResult(runCatching{repository.recordSell(assetId,quantity,unitPrice,fee)})}
 }
 fun deleteAsset(asset:AssetEntity){viewModelScope.launch{repository.delete(asset)}}
}