package com.portfolio.app.ui.main

import android.content.Intent
import android.graphics.Typeface
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.portfolio.app.data.importer.ExcelImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.portfolio.app.data.local.PortfolioSnapshotEntity
import com.portfolio.app.data.remote.MarketRate
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
    private var analyticsContainer: LinearLayout? = null
    private var ratesContainer: LinearLayout? = null
    private val pageViews = mutableMapOf<Int, View>()
    private val pageTexts = mutableMapOf<Int, TextView>()
    private val pageLists = mutableMapOf<Int, LinearLayout>()
    private val pageClockViews = mutableListOf<TextView>()
    private var currentAssets: List<AssetEntity> = emptyList()
    private var currentTrades: List<TradeEntity> = emptyList()
    private var currentDividends: List<DividendEntity> = emptyList()
    private var currentSnapshots: List<PortfolioSnapshotEntity> = emptyList()
    private var currentRates: List<MarketRate> = emptyList()
    private var dailyPnlTry: Double? = null
    private var dailyPnlUsd: Double? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockUpdate = object : Runnable {
        override fun run() {
            if (::binding.isInitialized) {
                val nowText = SimpleDateFormat("EEEE, dd MMMM yyyy • HH:mm:ss", Locale("tr", "TR")).format(Date())
                binding.tvCurrentDateTime.text = nowText
                pageClockViews.forEach { it.text = nowText }
                clockHandler.postDelayed(this, 1_000L)
            }
        }
    }

    private val createExcelDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? -> writeExcel(uri) }

    private val importExcelDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(::importExcel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dashboardView = binding.root.getChildAt(0)
        setupRecyclerView()
        setupNavigation()

        binding.fabAddAsset.visibility = View.GONE
        binding.btnSellAsset.visibility = View.GONE
        binding.btnAddDividend.visibility = View.GONE
        binding.btnTopAddAsset.setOnClickListener { showAddAssetDialog() }
        binding.btnTopSell.setOnClickListener { showSellDialog() }
        binding.btnTopImportExcel.setOnClickListener { launchExcelImport() }
        binding.btnTopAddDividend.setOnClickListener { showDividendDialog() }
        binding.btnTopManageAssets.setOnClickListener { showPage(NAV_ASSETS) }
        binding.btnTopManageTrades.setOnClickListener { showPage(NAV_TRADES) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assets.collect { assets ->
                    currentAssets = assets
                    assetAdapter.submitList(assets)
                    updateSummary()
                    binding.tvAssetCount.text = getString(R.string.asset_count, assets.size)
                    binding.tvHoldingsCount.text = "${assets.size} kayıt"
                    binding.tvEmptyState.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvChartEmpty.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    binding.portfolioChart.setValues(assets.map { ((it.lastPrice ?: it.costPrice) * it.quantity).toFloat() })

                    binding.btnSellAsset.isEnabled = assets.any { it.quantity > 0.0 }
                    updatePageContent()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trades.collect { currentTrades = it; renderTrades(it, currentAssets); updateSummary(); updatePageContent() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dividends.collect { currentDividends = it; updatePageContent() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snapshots.collect { currentSnapshots = it; updatePageContent() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dailyPnlTry.collect { dailyPnlTry = it; updateSummary() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dailyPnlUsd.collect { dailyPnlUsd = it; updateSummary() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rates.collect { currentRates = it; renderMarketRates() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        clockHandler.removeCallbacks(clockUpdate)
        clockUpdate.run()
    }

    override fun onStop() {
        clockHandler.removeCallbacks(clockUpdate)
        super.onStop()
    }

    private fun showAddAssetDialog() {
        AddAssetDialogFragment { name, ticker, quantity, costPrice, category, broker, currencyCode, priceSource ->
            viewModel.addAsset(name, ticker, quantity, costPrice, category, broker, currencyCode, priceSource)
        }.show(supportFragmentManager, "AddAssetDialog")
    }

    private fun launchExcelImport() {
        importExcelDocument.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    private fun importExcel(uri: Uri) {
        lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ExcelImporter.readHoldings(it) }
                        ?: error("Excel dosyası açılamadı.")
                }
                if (rows.isEmpty()) {
                    Toast.makeText(this@MainActivity, "İçe aktarılabilecek geçerli varlık bulunamadı.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val preview = rows.take(12).joinToString("\n") {
                    "${it.ticker} • ${formatNumber(it.quantity)} adet • ${currency(it.costPrice)} • ${it.broker}"
                } + if (rows.size > 12) "\n… ve ${rows.size - 12} kayıt daha" else ""
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Excel içe aktarma önizlemesi")
                    .setMessage("${rows.size} geçerli varlık bulundu. Mevcut semboller tekrar eklenmeyecek.\n\n$preview")
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("İçe aktar") { _, _ ->
                        viewModel.importHoldings(rows) { result ->
                            runOnUiThread {
                                result.onSuccess { (added, skipped) ->
                                    Toast.makeText(this@MainActivity, "$added varlık eklendi, $skipped tekrar eden sembol atlandı.", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(this@MainActivity, "İçe aktarma başarısız: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }.show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "Excel okunamadı: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupNavigation() {
        val root = binding.root
        val pages = listOf(NAV_ASSETS, NAV_TRADES, NAV_DIVIDENDS, NAV_ANALYTICS, NAV_EXPORT, NAV_MORE, NAV_CATEGORIES, NAV_BROKERS, NAV_IMPORT, NAV_BIST, NAV_CRYPTO, NAV_RATES)
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
            if (id in listOf(NAV_DIVIDENDS, NAV_EXPORT, NAV_CATEGORIES, NAV_BROKERS, NAV_IMPORT, NAV_BIST, NAV_CRYPTO, NAV_RATES)) {
                content.addView(Button(this).apply {
                    text = "‹ Daha"
                    setOnClickListener { showPage(NAV_MORE) }
                })
            }
            if (id == NAV_ASSETS || id == NAV_TRADES) {
                val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                shortcuts.addView(actionButton("Portföy", R.color.portfolio_blue) { showPage(NAV_ASSETS) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
                shortcuts.addView(actionButton("İşlemler", R.color.portfolio_orange) { showPage(NAV_TRADES) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
                content.addView(shortcuts)
            }
            content.addView(title)
            val pageClock = TextView(this).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                setPadding(0, dp(8), 0, dp(8))
            }
            pageClockViews += pageClock
            content.addView(pageClock)
            content.addView(body)
            if (id == NAV_ASSETS || id == NAV_TRADES || id == NAV_BIST || id == NAV_CRYPTO) {
                val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                content.addView(list)
                pageLists[id] = list
            }
            if (id == NAV_MORE) {
                content.addView(TextView(this).apply { text = "PİYASALAR"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_green)); setPadding(0, dp(12), 0, dp(8)) })
                content.addView(actionButton("Borsa • BIST ve Amerikan hisseleri", R.color.portfolio_blue) { showPage(NAV_BIST) })
                content.addView(actionButton("Kripto varlıklar", R.color.portfolio_purple) { showPage(NAV_CRYPTO) })
                content.addView(actionButton("Güncel kurlar • Dolar, Euro, Altın, Gümüş", R.color.portfolio_green) { showPage(NAV_RATES) })
                content.addView(TextView(this).apply { text = "PORTFÖY ARAÇLARI"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_green)); setPadding(0, dp(12), 0, dp(8)) })
                content.addView(actionButton("Kategori dağılımı", R.color.portfolio_blue) { showPage(NAV_CATEGORIES) })
                content.addView(actionButton("Mevcut varlıkları sınıflandır", R.color.portfolio_orange) { showClassificationDialog() })
                content.addView(actionButton("Aracı kurumlar", R.color.portfolio_purple) { showPage(NAV_BROKERS) })
                content.addView(actionButton("Temettü kayıtları", R.color.portfolio_green) { showPage(NAV_DIVIDENDS) })
                content.addView(actionButton("Excel raporu / dışa aktar", R.color.portfolio_orange) { showPage(NAV_EXPORT) })
                content.addView(actionButton("Excel'den içe aktar", R.color.portfolio_blue) { showPage(NAV_IMPORT); launchExcelImport() })
            }
            if (id == NAV_CATEGORIES || id == NAV_BROKERS) {
                content.addView(TextView(this).apply {
                    text = "Bu özet, varlık kayıtlarındaki sınıflandırma ve aracı kurum alanlarına göre hesaplanır."
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                    setPadding(0, dp(12), 0, dp(12))
                })
            }
            if (id == NAV_IMPORT) {
                content.addView(actionButton("Excel dosyası seç (.xlsx)", R.color.portfolio_green) { launchExcelImport() })
            }
            if (id == NAV_BIST || id == NAV_CRYPTO) {
                content.addView(actionButton("Varlık ekle", R.color.portfolio_green) { showAddAssetDialog() })
            }
            if (id == NAV_RATES) {
                content.addView(actionButton("Kurları yenile", R.color.portfolio_green) { viewModel.refreshRates() })
                val rates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                content.addView(rates)
                ratesContainer = rates
            }
            if (id == NAV_ANALYTICS) {
                val chartColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                content.addView(chartColumn)
                analyticsContainer = chartColumn
            }
            if (id == NAV_EXPORT) {
                content.addView(actionButton("Excel dosyasını oluştur (.xlsx)", R.color.portfolio_green) {
                    createExcelDocument.launch("PortfolioPro_Rapor.xlsx")
                })
                content.addView(actionButton("Excel dosyasından içe aktar", R.color.portfolio_blue) {
                    launchExcelImport()
                })
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
            menu.add(0, NAV_ANALYTICS, 3, "Analiz").setIcon(android.R.drawable.ic_menu_gallery)
            menu.add(0, NAV_MORE, 4, "Daha").setIcon(android.R.drawable.ic_menu_more)
            setOnItemSelectedListener { item ->
                showPage(item.itemId)
                true
            }
        }
        root.addView(bottomNavigation, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, dp(76)
        ).apply { gravity = Gravity.BOTTOM })
        (binding.fabAddAsset.layoutParams as? CoordinatorLayout.LayoutParams)?.let { params ->
            params.bottomMargin = dp(90)
            binding.fabAddAsset.layoutParams = params
        }
        bottomNavigation.selectedItemId = NAV_HOME
        updatePageContent()
    }

    private fun showPage(id: Int) {
        dashboardView.visibility = if (id == NAV_HOME) View.VISIBLE else View.GONE
        pageViews.forEach { (pageId, view) -> view.visibility = if (pageId == id) View.VISIBLE else View.GONE }
        val selectedNavId = if (id in listOf(NAV_DIVIDENDS, NAV_EXPORT, NAV_CATEGORIES, NAV_BROKERS, NAV_IMPORT, NAV_BIST, NAV_CRYPTO, NAV_RATES)) NAV_MORE else id
        bottomNavigation.menu.findItem(selectedNavId)?.isChecked = true
        binding.fabAddAsset.visibility = View.GONE
        updatePageContent()
    }

    private fun updatePageContent() {
        pageTexts[NAV_ASSETS]?.text = ""
        renderAssetManagement()
        pageTexts[NAV_TRADES]?.text = ""
        renderTradeManagement()
        renderMarketAssets(NAV_BIST)
        renderMarketAssets(NAV_CRYPTO)
        renderMarketRates()
        /* legacy summary text disabled: management cards provide per-record actions */
        /* pageTexts[NAV_ASSETS]?.text = if (currentAssets.isEmpty()) {
            "Henüz portföy varlığı eklenmedi."
        } else currentAssets.joinToString("\n\n") { asset ->
            val price = asset.lastPrice ?: asset.costPrice
            val value = asset.quantity * price
            val pnl = (price - asset.costPrice) * asset.quantity
            "${asset.ticker} — ${asset.name}\nKategori: ${categoryLabel(asset.category)} • Aracı kurum: ${asset.broker}\nAdet: ${formatNumber(asset.quantity)} • Ortalama maliyet: ${currency(asset.costPrice)}\nSon fiyat: ${currency(price)} • Piyasa değeri: ${currency(value)}\nKâr/Zarar: ${currency(pnl)}"
        }
        pageTexts[NAV_TRADES]?.text = if (currentTrades.isEmpty()) {
            "Henüz alış veya satış işlemi bulunmuyor."
        } else currentTrades.joinToString("\n\n") { trade ->
            val symbol = currentAssets.firstOrNull { it.id == trade.assetId }?.ticker ?: "Varlık #${trade.assetId}"
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(trade.executedAt))
            "${if (trade.action == "SELL") "SATIŞ" else "ALIŞ"} • $symbol\n$date\nMiktar: ${formatNumber(trade.quantity)} × ${currency(trade.unitPrice)}\nKomisyon: ${currency(trade.fee)}" +
                if (trade.action == "SELL") "\nGerçekleşen K/Z: ${currency(trade.realizedProfit)}" else ""
        }
        */
        pageTexts[NAV_DIVIDENDS]?.text = "Net temettü toplamı: " + currentDividends.groupBy { it.currency }.entries.joinToString(" • ") { (ccy, rows) -> currency(rows.sumOf { it.netAmount }, ccy) } + "\n\n" + if (currentDividends.isEmpty()) {
            "Henüz temettü kaydı bulunmuyor."
        } else currentDividends.joinToString("\n\n") { dividend ->
            val date = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(dividend.paymentDate))
            "${dividend.ticker} • $date\nBrüt: ${currency(dividend.grossAmount, dividend.currency)} • Stopaj: ${currency(dividend.withholdingTax, dividend.currency)}\nNet temettü: ${currency(dividend.netAmount, dividend.currency)}"
        }
        pageTexts[NAV_CATEGORIES]?.text = currentAssets.groupBy { it.category }.entries
            .sortedByDescending { entry -> entry.value.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) } }
            .joinToString("\n\n") { (category, assets) ->
                val totals = assets.groupBy { it.currency }.entries.joinToString(" • ") { (ccy, rows) -> currency(rows.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) }, ccy) }
                "${categoryLabel(category)} • ${assets.size} varlık\nToplam değer: $totals"
            }.ifBlank { "Kategori dağılımı için portföye varlık ekleyin." }
        pageTexts[NAV_BROKERS]?.text = currentAssets.groupBy { it.broker.ifBlank { "Belirtilmedi" } }
            .entries.sortedByDescending { entry -> entry.value.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) } }
            .joinToString("\n\n") { (broker, assets) ->
                val totals = assets.groupBy { it.currency }.entries.joinToString(" • ") { (ccy, rows) -> currency(rows.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) }, ccy) }
                "$broker • ${assets.size} varlık\nToplam değer: $totals"
            }.ifBlank { "Aracı kurum dağılımı için portföye varlık ekleyin." }
        pageTexts[NAV_IMPORT]?.text =
            "Excel (.xlsx) dosyasının ilk sayfasında Sembol, Varlık, Kategori, Adet ve Ortalama Maliyet sütunları bulunmalı.\n\nBu işlem mevcut portföy varlıklarını içe aktarır; işlem ve temettü geçmişini içe aktarmaz. Portfolio Pro dışa aktarma dosyaları doğrudan kullanılabilir. Aynı sembol varsa tekrar eklenmez. Önizleme onayından sonra yeni kayıtlar eklenir."
        pageTexts[NAV_MORE]?.text =
            "Kategori ve aracı kurum dağılımlarını inceleyin; temettü kayıtlarına ulaşın veya Excel dosyalarını içe/dışa aktarın."
        pageTexts[NAV_EXPORT]?.text =
            "Excel çalışma kitabı Portföy, İşlemler ve Temettüler sayfalarını içerir. Portföy sayfasında kategori ve aracı kurum bilgileri de bulunur. Kayıtlar cihazınızdaki yerel veritabanından alınır."
        renderAnalytics()
    }

    private fun updateSummary() {
        val currencies = listOf("TRY", "USD")
        fun rowsFor(calc: (String) -> Double): String = currencies.mapNotNull { code ->
            val amount = calc(code)
            if (amount == 0.0 && currentAssets.none { it.currency == code } && currentTrades.none { it.currency == code }) null
            else currency(amount, code)
        }.joinToString("\n").ifBlank { "—" }
        val invested = rowsFor { code -> currentAssets.filter { it.currency.equals(code, true) }.sumOf { it.quantity * it.costPrice } }
        val totalPnl = rowsFor { code ->
            val unrealized = currentAssets.filter { it.currency.equals(code, true) }.sumOf { it.quantity * ((it.lastPrice ?: it.costPrice) - it.costPrice) }
            val realized = currentTrades.filter { it.action == "SELL" && it.currency.equals(code, true) }.sumOf { it.realizedProfit }
            unrealized + realized
        }
        binding.tvTotalValue.text = invested
        binding.tvTotalProfitLoss.text = totalPnl
        binding.tvDailyProfitLoss.text = buildString {
            append("TRY  •  "); append(dailyPnlTry?.let { currency(it, "TRY") } ?: "Önceki gün verisi birikiyor")
            append("\nUSD  •  "); append(dailyPnlUsd?.let { currency(it, "USD") } ?: "Önceki gün verisi birikiyor")
        }
        binding.tvHeaderTitle.contentDescription = "Toplam yatırım $invested. Toplam kâr zarar $totalPnl"
    }

    private fun renderMarketAssets(pageId: Int) {
        val list = pageLists[pageId] ?: return
        list.removeAllViews()
        if (pageId == NAV_BIST) {
            val groups = listOf(
                "BIST Hisseleri" to currentAssets.filter { it.category.uppercase() in listOf("STOCK", "BIST", "HISSE") },
                "Amerikan Borsaları" to currentAssets.filter { it.category.uppercase() == "US_STOCK" }
            )
            groups.forEach { (heading, assets) ->
                list.addView(TextView(this).apply {
                    text = heading; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_green))
                    setPadding(0, dp(12), 0, dp(8))
                })
                if (assets.isEmpty()) list.addView(TextView(this).apply {
                    text = "Bu bölümde henüz varlık yok."; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                    setPadding(0, 0, 0, dp(12))
                }) else assets.forEach { addMarketAssetCard(list, it) }
            }
        } else {
            val assets = currentAssets.filter { it.category.equals("CRYPTO", true) }
            if (assets.isEmpty()) list.addView(TextView(this).apply {
                text = "Henüz kripto varlık eklenmedi."; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            }) else assets.forEach { addMarketAssetCard(list, it) }
        }
    }

    private fun addMarketAssetCard(list: LinearLayout, asset: AssetEntity) {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
            setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.portfolio_blue)))
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        val price = asset.lastPrice ?: asset.costPrice
        val value = price * asset.quantity
        val pnl = (price - asset.costPrice) * asset.quantity
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        column.addView(TextView(this).apply {
            text = "${asset.ticker} • ${asset.name}"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
        })
        column.addView(TextView(this).apply {
            text = "${formatNumber(asset.quantity)} adet • Son fiyat ${currency(price, asset.currency)}\nPiyasa değeri ${currency(value, asset.currency)} • K/Z ${currency(pnl, asset.currency)}\nKaynak: ${sourceLabel(asset.priceSource)} • ${asset.broker}"
            textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(6), 0, dp(8))
        })
        column.addView(actionButton("Varlığı düzenle", R.color.portfolio_blue) { showEditAssetDialog(asset) })
        card.addView(column); list.addView(card)
    }

    private fun renderMarketRates() {
        val container = ratesContainer ?: return
        container.removeAllViews()
        if (currentRates.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Piyasa verileri yükleniyor. Yahoo Finance üzerinden USD/TRY, EUR/TRY, altın ve gümüş vadeli işlem fiyatları sorgulanıyor."
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                setPadding(0, dp(16), 0, dp(16))
            })
            return
        }
        currentRates.forEach { rate ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
                setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.portfolio_blue)))
                strokeWidth = dp(1)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
            column.addView(TextView(this).apply {
                text = rate.title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            })
            column.addView(TextView(this).apply {
                text = currency(rate.value, rate.currency); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text)); setPadding(0, dp(5), 0, dp(3))
            })
            val change = rate.dailyChangePercent
            column.addView(TextView(this).apply {
                text = change?.let { String.format(Locale("tr", "TR"), "Günlük değişim  %+.2f%%", it) } ?: "Günlük değişim hesaplanamadı"
                setTextColor(ContextCompat.getColor(this@MainActivity, if ((change ?: 0.0) >= 0.0) R.color.portfolio_green else R.color.portfolio_orange))
                textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            })
            column.addView(TextView(this).apply {
                text = "${rate.source} • ${SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(rate.updatedAt))}"
                textSize = 11f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(6), 0, 0)
            })
            card.addView(column); container.addView(card)
        }
        container.addView(TextView(this).apply {
            text = "Gram altın ve gram gümüş, ons vadeli işlem fiyatı × USD/TRY ÷ 31,1034768 formülüyle hesaplanır. Veriler Yahoo Finance kaynaklıdır; piyasa/sağlayıcı gecikmeleri olabilir."
            textSize = 11f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(10), 0, dp(20))
        })
    }

    private fun renderAnalytics() {
        val container = analyticsContainer ?: return
        container.removeAllViews()
        val active = currentAssets.filter { it.quantity > 0.0 }
        if (active.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Grafikleri görüntülemek için portföye varlık ekleyin."
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            })
            return
        }
        fun addChart(title: String, subtitle: String,
                     type: com.portfolio.app.ui.chart.AnalyticsChartView.ChartType,
                     values: List<Double>, labels: List<String>) {
            if (values.isEmpty()) return
            container.addView(TextView(this).apply {
                text = "$title\n$subtitle"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
                setPadding(0, dp(18), 0, dp(8))
            })
            container.addView(com.portfolio.app.ui.chart.AnalyticsChartView(this).apply {
                chartType = type; chartValues = values; chartLabels = labels
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220))
            })
        }
        listOf("TRY", "USD").forEach { code ->
            val group = active.filter { it.currency.equals(code, true) }
            if (group.isNotEmpty()) {
                container.addView(TextView(this).apply {
                    text = if (code == "TRY") "TÜRK LİRASI PORTFÖYÜ" else "ABD DOLARI PORTFÖYÜ"
                    textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_green))
                    setPadding(0, dp(18), 0, dp(4))
                })
                val market = group.map { it.quantity * (it.lastPrice ?: it.costPrice) }
                val unrealized = group.map { ((it.lastPrice ?: it.costPrice) - it.costPrice) * it.quantity }
                addChart("Varlık dağılımı", "Güncel piyasa değerine göre • $code", com.portfolio.app.ui.chart.AnalyticsChartView.ChartType.DONUT, market, group.map { it.ticker })
                addChart("Gerçekleşmemiş kâr / zarar", "Ortalama maliyet ile son fiyat farkı • $code", com.portfolio.app.ui.chart.AnalyticsChartView.ChartType.BAR, unrealized, group.map { it.ticker })
                val realized = currentTrades.filter { it.action == "SELL" && it.currency.equals(code, true) }.sumOf { it.realizedProfit }
                container.addView(TextView(this).apply {
                    text = "Gerçekleşmiş satış kâr/zararı: ${currency(realized, code)}"
                    textSize = 13f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text)); setPadding(0, dp(8), 0, dp(8))
                })
            }
        }
        listOf("TRY", "USD").forEach { code ->
            val monthly = (0..5).map { offset ->
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset - 5) }
                val month = cal.get(java.util.Calendar.MONTH); val year = cal.get(java.util.Calendar.YEAR)
                currentDividends.filter { dividend ->
                    val d = java.util.Calendar.getInstance().apply { timeInMillis = dividend.paymentDate }
                    dividend.currency.equals(code, true) && d.get(java.util.Calendar.MONTH) == month && d.get(java.util.Calendar.YEAR) == year
                }.sumOf { it.netAmount }
            }
            if (monthly.any { it > 0.0 }) addChart("Temettü nakit akışı", "Son 6 ay • $code", com.portfolio.app.ui.chart.AnalyticsChartView.ChartType.BAR, monthly,
                (0..5).map { offset -> val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset - 5) }; String.format(Locale("tr", "TR"), "%02d/%02d", cal.get(java.util.Calendar.MONTH)+1, cal.get(java.util.Calendar.YEAR)%100) })
        }
        container.addView(TextView(this).apply {
            text = "Günlük kâr/zarar, bir önceki güne ait kaydedilmiş varlık fiyatlarıyla karşılaştırılır. Bu özellik 4.0 sürümünden itibaren fiyat geçmişi biriktirir; ilk karşılaştırma için en az iki farklı gün fiyat kaydı gerekir."
            textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(16), 0, dp(24))
        })
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
        NAV_ANALYTICS -> "Portföy analizi"
        NAV_MORE -> "Diğer sayfalar"
        NAV_CATEGORIES -> "Kategori dağılımı"
        NAV_BROKERS -> "Aracı kurum dağılımı"
        NAV_IMPORT -> "Excel içe aktarma"
        NAV_BIST -> "Borsa • BIST ve ABD"
        NAV_CRYPTO -> "Kripto varlıklar"
        NAV_RATES -> "Güncel kurlar"
        else -> "Raporlar ve dışa aktarma"
    }

    private fun actionButton(label: String, colorRes: Int, onClick: () -> Unit) =
        MaterialButton(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.rgb(8, 17, 31))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, colorRes))
            cornerRadius = dp(14)
            minHeight = dp(52)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                bottomMargin = dp(10)
            }
        }

    private fun tradeTypeLabel(action: String) = when(action) { "SELL" -> "SATIŞ"; "DIVIDEND" -> "TEMETTÜ"; else -> "ALIŞ" }

    private fun categoryLabel(value: String) = when (value.uppercase(Locale.ROOT)) {
        "STOCK", "BIST", "HISSE" -> "Hisse (BIST)"
        "US_STOCK" -> "Amerikan Borsaları"
        "FUND" -> "Yatırım Fonu"
        "CRYPTO" -> "Kripto"
        "FX" -> "Döviz"
        "GOLD" -> "Altın"
        "COMMODITY" -> "Emtia"
        "BOND" -> "Tahvil / Bono"
        "CASH" -> "Nakit"
        else -> "Diğer"
    }

    private fun showClassificationDialog() {
        if (currentAssets.isEmpty()) {
            Toast.makeText(this, "Önce portföye varlık ekleyin.", Toast.LENGTH_LONG).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(4))
        }
        val assetSpinner = android.widget.Spinner(this)
        assetSpinner.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            currentAssets.map { "${it.ticker} — ${it.name}" }
        )
        val categoryCodes = listOf("STOCK", "US_STOCK", "FUND", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "CASH", "OTHER")
        val categoryLabels = listOf("Hisse (BIST)", "Amerikan Borsaları", "Yatırım Fonu", "Kripto Para", "Döviz", "Altın", "Emtia", "Tahvil / Bono", "Nakit", "Diğer")
        val categorySpinner = android.widget.Spinner(this)
        categorySpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryLabels)
        val brokerNames = listOf("Belirtilmedi", "İş Yatırım", "Garanti BBVA Yatırım", "Yapı Kredi Yatırım", "Ak Yatırım", "Ziraat Yatırım", "QNB Invest", "Midas", "PhillipCapital", "A1 Capital", "Diğer")
        val brokerSpinner = android.widget.Spinner(this)
        brokerSpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, brokerNames)
        val customBroker = android.widget.EditText(this).apply { hint = "Diğer aracı kurum adı" }
        box.addView(label("Varlık"))
        box.addView(assetSpinner)
        box.addView(label("Kategori"))
        box.addView(categorySpinner)
        box.addView(label("Aracı kurum"))
        box.addView(brokerSpinner)
        box.addView(customBroker)
        fun syncSelection() {
            val selected = currentAssets[assetSpinner.selectedItemPosition.coerceIn(0, currentAssets.lastIndex)]
            categorySpinner.setSelection(categoryCodes.indexOf(selected.category).coerceAtLeast(0))
            val brokerIndex = brokerNames.indexOf(selected.broker)
            brokerSpinner.setSelection(if (brokerIndex >= 0) brokerIndex else brokerNames.lastIndex)
            customBroker.setText(if (brokerIndex < 0) selected.broker else "")
        }
        assetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = syncSelection()
        }
        syncSelection()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Varlık sınıflandırma")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val asset = currentAssets[assetSpinner.selectedItemPosition.coerceIn(0, currentAssets.lastIndex)]
                val brokerChoice = brokerNames[brokerSpinner.selectedItemPosition.coerceIn(0, brokerNames.lastIndex)]
                val broker = if (brokerChoice == "Diğer") customBroker.text.toString().trim().ifBlank { "Belirtilmedi" } else brokerChoice
                viewModel.classifyAsset(asset.id, categoryCodes[categorySpinner.selectedItemPosition], broker) { result ->
                    runOnUiThread {
                        result.onSuccess { Toast.makeText(this, "Kategori ve aracı kurum güncellendi.", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(this, "Güncelleme başarısız: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }.show()
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
        val price = field("Birim satış fiyatı (${available.first().currency})")
        val fee = field("Komisyon / masraf (${available.first().currency})").apply { setText("0") }
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
        if (currentAssets.isEmpty()) {
            Toast.makeText(this, "Temettü kaydetmeden önce ilgili varlığı ekleyin.", Toast.LENGTH_LONG).show()
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        val assetSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentAssets.map { "${it.ticker} — ${it.name}" })
        }
        val currencySpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Türk Lirası (TRY)", "ABD Doları (USD)"))
        }
        fun field(hint: String) = android.widget.EditText(this).apply {
            this.hint = hint; isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val gross = field("Brüt temettü tutarı")
        val tax = field("Stopaj oranı (%)").apply { setText("0") }
        val note = android.widget.EditText(this).apply { hint = "Not (isteğe bağlı)" }
        box.addView(TextView(this).apply { text = "Temettü alınan varlık"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)) })
        box.addView(assetSpinner)
        box.addView(TextView(this).apply { text = "Ödeme para birimi"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(10), 0, 0) })
        box.addView(currencySpinner); box.addView(gross); box.addView(tax); box.addView(note)
        assetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currencySpinner.setSelection(if (currentAssets[position].currency == "USD") 1 else 0)
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Temettüyü işlem olarak ekle").setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val asset = currentAssets[assetSpinner.selectedItemPosition.coerceIn(0, currentAssets.lastIndex)]
                val amount = parseNumber(gross.text.toString())
                val taxRate = parseNumber(tax.text.toString()) ?: 0.0
                val currencyCode = if (currencySpinner.selectedItemPosition == 1) "USD" else "TRY"
                if (amount == null || amount <= 0.0 || taxRate !in 0.0..100.0) {
                    Toast.makeText(this, "Pozitif brüt tutar ve %0–100 stopaj girin.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.addDividend(asset.ticker, amount, taxRate, currencyCode)
                    Toast.makeText(this, "Temettü işlemi kaydediliyor.", Toast.LENGTH_SHORT).show()
                    showPage(NAV_TRADES)
                }
            }.show()
    }

    private fun renderTrades(trades: List<TradeEntity>, assets: List<AssetEntity>) {
        if (trades.isEmpty()) { binding.tvTradeHistory.text = "Henüz işlem bulunmuyor."; return }
        val symbols = assets.associateBy { it.id }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        binding.tvTradeHistory.text = trades.take(12).joinToString("\n\n") { trade ->
            val symbol = symbols[trade.assetId]?.ticker ?: "Varlık #${trade.assetId}"
            val type = tradeTypeLabel(trade.action)
            val amount = trade.quantity * trade.unitPrice
            val result = if (trade.action == "SELL") "\nGerçekleşen K/Z: ${currency(trade.realizedProfit, trade.currency)}" else if (trade.action == "DIVIDEND") "\nNet temettü: ${currency(trade.unitPrice - trade.fee, trade.currency)}" else ""
            "${dateFormat.format(Date(trade.executedAt))}  •  $type $symbol\n${formatNumber(trade.quantity)} × ${currency(trade.unitPrice, trade.currency)} = ${currency(amount, trade.currency)}\n${if (trade.action == "DIVIDEND") "Stopaj" else "Komisyon"}: ${currency(trade.fee, trade.currency)}$result"
        }
    }

    private fun renderAssetManagement() {
        val list = pageLists[NAV_ASSETS] ?: return
        list.removeAllViews()
        if (currentAssets.isEmpty()) {
            list.addView(TextView(this).apply { text = "Henüz varlık yok. Üstteki Varlık ekle butonunu kullanın."; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(12), 0, dp(12)) })
            return
        }
        currentAssets.forEach { asset ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
                setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.portfolio_blue)))
                strokeWidth = dp(1)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            val value = asset.quantity * (asset.lastPrice ?: asset.costPrice)
            column.addView(TextView(this).apply {
                text = "${asset.ticker} — ${asset.name}"
                textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
            })
            column.addView(TextView(this).apply {
                text = "${categoryLabel(asset.category)} • ${asset.broker}\nMiktar: ${formatNumber(asset.quantity)} • Ort. maliyet: ${currency(asset.costPrice, asset.currency)}\nPiyasa değeri: ${currency(value, asset.currency)}\nKaynak: ${sourceLabel(asset.priceSource)}"
                textSize = 13f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(6), 0, dp(10))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton("Düzenle", R.color.portfolio_blue) { showEditAssetDialog(asset) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
            actions.addView(actionButton("Sil", R.color.portfolio_orange) {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity).setTitle("Varlığı sil")
                    .setMessage("${asset.ticker} varlığı ve ilişkili işlem/temettü kayıtları silinecek. Devam edilsin mi?")
                    .setNegativeButton("Vazgeç", null).setPositiveButton("Sil") { _, _ -> viewModel.deleteAsset(asset) }.show()
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
            column.addView(actions); card.addView(column); list.addView(card)
        }
    }

    private fun renderTradeManagement() {
        val list = pageLists[NAV_TRADES] ?: return
        list.removeAllViews()
        if (currentTrades.isEmpty()) {
            list.addView(TextView(this).apply { text = "Henüz işlem yok. Alış, satış veya temettü kaydı ekleyebilirsiniz."; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(12), 0, dp(12)) })
            return
        }
        val assets = currentAssets.associateBy { it.id }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        currentTrades.forEach { trade ->
            val symbol = assets[trade.assetId]?.ticker ?: "Varlık #${trade.assetId}"
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = dp(16).toFloat(); setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
                setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, if (trade.action == "SELL") R.color.portfolio_orange else if (trade.action == "DIVIDEND") R.color.portfolio_purple else R.color.portfolio_green)))
                strokeWidth = dp(1); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            column.addView(TextView(this).apply {
                text = "${tradeTypeLabel(trade.action)} • $symbol"
                textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, if (trade.action == "SELL") R.color.portfolio_orange else if (trade.action == "DIVIDEND") R.color.portfolio_purple else R.color.portfolio_green))
            })
            column.addView(TextView(this).apply {
                text = "${dateFormat.format(Date(trade.executedAt))}\nMiktar: ${formatNumber(trade.quantity)} × ${currency(trade.unitPrice, trade.currency)}\n${if (trade.action == "DIVIDEND") "Stopaj" else "Komisyon"}: ${currency(trade.fee, trade.currency)}" +
                    if (trade.action == "SELL") "\nGerçekleşen K/Z: ${currency(trade.realizedProfit, trade.currency)}" else if (trade.action == "DIVIDEND") "\nNet temettü: ${currency(trade.unitPrice - trade.fee, trade.currency)}" else ""
                textSize = 13f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(6), 0, dp(10))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton("İşlemi düzenle", R.color.portfolio_blue) { showEditTradeDialog(trade) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
            actions.addView(actionButton("Sil", R.color.portfolio_orange) {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity).setTitle("İşlemi sil")
                    .setMessage("Bu işlem silinince varlık miktarı, ortalama maliyet ve gerçekleşen kâr/zarar yeniden hesaplanacak.")
                    .setNegativeButton("Vazgeç", null).setPositiveButton("Sil") { _, _ ->
                        viewModel.deleteTrade(trade.id) { result -> runOnUiThread {
                            result.onSuccess { Toast.makeText(this@MainActivity, "İşlem silindi ve portföy yeniden hesaplandı.", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(this@MainActivity, it.message ?: "İşlem silinemedi.", Toast.LENGTH_LONG).show() }
                        } }
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
            column.addView(actions); card.addView(column); list.addView(card)
        }
    }

    private fun showEditAssetDialog(asset: AssetEntity) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        fun field(hint: String, value: String) = android.widget.EditText(this).apply { this.hint = hint; setText(value); isSingleLine = true }
        val name = field("Varlık adı", asset.name)
        val ticker = field("Sembol", asset.ticker)
        val categories = listOf("STOCK", "US_STOCK", "FUND", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "CASH", "OTHER")
        val labels = listOf("Hisse (BIST)", "Amerikan Borsaları", "Yatırım Fonu", "Kripto", "Döviz", "Altın", "Emtia", "Tahvil / Bono", "Nakit", "Diğer")
        val category = android.widget.Spinner(this).apply { adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels); setSelection(categories.indexOf(asset.category).coerceAtLeast(0)) }
        val broker = field("Aracı kurum", asset.broker)
        val currencyCodes = listOf("TRY", "USD")
        val currencySpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Türk Lirası (TRY)", "ABD Doları (USD)"))
            setSelection(if (asset.currency == "USD") 1 else 0)
            isEnabled = currentTrades.none { it.assetId == asset.id }
        }
        val sourceCodes = listOf("AUTO", "YAHOO_FINANCE", "COINGECKO", "FRANKFURTER", "MANUAL")
        val sourceSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Otomatik", "Yahoo Finance", "CoinGecko", "Frankfurter", "Elle giriş"))
            setSelection(sourceCodes.indexOf(asset.priceSource).coerceAtLeast(0))
        }
        val manualPrice = field("Elle girilecek güncel fiyat", (asset.lastPrice ?: asset.costPrice).toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            visibility = if (asset.priceSource == "MANUAL") View.VISIBLE else View.GONE
        }
        sourceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                manualPrice.visibility = if (sourceCodes[position] == "MANUAL") View.VISIBLE else View.GONE
            }
        }
        val hasTrades = currentTrades.any { it.assetId == asset.id }
        val quantity = if (!hasTrades) field("Miktar", asset.quantity.toString()) else null
        val cost = if (!hasTrades) field("Ortalama maliyet", asset.costPrice.toString()) else null
        box.addView(name); box.addView(ticker)
        box.addView(TextView(this).apply { text = "Kategori"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(8), 0, 0) })
        box.addView(category); box.addView(broker)
        box.addView(TextView(this).apply { text = "Para birimi"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(8), 0, 0) })
        box.addView(currencySpinner)
        box.addView(TextView(this).apply { text = "Fiyat veri kaynağı"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(8), 0, 0) })
        box.addView(sourceSpinner); box.addView(manualPrice)
        if (hasTrades) {
            box.addView(TextView(this).apply {
                text = "Bu varlığın işlem geçmişi var. Miktar ve ortalama maliyet, İşlemler sayfasındaki alış/satış kayıtları düzenlenerek otomatik hesaplanır."
                textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(10), 0, 0)
            })
        } else {
            quantity?.let { box.addView(it) }; cost?.let { box.addView(it) }
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Varlığı düzenle").setView(box)
            .setNegativeButton("İptal", null).setPositiveButton("Kaydet") { _, _ ->
                val newName = name.text.toString().trim(); val newTicker = ticker.text.toString().trim().uppercase(Locale.ROOT)
                val newBroker = broker.text.toString().trim().ifBlank { "Belirtilmedi" }
                if (newName.isBlank() || newTicker.isBlank()) Toast.makeText(this, "Ad ve sembol boş olamaz.", Toast.LENGTH_LONG).show()
                else {
                    val newQuantity = quantity?.let { parseNumber(it.text.toString()) }
                    val newCost = cost?.let { parseNumber(it.text.toString()) }
                    if (!hasTrades && (newQuantity == null || newQuantity < 0.0 || newCost == null || newCost < 0.0)) {
                        Toast.makeText(this, "Miktar ve ortalama maliyeti kontrol edin.", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    val currencyCode = currencyCodes[currencySpinner.selectedItemPosition]
                    val provider = sourceCodes[sourceSpinner.selectedItemPosition]
                    val manual = if (provider == "MANUAL") parseNumber(manualPrice.text.toString()) else null
                    if (provider == "MANUAL" && (manual == null || manual < 0.0)) {
                        Toast.makeText(this, "Elle giriş için geçerli güncel fiyat girin.", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    viewModel.updateAssetDetails(asset.id, newName, newTicker, categories[category.selectedItemPosition], newBroker, newQuantity, newCost, currencyCode, provider, manual) { result -> runOnUiThread {
                    result.onSuccess { Toast.makeText(this, "Varlık bilgileri güncellendi.", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(this, it.message ?: "Varlık güncellenemedi.", Toast.LENGTH_LONG).show() }
                } }
                }
            }.show()
    }

    private fun showEditTradeDialog(trade: TradeEntity) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        val action = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Alış", "Satış", "Temettü"))
            setSelection(when (trade.action) { "SELL" -> 1; "DIVIDEND" -> 2; else -> 0 })
            isEnabled = false
        }
        fun field(hint: String, value: String) = android.widget.EditText(this).apply {
            this.hint = hint; setText(value); isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        var executedAt = trade.executedAt
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        val dateButton = MaterialButton(this)
        dateButton.text = dateFormat.format(Date(executedAt))
        dateButton.isAllCaps = false
        dateButton.setTextColor(ContextCompat.getColor(this, R.color.portfolio_text))
        dateButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.portfolio_surface_alt))
        dateButton.setOnClickListener {
            val initial = java.util.Calendar.getInstance().apply { timeInMillis = executedAt }
            android.app.DatePickerDialog(this@MainActivity, { _, year, month, day ->
                android.app.TimePickerDialog(this@MainActivity, { _, hour, minute ->
                    executedAt = java.util.Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                    dateButton.text = dateFormat.format(Date(executedAt))
                }, initial.get(java.util.Calendar.HOUR_OF_DAY), initial.get(java.util.Calendar.MINUTE), true).show()
            }, initial.get(java.util.Calendar.YEAR), initial.get(java.util.Calendar.MONTH), initial.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val quantity = field("Miktar", trade.quantity.toString()).apply { isEnabled = trade.action != "DIVIDEND" }
        val price = field(if (trade.action == "DIVIDEND") "Brüt temettü" else "Birim fiyat", trade.unitPrice.toString())
        val fee = field(if (trade.action == "DIVIDEND") "Stopaj tutarı" else "Komisyon", trade.fee.toString())
        val note = android.widget.EditText(this).apply { hint = "Not"; setText(trade.note); isSingleLine = true; inputType = android.text.InputType.TYPE_CLASS_TEXT }
        box.addView(TextView(this).apply { text = "İşlem türü"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)) })
        box.addView(action); box.addView(TextView(this).apply { text = "İşlem tarihi ve saati"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(8), 0, dp(2)) }); box.addView(dateButton); box.addView(quantity); box.addView(price); box.addView(fee); box.addView(note)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("İşlemi düzenle").setView(box)
            .setNegativeButton("İptal", null).setPositiveButton("Kaydet") { _, _ ->
                val qty = parseNumber(quantity.text.toString()); val unitPrice = parseNumber(price.text.toString()); val commission = parseNumber(fee.text.toString()) ?: 0.0
                if (qty == null || qty <= 0.0 || unitPrice == null || unitPrice < 0.0 || commission < 0.0) Toast.makeText(this, "Miktar, fiyat ve komisyon değerlerini kontrol edin.", Toast.LENGTH_LONG).show()
                else if (trade.action == "DIVIDEND" && commission > unitPrice) Toast.makeText(this, "Stopaj brüt temettüden yüksek olamaz.", Toast.LENGTH_LONG).show()
                 else viewModel.editTrade(trade.id, trade.action, if (trade.action == "DIVIDEND") 1.0 else qty, unitPrice, commission, note.text.toString(), executedAt) { result -> runOnUiThread {
                    result.onSuccess { Toast.makeText(this, "İşlem güncellendi; portföy ve kâr/zarar yeniden hesaplandı.", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(this, it.message ?: "İşlem güncellenemedi.", Toast.LENGTH_LONG).show() }
                } }
            }.show()
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetAdapter({ asset -> viewModel.deleteAsset(asset) }, { asset -> showEditAssetDialog(asset) })
        binding.recyclerViewAssets.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = assetAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun parseNumber(raw: String) = raw.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun formatNumber(value: Double) = NumberFormat.getNumberInstance(Locale("tr", "TR")).format(value)
    private fun currency(value: Double, code: String = "TRY") = NumberFormat.getCurrencyInstance(Locale("tr", "TR")).apply { currency = java.util.Currency.getInstance(code.uppercase(Locale.ROOT)) }.format(value)
    private fun sourceLabel(source: String) = when(source.uppercase()) { "YAHOO_FINANCE" -> "Yahoo Finance"; "COINGECKO" -> "CoinGecko"; "FRANKFURTER" -> "Frankfurter"; "MANUAL" -> "Elle giriş"; else -> "Otomatik" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NAV_HOME = 1
        private const val NAV_ASSETS = 2
        private const val NAV_TRADES = 3
        private const val NAV_DIVIDENDS = 4
        private const val NAV_ANALYTICS = 5
        private const val NAV_EXPORT = 6
        private const val NAV_MORE = 7
        private const val NAV_CATEGORIES = 8
        private const val NAV_BROKERS = 9
        private const val NAV_IMPORT = 10
        private const val NAV_BIST = 11
        private const val NAV_CRYPTO = 12
        private const val NAV_RATES = 13
    }
}
