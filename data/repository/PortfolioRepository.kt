package com.portfolio.app.data.repository
import androidx.room.withTransaction
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.PortfolioSnapshotEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
class PortfolioRepository(private val database:AppDatabase){
 private val assetDao=database.assetDao()
 private val tradeDao=database.tradeDao()
 private val dividendDao=database.dividendDao()
 private val snapshotDao=database.portfolioSnapshotDao()
 val allAssets:Flow<List<AssetEntity>> = assetDao.getAllAssets()
 val allTrades:Flow<List<TradeEntity>> = tradeDao.getAll()
 val tradeCount:Flow<Int> = tradeDao.count()
 val allDividends:Flow<List<DividendEntity>> = dividendDao.getAll()
 val totalNetDividends:Flow<Double> = dividendDao.totalNet()
 val allSnapshots:Flow<List<PortfolioSnapshotEntity>> = snapshotDao.getAll()
 suspend fun recordBuy(name:String,ticker:String,quantity:Double,unitPrice:Double,category:String="OTHER",currency:String="TRY",fee:Double=0.0){
  require(quantity>0.0&&unitPrice>=0.0&&fee>=0.0)
  database.withTransaction{
   val existing=assetDao.getAssetsOnce().firstOrNull{it.ticker.equals(ticker,ignoreCase=true)}
   val assetId=existing?.id?:assetDao.insertAsset(AssetEntity(name=name.trim(),ticker=ticker.trim().uppercase(),quantity=0.0,costPrice=0.0,category=category,currency=currency,marketSymbol=defaultMarketSymbol(ticker,category)))
   val current=assetDao.getById(assetId)?:error("Varlık kaydı oluşturulamadı")
   val newQuantity=current.quantity+quantity
   val averageCost=(current.quantity*current.costPrice+quantity*unitPrice+fee)/newQuantity
   tradeDao.insert(TradeEntity(assetId=assetId,action="BUY",quantity=quantity,unitPrice=unitPrice,fee=fee,note="Alış işlemi"))
   assetDao.updatePosition(assetId,newQuantity,averageCost)
  }
 }
 suspend fun recordSell(assetId:Long,quantity:Double,unitPrice:Double,fee:Double=0.0){
  require(quantity>0.0&&unitPrice>=0.0&&fee>=0.0)
  database.withTransaction{
   val asset=assetDao.getById(assetId)?:error("Varlık bulunamadı")
   require(quantity<=asset.quantity){"Satış miktarı mevcut bakiyeden fazla olamaz."}
   val realized=(unitPrice-asset.costPrice)*quantity-fee
   tradeDao.insert(TradeEntity(assetId=assetId,action="SELL",quantity=quantity,unitPrice=unitPrice,fee=fee,realizedProfit=realized,note="Satış işlemi"))
   assetDao.updatePosition(assetId,asset.quantity-quantity,asset.costPrice)
  }
 }
 suspend fun recordDividend(ticker:String,grossAmount:Double,taxPercent:Double=0.0,paymentDate:Long=System.currentTimeMillis(),note:String=""){
  require(grossAmount>0.0&&taxPercent in 0.0..100.0)
  database.withTransaction{
   val asset=assetDao.getAssetsOnce().firstOrNull{it.ticker.equals(ticker.trim(),true)}?:error("Bu sembole ait portföy varlığı bulunamadı")
   val tax=grossAmount*taxPercent/100.0
   dividendDao.insert(DividendEntity(assetId=asset.id,ticker=asset.ticker,quantityAtPayment=asset.quantity,grossAmount=grossAmount,withholdingTax=tax,netAmount=grossAmount-tax,currency=asset.currency,exDate=paymentDate,paymentDate=paymentDate,note=note))
  }
 }

 suspend fun recordDailySnapshot(){
  val assets=assetDao.getAssetsOnce()
  if(assets.isEmpty()) return
  val calendar=Calendar.getInstance().apply {
   set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
  }
  val day=calendar.timeInMillis
  if((snapshotDao.latestDate()?:Long.MIN_VALUE)>=day) return
  val marketValue=assets.sumOf{it.quantity*(it.lastPrice?:it.costPrice)}
  val investedValue=assets.sumOf{it.quantity*it.costPrice}
  snapshotDao.insert(PortfolioSnapshotEntity(day,marketValue,investedValue,assets.size))
 }
 suspend fun delete(asset:AssetEntity){assetDao.deleteAsset(asset)}
 suspend fun updatePrice(assetId:Long,price:Double,updatedAt:Long=System.currentTimeMillis()){
  if(price.isFinite()&&price>=0.0)assetDao.updatePrice(assetId,price,updatedAt)
 }
 private fun defaultMarketSymbol(ticker:String,category:String):String{
  val symbol=ticker.trim().uppercase()
  return if(category.uppercase() in listOf("STOCK","BIST","HISSE")&&!symbol.endsWith(".IS"))symbol+".IS" else symbol
 }
}
