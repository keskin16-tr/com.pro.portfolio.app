package com.portfolio.app.ui.adapter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.databinding.ItemAssetBinding
import java.text.NumberFormat
import java.util.Locale
class AssetAdapter(private val onDeleteClick:(AssetEntity)->Unit, private val onEditClick:(AssetEntity)->Unit):ListAdapter<AssetEntity,AssetAdapter.AssetViewHolder>(AssetDiffCallback()){
 inner class AssetViewHolder(private val binding:ItemAssetBinding):RecyclerView.ViewHolder(binding.root){
  fun bind(asset:AssetEntity){
   binding.tvAssetName.text=asset.name
   val visibleSymbol=asset.ticker.uppercase(Locale.ROOT).ifBlank { "Sembol yok" }
   binding.tvAssetTicker.text="$visibleSymbol • ${categoryLabel(asset.category)} • ${asset.broker}"
   binding.tvAssetQuantity.text="Miktar: "+formatNumber(asset.quantity)
   binding.tvAssetCost.text="Ort. maliyet: "+currency(asset.costPrice,asset.currency)
   val marketValue=asset.lastPrice?.times(asset.quantity)
   val invested=asset.quantity*asset.costPrice
   binding.tvTotalCost.text=if(marketValue!=null) "Değer "+currency(marketValue,asset.currency)+" • K/Z "+currency(marketValue-invested,asset.currency) else "Maliyet "+currency(invested,asset.currency)+" • Piyasa verisi bekleniyor"
   binding.btnDelete.setOnClickListener{onDeleteClick(asset)}
   binding.btnEditAsset.setOnClickListener{onEditClick(asset)}
  }
 }
 override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):AssetViewHolder=AssetViewHolder(ItemAssetBinding.inflate(LayoutInflater.from(parent.context),parent,false))
 override fun onBindViewHolder(holder:AssetViewHolder,position:Int)=holder.bind(getItem(position))
 private fun categoryLabel(value:String)=when(value.uppercase()){
  "STOCK","BIST","HISSE"->"Hisse"
  "US_STOCK"->"ABD Hisse / ETF"
  "FUND"->"Fon"
  "CRYPTO"->"Kripto"
  "FX"->"Döviz"
  "GOLD"->"Altın"
  "COMMODITY"->"Emtia"
  "BOND"->"Tahvil"
  "BANK"->"Banka"
  "CASH"->"Nakit"
  else->"Diğer"
 }
 private fun currency(value:Double,code:String):String {
  val unit=runCatching{java.util.Currency.getInstance(code.uppercase(Locale.ROOT))}.getOrNull()
  return if(unit!=null) NumberFormat.getCurrencyInstance(Locale("tr","TR")).apply{currency=unit}.format(value)
  else code.uppercase(Locale.ROOT)+" "+NumberFormat.getNumberInstance(Locale("tr","TR")).format(value)
 }
 private fun formatNumber(value:Double)=NumberFormat.getNumberInstance(Locale("tr","TR")).format(value)
 class AssetDiffCallback:DiffUtil.ItemCallback<AssetEntity>(){
  override fun areItemsTheSame(oldItem:AssetEntity,newItem:AssetEntity)=oldItem.id==newItem.id
  override fun areContentsTheSame(oldItem:AssetEntity,newItem:AssetEntity)=oldItem==newItem
 }
}
