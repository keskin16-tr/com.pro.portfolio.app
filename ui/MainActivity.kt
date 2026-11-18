package com.portfolio.app.ui.main

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.portfolio.app.R
import com.portfolio.app.data.export.ExcelExporter
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.databinding.ActivityMainBinding
import com.portfolio.app.ui.adapter.AssetAdapter
import com.portfolio.app.ui.dialog.AddAssetDialogFragment
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var assetAdapter: AssetAdapter
    private lateinit var dashboardView: View
    private lateinit var bottomNavigation: BottomNavigationView
    private val pageViews = mutableMapOf<Int, View>()
    private val pageTexts = mutableMapOf<Int, TextView>()
    private var currentAssets: List<AssetEntity> = emptyList()
    private var currentTrades: List<TradeEntity> = emptyList()
    private var currentDividends: List<DividendEntity> = emptyList()

    private val createExcelDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? -> writeExcel(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dashboardView = binding.root.getChildAt(0)
        setupRecyclerView()
        setupNavigation()

        binding.fabAddAsset.setOnClickListener {
            AddAssetDialogFragment { name, ticker, quantity, costPrice ->
                viewModel.addAsset(name, ticker, quantity, costPrice)
            }.show(supportFragmentManager, "AddAssetDialog")
        }
        binding.btnAddDividend.setOnClickListener { showDividendDialog() }
        binding.btnSellAsset.setOnClickListener { showSellDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assets.collect { assets ->
                    currentAssets = assets
                    assetAdapter.submitList(assets)
                    val invested = assets.sumOf { it.quantity * it.costPrice }
                    val marketValue = assets.sumOf { (it.lastPrice ?: it.costPrice) * it.quantity }
                    binding.tvTotalValue.text = currency(marketValue)
                    binding.tvAssetCount.text = getString(R.string.asset_count, assets.size)
                    binding.tvHoldingsCount.text = "${assets.size} kayıt"
                    binding.tvEmptyState.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvChartEmpty.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    binding.portfolioChart.setValues(assets.map { ((it.lastPrice ?: it.costPrice) * it.quantity).toFloat() })
                    binding.tvHeaderTitle.contentDescription = "Portföy kâr/zarar: ${currency(marketValue - invested)}"
                    binding.btnSellAsset.isEnabled = assets.any { it.quantity > 0.0 }
                    updatePageContent()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalDividends.collect { binding.tvDividendTotal.text = currency(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trades.collect { currentTrades = it; renderTrades(it, currentAssets); updatePageContent() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dividends.collect { currentDividends = it; updatePageContent() }
            }
        }
    }

    private fun setupNavigation() {
        val root = binding.root
        val pages = listOf(NAV_ASSETS, NAV_TRADES, NAV_DIVIDENDS, NAV_EXPORT)
        pages.forEach { id ->
            val page = ScrollView(this).apply {
                visibility = View.GONE
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_navy))
                isFillViewport = true
                clipToPadding = false
                setPadding(dp(20), dp(24), dp(20), dp(24))
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(this).apply {
                text = pageTitle(id)
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
            }
            val body = TextView(this).apply {
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                setPadding(0, dp(18), 0, dp(18))
                setLineSpacing(dp(5).toFloat(), 1f)
            }
            content.addView(title)
            content.addView(body)
            if (id == NAV_EXPORT) {
                val exportButton = Button(this).apply {
                    text = "Excel dosyasını oluştur (.xlsx)"
                    setOnClickListener { createExcelDocument.launch("PortfolioPro_Rapor.xlsx") }
                }
                content.addView(exportButton)
                val csvButton = Button(this).apply {
                    text = "CSV olarak dışa aktar"
                    setOnClickListener { createExcelDocument.launch("PortfolioPro_Rapor.xlsx") }
                }
                // Excel çalışma kitabı üç ayrı sayfa içerir; CSV dışa aktarımı ayrıca sonraki sürümde eklenecek.
                content.addView(csvButton)
            }
            page.addView(content)
            root.addView(page, CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.TOP; bottomMargin = dp(76) })
            pageViews[id] = page
            pageTexts[id] = body
        }

        bottomNavigation = BottomNavigationView(this).apply {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
            menu.add(0, NAV_HOME, 0, "Özet").setIcon(android.R.drawable.ic_menu_view)
            menu.add(0, NAV_ASSETS, 1, "Portföy").setIcon(android.R.drawable.ic_menu_agenda)
            menu.add(0, NAV_TRADES, 2, "İşlemler").setIcon(android.R.drawable.ic_menu_sort_by_size)
            menu.add(0, NAV_DIVIDENDS, 3, "Temettü").setIcon(android.R.drawable.ic_menu_my_calendar)
            menu.add(0, NAV_EXPORT, 4, "Rapor").setIcon(android.R.drawable.ic_menu_save)
            setOnItemSelectedListener { item ->
                showPage(item.itemId)
                true
            }
        }
        root.addView(bottomNavigation, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, dp(76)
        ).apply { gravity = Gravity.BOTTOM })
        bottomNavigation.selectedItemId = NAV_HOME
        updatePageContent()
    }

    private fun showPage(id: Int) {
        dashboardView.visibility = if (id == NAV_HOME) View.VISIBLE else View.GONE
        pageViews.forEach { (pageId, view) -> view.visibility = if (pageId == id) View.VISIBLE else View.GONE }
        binding.fabAddAsset.visibility = if (id == NAV_HOME || id == NAV_ASSETS) View.VISIBLE else View.GONE
        updatePageContent()
    }

    private fun updatePageContent() {
        pageTexts[NAV_ASSETS]?.text = if (currentAssets.isEmpty()) {
            "Henüz portföy varlığı eklenmedi."
        } else currentAssets.joinToString("\n\n") { a ->
            val price = a.lastPrice ?: a.costPrice
            val value = a.quantity * price
            val pnl = (price - a.costPrice) * a.quantity
            "${a.ticker} — ${a.name}\nAdet: ${formatNumber(a.quantity)}   •   Ortalama maliyet: ${currency(a.costPrice)}\nSon fiyat: ${currency(price)}   •   Piyasa değeri: ${currency(value)}\nKâr/Zarar: ${currency(pnl)}"
        }
        pageTexts[NAV_TRADES]?.text = if (currentTrades.isEmpty()) {
            "Henüz alış veya satış işlemi bulunmuyor."
        } else currentTrades.joinToString("\n\n") { t ->
            val symbol = currentAssets.firstOrNull { it.id == t.assetId }?.ticker ?: "Varlık #${t.assetId}"
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(t.executedAt))
            "${if (t.action == "SELL") "SATIŞ" else "ALIŞ"} • $symbol\n$date\nMiktar: ${formatNumber(t.quantity)} × ${currency(t.unitPrice)}\nKomisyon: ${currency(t.fee)}" +
                if (t.action == "SELL") "\nGerçekleşen K/Z: ${currency(t.realizedProfit)}" else ""
        }
        pageTexts[NAV_DIVIDENDS]?.text = if (currentDividends.isEmpty()) {
            "Henüz temettü kaydı bulunmuyor."
        } else currentDividends.joinToString("\n\n") { d ->
            val date = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(d.paymentDate))
            "${d.ticker} • $date\nBrüt: ${currency(d.grossAmount)}   Stopaj: ${currency(d.withholdingTax)}\nNet temettü: ${currency(d.netAmount)}"
        }
        pageTexts[NAV_EXPORT]?.text =
            "Rapor dosyası Android'in dosya kaydetme penceresiyle seçtiğiniz konuma kaydedilir.\n\nExcel çalışma kitabı:\n• Portföy: adet, ortalama maliyet, son fiyat, piyasa değeri ve kâr/zarar\n• İşlemler: alış/satış, komisyon ve gerçekleşen kâr/zarar\n• Temettüler: brüt, stopaj ve net tutar\n\nKayıtlar cihazınızdaki yerel veritabanından alınır; internet gerekmez."
    }

    private fun writeExcel(uri: Uri?) {
        if (uri == null) return
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                ExcelExporter.write(output, currentAssets, currentTrades, currentDividends)
            } ?: error("Dosya yazma akışı açılamadı.")
            Toast.makeText(this, "Excel raporu kaydedildi.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Excel oluşturulamadı: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pageTitle(id: Int) = when (id) {
        NAV_ASSETS -> "Portföyüm"
        NAV_TRADES -> "İşlem geçmişi"
        NAV_DIVIDENDS -> "Temettüler"
        else -> "Raporlar ve dışa aktarma"
    }

    private fun showSellDialog() {
        val available = viewModel.assets.value.filter { it.quantity > 0.0 }
        if (available.isEmpty()) {
            Toast.makeText(this, "Satış yapmak için portföyde varlık bulunmalı.", Toast.LENGTH_LONG).show()
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(56, 12, 56, 0) }
        val spinner = android.widget.Spinner(this)
        spinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            available.map { "${it.ticker} — ${formatNumber(it.quantity)} adet" })
        fun field(hintText: String) = android.widget.EditText(this).apply {
            hint = hintText
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val quantity = field("Satılacak miktar")
        val price = field("Birim satış fiyatı (TL)")
        val fee = field("Komisyon / masraf (TL)").apply { setText("0") }
        box.addView(spinner); box.addView(quantity); box.addView(price); box.addView(fee)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Varlık satışı").setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Satışı kaydet") { _, _ ->
                val asset = available[spinner.selectedItemPosition.coerceIn(0, available.lastIndex)]
                val qty = parseNumber(quantity.text.toString())
                val unitPrice = parseNumber(price.text.toString())
                val commission = parseNumber(fee.text.toString()) ?: 0.0
                if (qty == null || qty <= 0.0 || unitPrice == null || unitPrice < 0.0 || commission < 0.0) {
                    Toast.makeText(this, "Geçerli miktar, satış fiyatı ve komisyon girin.", Toast.LENGTH_LONG).show()
                } else viewModel.sellAsset(asset.id, qty, unitPrice, commission) { result ->
                    runOnUiThread {
                        result.onSuccess { Toast.makeText(this, "Satış kaydedildi.", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(this, it.message ?: "Satış kaydedilemedi.", Toast.LENGTH_LONG).show() }
                    }
                }
            }.show()
    }

    private fun showDividendDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(56, 12, 56, 0) }
        fun field(hintText: String, decimal: Boolean = false) = android.widget.EditText(this).apply {
            hint = hintText
            inputType = if (decimal) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val ticker = field("Hisse sembolü (ör. THYAO)")
        val gross = field("Brüt temettü tutarı (TL)", true)
        val tax = field("Stopaj oranı (%)").apply { setText("0") }
        container.addView(ticker); container.addView(gross); container.addView(tax)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Temettü kaydı").setView(container)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val symbol = ticker.text.toString().trim().uppercase(Locale.ROOT)
                val amount = parseNumber(gross.text.toString())
                val taxRate = parseNumber(tax.text.toString()) ?: 0.0
                if (symbol.isBlank() || amount == null || amount <= 0.0 || taxRate !in 0.0..100.0) {
                    Toast.makeText(this, "Sembol, pozitif brüt tutar ve %0–100 stopaj girin.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.addDividend(symbol, amount, taxRate)
                    Toast.makeText(this, "Temettü kaydı işleniyor.", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun renderTrades(trades: List<TradeEntity>, assets: List<AssetEntity>) {
        if (trades.isEmpty()) { binding.tvTradeHistory.text = "Henüz işlem bulunmuyor."; return }
        val symbols = assets.associateBy { it.id }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        binding.tvTradeHistory.text = trades.take(12).joinToString("\n\n") { trade ->
            val symbol = symbols[trade.assetId]?.ticker ?: "Varlık #${trade.assetId}"
            val type = if (trade.action == "SELL") "SATIŞ" else "ALIŞ"
            val amount = trade.quantity * trade.unitPrice
            val result = if (trade.action == "SELL") "\nGerçekleşen K/Z: ${currency(trade.realizedProfit)}" else ""
            "${dateFormat.format(Date(trade.executedAt))}  •  $type $symbol\n${formatNumber(trade.quantity)} × ${currency(trade.unitPrice)} = ${currency(amount)}\nKomisyon: ${currency(trade.fee)}$result"
        }
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetAdapter { asset -> viewModel.deleteAsset(asset) }
        binding.recyclerViewAssets.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = assetAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun parseNumber(raw: String) = raw.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun formatNumber(value: Double) = NumberFormat.getNumberInstance(Locale("tr", "TR")).format(value)
    private fun currency(value: Double) = NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NAV_HOME = 1
        private const val NAV_ASSETS = 2
        private const val NAV_TRADES = 3
        private const val NAV_DIVIDENDS = 4
        private const val NAV_EXPORT = 5
    }
}
