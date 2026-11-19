package com.portfolio.app.ui.main
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.portfolio.app.R
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.databinding.ActivityMainBinding
import com.portfolio.app.ui.adapter.AssetAdapter
import com.portfolio.app.ui.dialog.AddAssetDialogFragment
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
class MainActivity:AppCompatActivity(){
 private lateinit var binding:ActivityMainBinding
 private val viewModel:MainViewModel by viewModels()
 private lateinit var assetAdapter:AssetAdapter
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);binding=ActivityMainBinding.inflate(layoutInflater);setContentView(binding.root);setupRecyclerView()
  binding.fabAddAsset.setOnClickListener{AddAssetDialogFragment{name,ticker,quantity,costPrice->viewModel.addAsset(name,ticker,quantity,costPrice)}.show(supportFragmentManager,"AddAssetDialog")}
  binding.btnAddDividend.setOnClickListener{showDividendDialog()}
  binding.btnSellAsset.setOnClickListener{showSellDialog()}
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.assets.collect{assets->
   assetAdapter.submitList(assets)
   val invested=assets.sumOf{it.quantity*it.costPrice}
   val marketValue=assets.sumOf{(it.lastPrice?:it.costPrice)*it.quantity}
   val profit=marketValue-invested
   binding.tvTotalValue.text=currency(marketValue)
   binding.tvAssetCount.text=getString(R.string.asset_count,assets.size)
   binding.tvHoldingsCount.text=assets.size.toString()+" kayıt"
   binding.tvEmptyState.visibility=if(assets.isEmpty())View.VISIBLE else View.GONE
   binding.tvChartEmpty.visibility=if(assets.isEmpty())View.VISIBLE else View.GONE
   binding.portfolioChart.setValues(assets.map{((it.lastPrice?:it.costPrice)*it.quantity).toFloat()})
   binding.tvHeaderTitle.contentDescription="Portföy toplam kâr zarar: "+currency(profit)
   binding.btnSellAsset.isEnabled=assets.any{it.quantity>0.0}
  }}}
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.totalDividends.collect{binding.tvDividendTotal.text=currency(it)}}}
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.trades.collect{renderTrades(it,viewModel.assets.value)}}}
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.assets.collect{renderTrades(viewModel.trades.value,it)}}}
 }
 private fun showSellDialog(){
  val available=viewModel.assets.value.filter{it.quantity>0.0}
  if(available.isEmpty()){Toast.makeText(this,"Satış yapmak için portföyde varlık bulunmalı.",Toast.LENGTH_LONG).show();return}
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(56,12,56,0)}
  val spinner=Spinner(this)
  val labels=available.map{"${it.ticker} — ${formatNumber(it.quantity)} adet"}
  spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
  fun field(hint:String)=EditText(this).apply{
   this.hint=hint
   inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
   layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{bottomMargin=10}
  }
  val quantity=field("Satılacak miktar")
  val price=field("Birim satış fiyatı (TL)")
  val fee=field("Komisyon / masraf (TL) — varsayılan 0").apply{setText("0")}
  box.addView(spinner);box.addView(quantity);box.addView(price);box.addView(fee)
  AlertDialog.Builder(this).setTitle("Varlık satışı").setView(box)
   .setNegativeButton("İptal",null)
   .setPositiveButton("Satışı kaydet"){_,_->
    val asset=available[spinner.selectedItemPosition.coerceIn(0,available.lastIndex)]
    val qty=parseNumber(quantity.text.toString())
    val unitPrice=parseNumber(price.text.toString())
    val commission=parseNumber(fee.text.toString())?:0.0
    if(qty==null||qty<=0.0||unitPrice==null||unitPrice<0.0||commission<0.0){
     Toast.makeText(this,"Geçerli miktar, satış fiyatı ve komisyon girin.",Toast.LENGTH_LONG).show()
    }else{
     viewModel.sellAsset(asset.id,qty,unitPrice,commission){result->
      runOnUiThread{
       result.onSuccess{Toast.makeText(this,"Satış kaydedildi. Gerçekleşen kâr/zarar hesaplandı.",Toast.LENGTH_LONG).show()}
        .onFailure{Toast.makeText(this,it.message?: "Satış kaydedilemedi.",Toast.LENGTH_LONG).show()}
      }
     }
    }
   }.show()
 }
 private fun showDividendDialog(){
  val container=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(56,12,56,0)}
  fun field(hint:String,decimal:Boolean=false)=EditText(this).apply{
   this.hint=hint
   inputType=if(decimal)android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
   layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{bottomMargin=12}
  }
  val ticker=field("Hisse sembolü (ör. THYAO)")
  val gross=field("Brüt temettü tutarı (TL)",true)
  val tax=field("Stopaj oranı (%) — varsayılan 0",true).apply{setText("0")}
  container.addView(ticker);container.addView(gross);container.addView(tax)
  AlertDialog.Builder(this).setTitle("Temettü kaydı").setView(container)
   .setNegativeButton("İptal",null)
   .setPositiveButton("Kaydet"){_,_->
    val symbol=ticker.text.toString().trim().uppercase(Locale.ROOT)
    val amount=parseNumber(gross.text.toString())
    val taxRate=parseNumber(tax.text.toString())?:0.0
    if(symbol.isBlank()||amount==null||amount<=0.0||taxRate !in 0.0..100.0){
     Toast.makeText(this,"Sembol, pozitif brüt tutar ve %0–100 stopaj girin.",Toast.LENGTH_LONG).show()
    }else{
     viewModel.addDividend(symbol,amount,taxRate)
     Toast.makeText(this,"Temettü kaydı işleniyor.",Toast.LENGTH_SHORT).show()
    }
   }.show()
 }
 private fun renderTrades(trades:List<TradeEntity>,assets:List<AssetEntity>){
  if(trades.isEmpty()){binding.tvTradeHistory.text="Henüz işlem bulunmuyor.";return}
  val symbols=assets.associateBy{it.id}
  val dateFormat=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR"))
  binding.tvTradeHistory.text=trades.take(12).joinToString("\n\n"){trade->
   val symbol=symbols[trade.assetId]?.ticker?: "Varlık #${trade.assetId}"
   val type=if(trade.action=="SELL")"SATIŞ" else "ALIŞ"
   val amount=trade.quantity*trade.unitPrice
   val result=if(trade.action=="SELL")"\nGerçekleşen K/Z: ${currency(trade.realizedProfit)}" else ""
   "${dateFormat.format(Date(trade.executedAt))}  •  $type $symbol\n${formatNumber(trade.quantity)} × ${currency(trade.unitPrice)} = ${currency(amount)}\nKomisyon: ${currency(trade.fee)}$result"
  }
 }
 private fun parseNumber(raw:String)=raw.trim().replace(" ","").replace(",",".").toDoubleOrNull()?.takeIf{it.isFinite()}
 private fun formatNumber(value:Double)=NumberFormat.getNumberInstance(Locale("tr","TR")).format(value)
 private fun setupRecyclerView(){assetAdapter=AssetAdapter{asset->viewModel.deleteAsset(asset)};binding.recyclerViewAssets.apply{layoutManager=LinearLayoutManager(this@MainActivity);adapter=assetAdapter;isNestedScrollingEnabled=false}}
 private fun currency(value:Double)=NumberFormat.getCurrencyInstance(Locale("tr","TR")).format(value)
}
