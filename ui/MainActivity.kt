package com.portfolio.app.ui.main
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.portfolio.app.R
import com.portfolio.app.databinding.ActivityMainBinding
import com.portfolio.app.ui.adapter.AssetAdapter
import com.portfolio.app.ui.dialog.AddAssetDialogFragment
import java.text.NumberFormat
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
  }}}
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.totalDividends.collect{binding.tvDividendTotal.text=currency(it)}}}
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
 private fun parseNumber(raw:String)=raw.trim().replace(" ","").replace(",",".").toDoubleOrNull()?.takeIf{it.isFinite()}
 private fun setupRecyclerView(){assetAdapter=AssetAdapter{asset->viewModel.deleteAsset(asset)};binding.recyclerViewAssets.apply{layoutManager=LinearLayoutManager(this@MainActivity);adapter=assetAdapter;isNestedScrollingEnabled=false}}
 private fun currency(value:Double)=NumberFormat.getCurrencyInstance(Locale("tr","TR")).format(value)
}
