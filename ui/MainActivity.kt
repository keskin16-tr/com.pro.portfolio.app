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
import com.portfolio.app.data.local.AssetPriceSnapshotEntity
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
    private var dashboardChartsContainer: LinearLayout? = null
    private val breakdownContainers = mutableMapOf<Int, LinearLayout>()
    private val pageViews = mutableMapOf<Int, View>()
    private val pageTexts = mutableMapOf<Int, TextView>()
    private val pageLists = mutableMapOf<Int, LinearLayout>()
    private val pageClockViews = mutableListOf<TextView>()
    private var currentAssets: List<AssetEntity> = emptyList()
    private var currentTrades: List<TradeEntity> = emptyList()
    private var currentDividends: List<DividendEntity> = emptyList()
    private var currentSnapshots: List<PortfolioSnapshotEntity> = emptyList()
    private var currentRates: List<MarketRate> = emptyList()
    private var ratesSyncing: Boolean = false
    private var currentPriceSnapshots: List<AssetPriceSnapshotEntity> = emptyList()
    private var tradeSearchQuery = ""
    private var tradeActionFilter = "Tümü"
    private var tradeCurrencyFilter = "Tümü"
    private var tradeDateFilter = "Tümü"
    private var selectedSummaryCurrency = "TRY"
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
        setupDashboardCharts()

        binding.fabAddAsset.visibility = View.GONE
        binding.btnTopAddAsset.setOnClickListener { showAddAssetDialog() }
        binding.btnTopSell.text = "İşlem Ekle"
        binding.btnTopSell.setOnClickListener { showAddTransactionDialog() }
        binding.btnCurrencyToggle.setOnClickListener { selectedSummaryCurrency = if (selectedSummaryCurrency == "TRY") "USD" else "TRY"; updateSummary() }
        binding.tvTopRatesStatus.setOnClickListener { viewModel.refreshRates() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assets.collect { assets ->
                    currentAssets = assets
                    assetAdapter.submitList(assets)
                    updateSummary()
                    binding.tvAssetCount.text = getString(R.string.asset_count, assets.size)
                    binding.tvHoldingsCount.text = "${assets.size} kayıt"
                    binding.tvEmptyState.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    updateDashboardAllocationChart()

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
                viewModel.dividends.collect { currentDividends = it; updateSummary(); updatePageContent() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snapshots.collect { currentSnapshots = it; updatePageContent() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.priceSnapshots.collect { currentPriceSnapshots = it; renderAnalytics() }
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
                viewModel.rates.collect { currentRates = it; updateSummary(); renderMarketRates(); renderTopRates() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ratesSyncing.collect { syncing ->
                    ratesSyncing = syncing
                    renderTopRates()
                }
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
        AddAssetDialogFragment { name, ticker, quantity, costPrice, category, broker, currencyCode, priceSource, manualPrice ->
            viewModel.addAsset(name, ticker, quantity, costPrice, category, broker, currencyCode, priceSource, manualPrice)
        }.show(supportFragmentManager, "AddAssetDialog")
    }

    private fun launchExcelImport() {
        importExcelDocument.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    private fun importExcel(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ExcelImporter.readPortfolio(it) }
                        ?: error("Excel dosyası açılamadı.")
                }
                val tradeCounts = backup.trades.groupingBy { it.action }.eachCount()
                val preview = backup.assets.take(10).joinToString("\n") {
                    "${it.ticker} • ${formatNumber(it.quantity)} adet • ${currency(it.costPerUnit, it.currency)}"
                } + if (backup.assets.size > 10) "\n… ve ${backup.assets.size - 10} varlık daha" else ""
                val transactionSummary = "Alış: ${tradeCounts["BUY"] ?: 0} • Satış: ${tradeCounts["SELL"] ?: 0} • Para girişi: ${tradeCounts["CASH_IN"] ?: 0} • Para çıkışı: ${tradeCounts["CASH_OUT"] ?: 0} • Transfer: ${tradeCounts["TRANSFER"] ?: 0} • Temettü: ${tradeCounts["DIVIDEND"] ?: 0}"
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Excel yedeği doğrulandı")
                    .setMessage(
                        "${backup.assets.size} varlık ve ${backup.trades.size} işlem bulundu.\n$transactionSummary\n\n" +
                            "Örnek varlıklar:\n$preview\n\n" +
                            "Birleştir: mevcut varlıkları korur, yeni varlıkları ve mükerrer olmayan geçmiş işlemleri ekler. Uygulanmış işlemler mevcut bakiyeye ikinci kez uygulanmaz.\n\n" +
                            "Tamamen değiştir: mevcut portföy ve geçmiş kayıtlarını Excel yedeğiyle değiştirir."
                    )
                    .setNegativeButton("Vazgeç", null)
                    .setNeutralButton("Birleştir") { _, _ -> runPortfolioRestore(backup, overwrite = false) }
                    .setPositiveButton("Tamamen değiştir") { _, _ ->
                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Mevcut portföy silinecek")
                            .setMessage("Mevcut varlıklar, işlemler, temettüler ve grafik geçmişi silinerek Excel yedeği yüklenecek. Bu işlem geri alınamaz. Devam edilsin mi?")
                            .setNegativeButton("İptal", null)
                            .setPositiveButton("Evet, yedeği yükle") { _, _ -> runPortfolioRestore(backup, overwrite = true) }
                            .show()
                    }
                    .show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "Excel okunamadı: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun runPortfolioRestore(backup: com.portfolio.app.data.importer.ImportedPortfolio, overwrite: Boolean) {
        viewModel.restorePortfolio(backup, overwrite) { result ->
            runOnUiThread {
                result.onSuccess { summary ->
                    val mode = if (overwrite) "Yedek geri yüklendi" else "Yedek birleştirildi"
                    Toast.makeText(
                        this@MainActivity,
                        "$mode: ${summary.assetsAdded} varlık, ${summary.tradesAdded} işlem eklendi; " +
                            "${summary.assetsSkipped} varlık ve ${summary.tradesSkipped} işlem atlandı.",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "Geri yükleme başarısız; veritabanı değişmedi: ${error.message}", Toast.LENGTH_LONG).show()
                }
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
            if (id == NAV_ASSETS) content.addView(actionButton("Yeni varlık / alış ekle", R.color.portfolio_green) { showAddAssetDialog() })
            if (id == NAV_TRADES) {
                content.addView(actionButton("İşlem Ekle", R.color.portfolio_green) { showAddTransactionDialog() })
            }
            if (id == NAV_TRADES) {
                val search = android.widget.EditText(this).apply {
                    hint = "Sembol, varlık adı veya not ara"; setSingleLine(true)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
                    setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { tradeSearchQuery = s?.toString().orEmpty(); renderTradeManagement() }
                        override fun afterTextChanged(s: android.text.Editable?) = Unit
                    })
                }
                content.addView(search)
                val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun addFilter(items: List<String>, onSelect: (String) -> Unit) {
                    val spinner = android.widget.Spinner(this@MainActivity).apply {
                        adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
                        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { onSelect(items[position]); renderTradeManagement() }
                        }
                    }
                    filters.addView(spinner, LinearLayout.LayoutParams(0, dp(48), 1f))
                }
                addFilter(listOf("Tümü", "Alış", "Satış", "Para Girişi", "Para Çıkışı", "Transfer", "Temettü")) { tradeActionFilter = it }
                addFilter(listOf("Tümü", "TRY", "USD")) { tradeCurrencyFilter = it }
                addFilter(listOf("Tümü", "7 gün", "30 gün", "90 gün", "Bu yıl")) { tradeDateFilter = it }
                content.addView(filters)
            }
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
                val charts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                content.addView(charts)
                breakdownContainers[id] = charts
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
        renderBreakdownPages()
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
        pageTexts[NAV_CATEGORIES]?.text = ""
        pageTexts[NAV_BROKERS]?.text = ""
        pageTexts[NAV_IMPORT]?.text =
            "Excel çalışma kitabında Varlıklar ve İşlemler sayfaları bulunmalıdır. Varlıklar sayfası id, name, symbol, category, account, quantity, unit_price, currency ve cost_per_unit alanlarını; İşlemler sayfası işlem türü, miktar, birim fiyat, tarih, applied ve gerçekleşen kâr/zarar alanlarını kullanır. Alış, satış, para girişi, para çıkışı, transfer ve temettü geçmişi birlikte geri yüklenir. Birleştirme mevcut varlıkları korur; tam değiştirme mevcut portföyü yedek dosyayla değiştirir."
        pageTexts[NAV_MORE]?.text =
            "Kategori ve aracı kurum dağılımlarını inceleyin; temettü kayıtlarına ulaşın veya Excel dosyalarını içe/dışa aktarın."
        pageTexts[NAV_EXPORT]?.text =
            "Excel çalışma kitabı, kullandığınız dosyayla aynı Varlıklar ve İşlemler sayfalarını ve aynı sütun adlarını kullanır. Alış, satış, para girişi, para çıkışı, transfer ve temettü kayıtları tek işlem geçmişinde saklanır."
        renderAnalytics()
    }

    private fun updateSummary() {
        val displayCurrency = selectedSummaryCurrency
        val usdTry = currentRates.firstOrNull { it.title.equals("Dolar / TL", true) }?.value
        val hasTry = currentAssets.any { it.currency.equals("TRY", true) } || currentTrades.any { it.currency.equals("TRY", true) } || currentDividends.any { it.currency.equals("TRY", true) }
        val hasUsd = currentAssets.any { it.currency.equals("USD", true) } || currentTrades.any { it.currency.equals("USD", true) } || currentDividends.any { it.currency.equals("USD", true) }
        fun convert(amount: Double, source: String): Double? {
            if (source.equals(displayCurrency, true)) return amount
            val rate = usdTry?.takeIf { it > 0.0 } ?: return null
            return if (source == "TRY" && displayCurrency == "USD") amount / rate else if (source == "USD" && displayCurrency == "TRY") amount * rate else null
        }
        fun total(calc: (String) -> Double): Double? {
            val t = if (hasTry) convert(calc("TRY"), "TRY") else 0.0
            val u = if (hasUsd) convert(calc("USD"), "USD") else 0.0
            if ((hasTry && t == null) || (hasUsd && u == null)) return null
            return (t ?: 0.0) + (u ?: 0.0)
        }
        val marketValue = total { code -> currentAssets.filter { it.currency.equals(code, true) }.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) } }
        val pnl = total { code ->
            val unrealized = currentAssets.filter { it.currency.equals(code, true) }.sumOf { it.quantity * ((it.lastPrice ?: it.costPrice) - it.costPrice) }
            val realized = currentTrades.filter { it.action == "SELL" && it.currency.equals(code, true) }.sumOf { it.realizedProfit }
            val dividends = currentDividends.filter { it.currency.equals(code, true) }.sumOf { it.netAmount }
            unrealized + realized + dividends
        }
        val capital = total { code ->
            val opening = currentAssets.filter { it.currency.equals(code, true) }.sumOf { it.openingQuantity * it.openingCostPrice }
            val buys = currentTrades.filter { it.action == "BUY" && it.currency.equals(code, true) }.sumOf { it.quantity * it.unitPrice + it.fee }
            opening + buys
        }
        val percent = if (pnl != null && capital != null && capital > 0.0) pnl / capital * 100.0 else null
        binding.btnCurrencyToggle.text = if (displayCurrency == "TRY") "₺" else "\$"
        binding.btnCurrencyToggle.contentDescription = if (displayCurrency == "TRY") "Dolar görünümüne geç" else "Türk lirası görünümüne geç"
        binding.tvTotalValue.text = marketValue?.let { currency(it, displayCurrency) } ?: "USD/TRY kuru bekleniyor"
        binding.tvTotalProfitLoss.text = pnl?.let { currency(it, displayCurrency) } ?: "USD/TRY kuru bekleniyor"
        binding.tvTotalProfitLossPercent.text = percent?.let { String.format(Locale("tr", "TR"), "%+.2f%% toplam getiri", it) } ?: "Getiri yüzdesi hesaplanamıyor"
        binding.tvTotalProfitLoss.setTextColor(ContextCompat.getColor(this, if ((pnl ?: 0.0) < 0.0) R.color.portfolio_orange else R.color.portfolio_green))
        binding.tvTotalProfitLossPercent.setTextColor(ContextCompat.getColor(this, if ((percent ?: 0.0) < 0.0) R.color.portfolio_orange else R.color.portfolio_green))
        val todayStart = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val divTry = currentDividends.filter { it.currency == "TRY" && it.paymentDate >= todayStart }.sumOf { it.netAmount }
        val divUsd = currentDividends.filter { it.currency == "USD" && it.paymentDate >= todayStart }.sumOf { it.netAmount }
        val dailyTry = dailyPnlTry?.plus(divTry) ?: divTry.takeIf { it != 0.0 }
        val dailyUsd = dailyPnlUsd?.plus(divUsd) ?: divUsd.takeIf { it != 0.0 }
        val daily = if ((hasTry && dailyTry == null) || (hasUsd && dailyUsd == null) || (!hasTry && !hasUsd)) null else {
            val t = if (hasTry) convert(dailyTry ?: 0.0, "TRY") else 0.0
            val u = if (hasUsd) convert(dailyUsd ?: 0.0, "USD") else 0.0
            if ((hasTry && t == null) || (hasUsd && u == null)) null else (t ?: 0.0) + (u ?: 0.0)
        }
        binding.tvDailyProfitLoss.text = daily?.let { currency(it, displayCurrency) } ?: "Günlük karşılaştırma verisi birikiyor"
        binding.tvDailyProfitLoss.setTextColor(ContextCompat.getColor(this, if ((daily ?: 0.0) < 0.0) R.color.portfolio_orange else R.color.portfolio_text))
        binding.tvHeaderTitle.contentDescription = "Toplam portföy piyasa değeri: " + binding.tvTotalValue.text + ". Kâr zarar: " + binding.tvTotalProfitLoss.text + ". Getiri: " + binding.tvTotalProfitLossPercent.text
        updateDashboardAllocationChart()
        renderDashboardCharts()
    }

    private fun setupDashboardCharts() {
        val scroll = dashboardView as? android.widget.ScrollView
        val content = scroll?.getChildAt(0) as? LinearLayout ?: return
        val charts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        content.addView(charts)
        dashboardChartsContainer = charts
    }

    private fun updateDashboardAllocationChart() {
        val rate = currentRates.firstOrNull { it.title.equals("Dolar / TL", true) }?.value
        val active = currentAssets.filter { it.quantity > 0.0 }
        val converted = active.map { asset ->
            val native = asset.quantity * (asset.lastPrice ?: asset.costPrice)
            when {
                asset.currency.equals(selectedSummaryCurrency, true) -> native
                rate == null || rate <= 0.0 -> null
                asset.currency == "USD" && selectedSummaryCurrency == "TRY" -> native * rate
                asset.currency == "TRY" && selectedSummaryCurrency == "USD" -> native / rate
                else -> null
            }
        }
        val complete = converted.none { it == null }
        val values = if (complete) converted.filterNotNull() else emptyList()
        binding.portfolioChart.setValues(values.map { it.toFloat() })
        binding.tvChartEmpty.visibility = if (values.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderDashboardCharts() {
        val container = dashboardChartsContainer ?: return
        container.removeAllViews()
        if (currentAssets.isEmpty()) return
        val rate = currentRates.firstOrNull { it.title.equals("Dolar / TL", true) }?.value
        val needsFx = currentAssets.any { !it.currency.equals(selectedSummaryCurrency, true) } ||
            currentTrades.any { !it.currency.equals(selectedSummaryCurrency, true) } ||
            currentDividends.any { !it.currency.equals(selectedSummaryCurrency, true) }
        if (needsFx && (rate == null || rate <= 0.0)) {
            container.addView(TextView(this).apply {
                text = "USD/TRY kuru bekleniyor. Farklı para birimlerindeki veriler eksik toplam göstermemek için grafiklere dahil edilmedi."
                textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                setPadding(0, dp(12), 0, dp(12))
            })
            return
        }
        fun convert(value: Double, code: String): Double? {
            if (code.equals(selectedSummaryCurrency, true)) return value
            if (rate == null || rate <= 0.0) return null
            return if (code == "USD" && selectedSummaryCurrency == "TRY") value * rate
                else if (code == "TRY" && selectedSummaryCurrency == "USD") value / rate else null
        }
        val categories = currentAssets.groupBy { categoryLabel(it.category) }.mapNotNull { (name, rows) ->
            val value = rows.sumOf { asset ->
                convert(asset.quantity * (asset.lastPrice ?: asset.costPrice), asset.currency) ?: 0.0
            }
            if (value > 0.0) name to value else null
        }.sortedByDescending { it.second }
        addPortfolioChart(container, "Varlık dağılımı", "Kategori • ${if (selectedSummaryCurrency == "TRY") "₺" else "$"} görünümü",
            com.portfolio.app.ui.chart.PortfolioChartView.Type.DONUT, categories.map { it.second },
            categories.map { it.first }, 220, categories.sumOf { it.second }.let { currency(it, selectedSummaryCurrency) })
        val accounts = currentAssets.groupBy { it.broker.ifBlank { "Belirtilmedi" } }.mapNotNull { (name, rows) ->
            val value = rows.sumOf { asset -> convert(asset.quantity * (asset.lastPrice ?: asset.costPrice), asset.currency) ?: 0.0 }
            if (value > 0.0) name to value else null
        }.sortedByDescending { it.second }
        addPortfolioChart(container, "Aracı kurum dağılımı", "Piyasa değerine göre • ${selectedSummaryCurrency}",
            com.portfolio.app.ui.chart.PortfolioChartView.Type.HORIZONTAL_BAR,
            accounts.map { it.second }, accounts.map { it.first }, maxOf(150, accounts.size * 38 + 20),
            suffix = if (selectedSummaryCurrency == "TRY") " ₺" else " $")
        val monthlyLabels = (0..5).map { offset ->
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset - 5) }
            String.format(Locale("tr", "TR"), "%02d/%02d", cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR) % 100)
        }
        val monthly = (0..5).map { offset ->
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset - 5) }
            val m = cal.get(java.util.Calendar.MONTH); val y = cal.get(java.util.Calendar.YEAR)
            val sells = currentTrades.filter { trade ->
                val date = java.util.Calendar.getInstance().apply { timeInMillis = trade.executedAt }
                trade.action == "SELL" && date.get(java.util.Calendar.MONTH) == m && date.get(java.util.Calendar.YEAR) == y
            }.sumOf { trade -> convert(trade.realizedProfit, trade.currency) ?: 0.0 }
            val dividends = currentDividends.filter { dividend ->
                val date = java.util.Calendar.getInstance().apply { timeInMillis = dividend.paymentDate }
                date.get(java.util.Calendar.MONTH) == m && date.get(java.util.Calendar.YEAR) == y
            }.sumOf { dividend -> convert(dividend.netAmount, dividend.currency) ?: 0.0 }
            sells + dividends
        }
        if (monthly.any { it != 0.0 }) addPortfolioChart(container, "Aylık gerçekleşmiş gelir", "Satış kâr/zararı + net temettü",
            com.portfolio.app.ui.chart.PortfolioChartView.Type.BAR, monthly, monthlyLabels, 220,
            suffix = if (selectedSummaryCurrency == "TRY") " ₺" else " $")
    }

    private fun renderMarketAssets(pageId: Int) {
        val list = pageLists[pageId] ?: return
        list.removeAllViews()
        val chartAssets = if (pageId == NAV_BIST) currentAssets.filter {
            it.category.uppercase() in listOf("STOCK", "BIST", "HISSE", "US_STOCK")
        } else currentAssets.filter { it.category.equals("CRYPTO", true) }
        listOf("TRY", "USD").forEach { code ->
            val currencyAssets = chartAssets.filter { it.currency.equals(code, true) && it.quantity > 0.0 }
            if (currencyAssets.isNotEmpty()) {
                val marketValues = currencyAssets.map { it.quantity * (it.lastPrice ?: it.costPrice) }
                val invested = currencyAssets.sumOf { it.quantity * it.costPrice }
                val marketTotal = marketValues.sum()
                val pnlTotal = marketTotal - invested
                val returnPct = if (invested > 0.0) pnlTotal / invested * 100.0 else 0.0
                val summaryCard = com.google.android.material.card.MaterialCardView(this).apply {
                    radius = dp(14).toFloat()
                    setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
                    strokeWidth = dp(1)
                    setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.portfolio_blue)))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                }
                summaryCard.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13))
                    addView(TextView(this@MainActivity).apply {
                        text = "${currencyAssets.size} varlık • ${code} portföy özeti"
                        textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "Piyasa değeri: ${currency(marketTotal, code)}\nYatırım maliyeti: ${currency(invested, code)}\nToplam K/Z: ${currency(pnlTotal, code)} (${String.format(Locale("tr", "TR"), "%+.2f%%", returnPct)})"
                        textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(ContextCompat.getColor(this@MainActivity, if (pnlTotal < 0.0) R.color.portfolio_orange else R.color.portfolio_green))
                        setPadding(0, dp(6), 0, 0)
                    })
                })
                list.addView(summaryCard)
                addPortfolioChart(list, "Piyasa değeri dağılımı", "Varlık bazında • $code",
                    com.portfolio.app.ui.chart.PortfolioChartView.Type.DONUT, marketValues,
                    currencyAssets.map { it.ticker }, 210, currency(marketValues.sum(), code))
                val pnlValues = currencyAssets.map { ((it.lastPrice ?: it.costPrice) - it.costPrice) * it.quantity }
                addPortfolioChart(list, "Varlık bazında kâr / zarar", "Pozisyon bazında • $code",
                    com.portfolio.app.ui.chart.PortfolioChartView.Type.HORIZONTAL_BAR, pnlValues,
                    currencyAssets.map { it.ticker }, maxOf(150, currencyAssets.size * 36 + 20),
                    suffix = if (code == "TRY") " ₺" else " $")
            }
        }
        chartAssets.take(6).forEach { asset ->
            val history = currentPriceSnapshots.filter { it.assetId == asset.id }.sortedBy { it.snapshotDate }.takeLast(30)
            if (history.size >= 2) addPortfolioChart(list, "${asset.ticker} fiyat trendi", "Son ${history.size} kayıt",
                com.portfolio.app.ui.chart.PortfolioChartView.Type.SPARK, history.map { it.price },
                history.map { SimpleDateFormat("dd.MM", Locale("tr", "TR")).format(Date(it.snapshotDate)) }, 54)
        }
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
        val history = currentPriceSnapshots.filter { it.assetId == asset.id }.sortedBy { it.snapshotDate }.takeLast(30)
        if (history.size >= 2) column.addView(com.portfolio.app.ui.chart.PortfolioChartView(this).apply {
            type = com.portfolio.app.ui.chart.PortfolioChartView.Type.SPARK
            values = history.map { it.price }
            layoutParams = LinearLayout.LayoutParams(-1, dp(42))
        })
        column.addView(actionButton("Varlığı düzenle", R.color.portfolio_blue) { showEditAssetDialog(asset) })
        card.addView(column); list.addView(card)
    }


    private fun renderTopRates() {
        val rows = listOf(binding.topRatesRowOne, binding.topRatesRowTwo)
        rows.forEach { it.removeAllViews() }
        val definitions = listOf("Dolar / TL", "Euro / TL", "Gram Altın", "Gram Gümüş")
        val latestTime = currentRates.maxOfOrNull { it.updatedAt }
        val timeText = latestTime?.let {
            SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(it))
        } ?: "Henüz veri yok"
        binding.tvTopRatesStatus.text = when {
            ratesSyncing && latestTime != null -> "⟳ Güncelleniyor • Son: $timeText"
            ratesSyncing -> "⟳ Kurlar alınıyor..."
            latestTime != null -> "Son: $timeText • Yenile"
            else -> "Kur verisi bekleniyor • Yenile"
        }
        binding.tvTopRatesStatus.setTextColor(ContextCompat.getColor(
            this, if (ratesSyncing) R.color.portfolio_blue else R.color.portfolio_muted
        ))

        definitions.forEachIndexed { index, title ->
            val rate = currentRates.firstOrNull { it.title.equals(title, true) }
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = dp(15).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
                setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.portfolio_blue)))
                strokeWidth = dp(1)
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(0, dp(102), 1f).apply {
                    if (index % 2 == 0) marginEnd = dp(5) else marginStart = dp(5)
                    if (index >= 2) topMargin = dp(8)
                }
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(10), dp(8))
            }
            column.addView(TextView(this).apply {
                text = when (index) { 0 -> "USD/TRY"; 1 -> "EUR/TRY"; 2 -> "ALTIN (GR)"; else -> "GÜMÜŞ (GR)" }
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            })
            column.addView(TextView(this).apply {
                text = rate?.let { currency(it.value, it.currency) } ?: "—"
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                fontFeatureSettings = "tnum"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
                setPadding(0, dp(5), 0, dp(2))
            })
            val change = rate?.dailyChangePercent
            column.addView(TextView(this).apply {
                text = change?.let { String.format(Locale("tr", "TR"), "%+.2f%%", it) } ?: "Günlük değişim —"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(
                    this@MainActivity,
                    if ((change ?: 0.0) < 0.0) R.color.portfolio_orange else R.color.portfolio_green
                ))
            })
            card.addView(column)
            rows[index / 2].addView(card)
        }
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

    private fun addPortfolioChart(
        container: LinearLayout, title: String, subtitle: String,
        type: com.portfolio.app.ui.chart.PortfolioChartView.Type,
        values: List<Double>, labels: List<String>, height: Int = 220,
        center: String = "", suffix: String = ""
    ) {
        if (values.isEmpty()) return
        container.addView(TextView(this).apply {
            text = title
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
            setPadding(0, dp(18), 0, dp(3))
        })
        if (subtitle.isNotBlank()) container.addView(TextView(this).apply {
            text = subtitle; textSize = 11f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            setPadding(0, 0, 0, dp(6))
        })
        val chart = com.portfolio.app.ui.chart.PortfolioChartView(this).apply {
            this.type = type; this.values = values; this.labels = labels
            centerValue = center; this.suffix = suffix
            val chartHeight = if (type == com.portfolio.app.ui.chart.PortfolioChartView.Type.DONUT) {
                maxOf(height, labels.size.coerceAtMost(8) * 30 + 28)
            } else height
            layoutParams = LinearLayout.LayoutParams(-1, dp(chartHeight))
        }
        container.addView(chart)
    }

    private fun renderBreakdownPages() {
        val categoryContainer = breakdownContainers[NAV_CATEGORIES]
        categoryContainer?.removeAllViews()
        if (categoryContainer != null) {
            if (currentAssets.isEmpty()) {
                categoryContainer.addView(TextView(this).apply {
                    text = "Kategori grafikleri için portföye varlık ekleyin."
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                })
            } else listOf("TRY", "USD").forEach { code ->
                val rows = currentAssets.filter { it.currency.equals(code, true) && it.quantity > 0.0 }
                if (rows.isNotEmpty()) {
                    val grouped = rows.groupBy { categoryLabel(it.category) }.mapValues { (_, items) ->
                        items.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) }
                    }.entries.sortedByDescending { it.value }
                    addPortfolioChart(categoryContainer, "Kategori dağılımı", "Piyasa değeri • $code",
                        com.portfolio.app.ui.chart.PortfolioChartView.Type.DONUT,
                        grouped.map { it.value }, grouped.map { it.key }, 210,
                        currency(grouped.sumOf { it.value }, code))
                    addPortfolioChart(categoryContainer, "Kategori bazında piyasa değeri", "Yatay karşılaştırma • $code",
                        com.portfolio.app.ui.chart.PortfolioChartView.Type.HORIZONTAL_BAR,
                        grouped.map { it.value }, grouped.map { it.key },
                        maxOf(150, grouped.size * 38 + 20), suffix = if (code == "TRY") " ₺" else " $")
                }
            }
        }

        val brokerContainer = breakdownContainers[NAV_BROKERS]
        brokerContainer?.removeAllViews()
        if (brokerContainer != null) {
            val brokers = currentAssets.groupBy { it.broker.ifBlank { "Belirtilmedi" } }
            if (brokers.isEmpty()) {
                brokerContainer.addView(TextView(this).apply {
                    text = "Aracı kurum analizi için portföye varlık ekleyin."
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                })
            } else listOf("TRY", "USD").forEach { code ->
                val data = brokers.mapNotNull { (broker, rows) ->
                    val matching = rows.filter { it.currency.equals(code, true) && it.quantity > 0.0 }
                    if (matching.isEmpty()) null else broker to matching.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) }
                }.sortedByDescending { it.second }
                if (data.isNotEmpty()) {
                    addPortfolioChart(brokerContainer, "Aracı kurumlara göre portföy", "Piyasa değeri • $code",
                        com.portfolio.app.ui.chart.PortfolioChartView.Type.DONUT,
                        data.map { it.second }, data.map { it.first }, 210,
                        currency(data.sumOf { it.second }, code))
                    addPortfolioChart(brokerContainer, "Aracı kurum karşılaştırması", "Yatay piyasa değeri • $code",
                        com.portfolio.app.ui.chart.PortfolioChartView.Type.HORIZONTAL_BAR,
                        data.map { it.second }, data.map { it.first },
                        maxOf(150, data.size * 38 + 20), suffix = if (code == "TRY") " ₺" else " $")
                    data.forEach { (broker, _) ->
                        val ids = brokers[broker].orEmpty().map { it.id }.toSet()
                        val assets = currentAssets.filter { it.id in ids && it.currency.equals(code, true) }
                        val market = assets.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) }
                        val unrealized = assets.sumOf { it.quantity * ((it.lastPrice ?: it.costPrice) - it.costPrice) }
                        val realized = currentTrades.filter { it.assetId in ids && it.action == "SELL" && it.currency.equals(code, true) }.sumOf { it.realizedProfit }
                        val dividend = currentDividends.filter { it.assetId in ids && it.currency.equals(code, true) }.sumOf { it.netAmount }
                        val capital = assets.sumOf { it.openingQuantity * it.openingCostPrice } +
                            currentTrades.filter { it.assetId in ids && it.action == "BUY" && it.currency.equals(code, true) }.sumOf { it.quantity * it.unitPrice + it.fee }
                        val pnl = unrealized + realized + dividend
                        val pct = if (capital > 0.0) pnl / capital * 100.0 else 0.0
                        val card = com.google.android.material.card.MaterialCardView(this).apply {
                            radius = dp(14).toFloat()
                            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_surface))
                            strokeWidth = dp(1)
                            setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.portfolio_blue)))
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                        }
                        card.addView(LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(12), dp(15), dp(12))
                            addView(TextView(this@MainActivity).apply {
                                text = "$broker • ${assets.size} varlık"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_text))
                            })
                            addView(TextView(this@MainActivity).apply {
                                text = "Piyasa değeri: ${currency(market, code)}\nGerçekleşmemiş K/Z: ${currency(unrealized, code)}\nGerçekleşmiş K/Z: ${currency(realized, code)}\nNet temettü: ${currency(dividend, code)}\nToplam getiri: ${currency(pnl, code)} (${String.format(Locale("tr", "TR"), "%+.2f%%", pct)})"
                                textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                                setPadding(0, dp(5), 0, 0)
                            })
                        })
                        brokerContainer.addView(card)
                    }
                }
            }
        }
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
                addPortfolioChart(container, "Varlık dağılımı", "Güncel piyasa değerine göre • $code",
                    com.portfolio.app.ui.chart.PortfolioChartView.Type.DONUT, market, group.map { it.ticker }, 220,
                    currency(market.sum(), code))
                addPortfolioChart(container, "Gerçekleşmemiş kâr / zarar", "Ortalama maliyet ile son fiyat farkı • $code",
                    com.portfolio.app.ui.chart.PortfolioChartView.Type.HORIZONTAL_BAR, unrealized, group.map { it.ticker },
                    maxOf(160, group.size * 36 + 20), suffix = if (code == "TRY") " ₺" else " $")
                val realized = currentTrades.filter { it.action == "SELL" && it.currency.equals(code, true) }.sumOf { it.realizedProfit }
                val dividends = currentDividends.filter { it.currency.equals(code, true) }.sumOf { it.netAmount }
                val pnlCard = TextView(this).apply {
                    text = "Gerçekleşmiş satış K/Z: ${currency(realized, code)}\nNet temettü: ${currency(dividends, code)}\nToplam gerçekleşmiş gelir: ${currency(realized + dividends, code)}"
                    textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
                    setPadding(0, dp(8), 0, dp(4))
                }
                container.addView(pnlCard)
            }
        }
        listOf("TRY", "USD").forEach { code ->
            val monthlyLabels = (0..5).map { offset ->
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset - 5) }
                String.format(Locale("tr", "TR"), "%02d/%02d", cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR) % 100)
            }
            val monthlyValues = (0..5).map { offset ->
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, offset - 5) }
                val month = cal.get(java.util.Calendar.MONTH); val year = cal.get(java.util.Calendar.YEAR)
                val sells = currentTrades.filter { trade ->
                    val date = java.util.Calendar.getInstance().apply { timeInMillis = trade.executedAt }
                    trade.action == "SELL" && trade.currency.equals(code, true) &&
                        date.get(java.util.Calendar.MONTH) == month && date.get(java.util.Calendar.YEAR) == year
                }.sumOf { it.realizedProfit }
                val divs = currentDividends.filter { dividend ->
                    val date = java.util.Calendar.getInstance().apply { timeInMillis = dividend.paymentDate }
                    dividend.currency.equals(code, true) && date.get(java.util.Calendar.MONTH) == month &&
                        date.get(java.util.Calendar.YEAR) == year
                }.sumOf { it.netAmount }
                sells + divs
            }
            if (monthlyValues.any { it != 0.0 }) addPortfolioChart(container, "Aylık gerçekleşmiş gelir", "Satış K/Z + net temettü • $code",
                com.portfolio.app.ui.chart.PortfolioChartView.Type.BAR, monthlyValues, monthlyLabels, 220,
                suffix = if (code == "TRY") " ₺" else " $")
        }
        active.sortedByDescending { it.quantity * (it.lastPrice ?: it.costPrice) }.take(8).forEach { asset ->
            val history = currentPriceSnapshots.filter { it.assetId == asset.id }.sortedBy { it.snapshotDate }.takeLast(30)
            if (history.size >= 2) {
                val labels = history.map { SimpleDateFormat("dd.MM", Locale("tr", "TR")).format(Date(it.snapshotDate)) }
                addPortfolioChart(container, asset.ticker + " fiyat geçmişi", "Son ${history.size} kayıt • ${asset.currency}",
                    com.portfolio.app.ui.chart.PortfolioChartView.Type.AREA, history.map { it.price }, labels, 210,
                    suffix = if (asset.currency == "TRY") " ₺" else " $")
            }
        }
        container.addView(TextView(this).apply {
            text = "Fiyat geçmişi 4.0 sürümünden itibaren cihazda biriktirilir; geçmişe dönük veri üretilmez. İlk fiyat karşılaştırması için en az iki farklı gün kaydı gerekir."
            textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            setPadding(0, dp(16), 0, dp(24))
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

    private fun tradeTypeLabel(action: String) = when(action) {
        "BUY" -> "ALIŞ"
        "SELL" -> "SATIŞ"
        "CASH_IN" -> "PARA GİRİŞİ"
        "CASH_OUT" -> "PARA ÇIKIŞI"
        "TRANSFER" -> "TRANSFER"
        "DIVIDEND" -> "TEMETTÜ"
        else -> action
    }

    private fun categoryLabel(value: String) = when (value.uppercase(Locale.ROOT)) {
        "STOCK", "BIST", "HISSE" -> "Hisse (BIST)"
        "US_STOCK" -> "Amerikan Borsaları"
        "FUND" -> "Yatırım Fonu"
        "CRYPTO" -> "Kripto"
        "FX" -> "Döviz"
        "GOLD" -> "Altın"
        "COMMODITY" -> "Emtia"
        "BOND" -> "Tahvil / Bono"
        "BANK" -> "Banka"
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
        val categoryCodes = listOf("STOCK", "US_STOCK", "FUND", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "BANK", "CASH", "OTHER")
        val categoryLabels = listOf("Hisse (BIST)", "Amerikan Borsaları", "Yatırım Fonu", "Kripto Para", "Döviz", "Altın", "Emtia", "Tahvil / Bono", "Banka", "Nakit", "Diğer")
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

    private fun showAddTransactionDialog() {
        if (currentAssets.isEmpty()) {
            Toast.makeText(this, "İşlem eklemeden önce bir varlık oluşturun.", Toast.LENGTH_LONG).show()
            showAddAssetDialog()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val actionCodes = listOf("BUY", "SELL", "CASH_IN", "CASH_OUT", "TRANSFER", "DIVIDEND")
        val actionLabels = listOf("Alış", "Satış", "Para Girişi", "Para Çıkışı", "Transfer", "Temettü")
        val typeSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, actionLabels)
        }
        var selectableAssets = currentAssets
        val assetSpinner = android.widget.Spinner(this)
        fun refreshAssets(items: List<AssetEntity>) {
            selectableAssets = items
            assetSpinner.adapter = android.widget.ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                items.map { "${it.ticker} — ${it.name} (${it.currency} • ${it.broker})" }
            )
        }
        refreshAssets(currentAssets)
        fun field(hintText: String) = android.widget.EditText(this).apply {
            hint = hintText
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val quantity = field("Miktar / tutar")
        val price = field("Birim fiyat")
        val fee = field("Komisyon / stopaj")
        val destination = android.widget.EditText(this).apply {
            hint = "Hedef hesap / aracı kurum"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val note = android.widget.EditText(this).apply {
            hint = "Açıklama / not (isteğe bağlı)"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted))
            setPadding(0, dp(8), 0, dp(2))
        }
        box.addView(label("İşlem türü"))
        box.addView(typeSpinner)
        box.addView(label("Varlık / hesap"))
        box.addView(assetSpinner)
        box.addView(quantity)
        box.addView(price)
        box.addView(fee)
        box.addView(destination)
        box.addView(note)

        fun updateFields() {
            val action = actionCodes[typeSpinner.selectedItemPosition.coerceIn(0, actionCodes.lastIndex)]
            val cashAction = action == "CASH_IN" || action == "CASH_OUT"
            val eligible = if (cashAction) currentAssets.filter {
                it.category.equals("CASH", true) || it.category.equals("BANK", true) || it.ticker.equals("TRY", true) || it.ticker.equals("USD", true)
            } else currentAssets
            refreshAssets(eligible.ifEmpty { currentAssets })
            quantity.visibility = if (action == "TRANSFER") View.GONE else View.VISIBLE
            price.visibility = if (action in setOf("BUY", "SELL")) View.VISIBLE else View.GONE
            fee.visibility = if (action in setOf("BUY", "SELL", "DIVIDEND")) View.VISIBLE else View.GONE
            destination.visibility = if (action == "TRANSFER") View.VISIBLE else View.GONE
            quantity.hint = when (action) {
                "CASH_IN", "CASH_OUT" -> "Tutar (${selectableAssets.firstOrNull()?.currency ?: "TRY"})"
                "DIVIDEND" -> "Brüt temettü tutarı"
                else -> "Miktar"
            }
            price.hint = "Birim fiyat (${selectableAssets.firstOrNull()?.currency ?: "TRY"})"
            fee.hint = if (action == "DIVIDEND") "Stopaj oranı (%)" else "Komisyon / masraf"
            if (action == "DIVIDEND" && fee.text.isNullOrBlank()) fee.setText("0")
            if (action == "BUY" || action == "SELL") {
                quantity.hint = if (action == "BUY") "Alınacak miktar" else "Satılacak miktar"
            }
        }
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateFields()
        }
        updateFields()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Yeni işlem")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val action = actionCodes[typeSpinner.selectedItemPosition.coerceIn(0, actionCodes.lastIndex)]
                val asset = selectableAssets.getOrNull(assetSpinner.selectedItemPosition)
                if (asset == null) {
                    Toast.makeText(this, "İşlem için uygun varlık seçin.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val qtyOrAmount = parseNumber(quantity.text.toString())
                val unitPrice = parseNumber(price.text.toString())
                val commission = parseNumber(fee.text.toString()) ?: 0.0
                val memo = note.text.toString().trim()
                when (action) {
                    "BUY" -> {
                        if (qtyOrAmount == null || qtyOrAmount <= 0.0 || unitPrice == null || unitPrice < 0.0 || commission < 0.0) {
                            Toast.makeText(this, "Alış miktarı, birim fiyat ve komisyonu kontrol edin.", Toast.LENGTH_LONG).show()
                        } else viewModel.buyAsset(asset, qtyOrAmount, unitPrice, commission, memo) { result ->
                            runOnUiThread {
                                result.onSuccess { Toast.makeText(this, "Alış işlemi kaydedildi.", Toast.LENGTH_LONG).show() }
                                    .onFailure { Toast.makeText(this, it.message ?: "Alış kaydedilemedi.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                    "SELL" -> {
                        if (qtyOrAmount == null || qtyOrAmount <= 0.0 || unitPrice == null || unitPrice < 0.0 || commission < 0.0) {
                            Toast.makeText(this, "Satış miktarı, birim fiyat ve komisyonu kontrol edin.", Toast.LENGTH_LONG).show()
                        } else viewModel.sellAsset(asset.id, qtyOrAmount, unitPrice, commission, memo.ifBlank { "Satış işlemi" }) { result ->
                            runOnUiThread {
                                result.onSuccess { Toast.makeText(this, "Satış işlemi kaydedildi.", Toast.LENGTH_LONG).show() }
                                    .onFailure { Toast.makeText(this, it.message ?: "Satış kaydedilemedi.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                    "CASH_IN", "CASH_OUT" -> {
                        if (qtyOrAmount == null || qtyOrAmount <= 0.0) {
                            Toast.makeText(this, "Pozitif bir tutar girin.", Toast.LENGTH_LONG).show()
                        } else viewModel.addCashFlow(asset.id, action, qtyOrAmount, memo) { result ->
                            runOnUiThread {
                                result.onSuccess { Toast.makeText(this, if (action == "CASH_IN") "Para girişi kaydedildi." else "Para çıkışı kaydedildi.", Toast.LENGTH_LONG).show() }
                                    .onFailure { Toast.makeText(this, it.message ?: "Para hareketi kaydedilemedi.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                    "TRANSFER" -> {
                        val target = destination.text.toString().trim()
                        if (target.isBlank()) {
                            Toast.makeText(this, "Hedef hesap / aracı kurum girin.", Toast.LENGTH_LONG).show()
                        } else viewModel.transferAsset(asset.id, target, memo) { result ->
                            runOnUiThread {
                                result.onSuccess { Toast.makeText(this, "Transfer kaydedildi.", Toast.LENGTH_LONG).show() }
                                    .onFailure { Toast.makeText(this, it.message ?: "Transfer kaydedilemedi.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                    "DIVIDEND" -> {
                        val gross = qtyOrAmount
                        if (gross == null || gross <= 0.0 || commission !in 0.0..100.0) {
                            Toast.makeText(this, "Pozitif brüt temettü ve %0–100 stopaj oranı girin.", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.addDividend(asset.ticker, gross, commission, asset.currency, memo, asset.broker)
                            Toast.makeText(this, "Temettü işlemi kaydediliyor.", Toast.LENGTH_SHORT).show()
                        }
                    }
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
            val result = if (trade.action == "SELL") "\nGerçekleşen K/Z: ${currency(trade.realizedProfit, trade.currency)}" else if (trade.action == "DIVIDEND") "\nNet temettü: ${currency(trade.quantity * trade.unitPrice - trade.fee, trade.currency)}" else ""
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
        val filteredTrades = currentTrades.filter { trade ->
            val asset = assets[trade.assetId]
            val symbol = asset?.ticker ?: "Varlık #${trade.assetId}"
            val actionLabel = when (trade.action) {
                "BUY" -> "Alış"; "SELL" -> "Satış"; "CASH_IN" -> "Para Girişi"
                "CASH_OUT" -> "Para Çıkışı"; "TRANSFER" -> "Transfer"; "DIVIDEND" -> "Temettü"
                else -> trade.action
            }
            val matchesAction = tradeActionFilter == "Tümü" || actionLabel == tradeActionFilter
            val matchesCurrency = tradeCurrencyFilter == "Tümü" || trade.currency.equals(tradeCurrencyFilter, true)
            val query = tradeSearchQuery.trim()
            val matchesQuery = query.isBlank() || symbol.contains(query, true) || asset?.name.orEmpty().contains(query, true) || asset?.broker.orEmpty().contains(query, true) || trade.note.contains(query, true)
            val now = System.currentTimeMillis()
            val dateCutoff = when (tradeDateFilter) { "7 gün" -> now - 7L * 86_400_000L; "30 gün" -> now - 30L * 86_400_000L; "90 gün" -> now - 90L * 86_400_000L; "Bu yıl" -> java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis; else -> Long.MIN_VALUE }
            val matchesDate = trade.executedAt >= dateCutoff
            matchesAction && matchesCurrency && matchesQuery && matchesDate
        }
        if (filteredTrades.isEmpty()) list.addView(TextView(this).apply { text = "Seçili filtrelerle eşleşen işlem bulunamadı."; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(12), 0, dp(12)) })
        filteredTrades.forEach { trade ->
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
                    if (trade.action == "SELL") "\nGerçekleşen K/Z: ${currency(trade.realizedProfit, trade.currency)}" else if (trade.action == "DIVIDEND") "\nNet temettü: ${currency(trade.quantity * trade.unitPrice - trade.fee, trade.currency)}" else ""
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
        val categories = listOf("STOCK", "US_STOCK", "FUND", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "BANK", "CASH", "OTHER")
        val labels = listOf("Hisse (BIST)", "Amerikan Borsaları", "Yatırım Fonu", "Kripto", "Döviz", "Altın", "Emtia", "Tahvil / Bono", "Banka", "Nakit", "Diğer")
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
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Alış", "Satış", "Para Girişi", "Para Çıkışı", "Transfer", "Temettü"))
            setSelection(when (trade.action) { "SELL" -> 1; "CASH_IN" -> 2; "CASH_OUT" -> 3; "TRANSFER" -> 4; "DIVIDEND" -> 5; else -> 0 })
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
        val quantity = field(if (trade.action == "DIVIDEND") "Adet / hisse başı temettü" else "Miktar", trade.quantity.toString())
        val price = field(if (trade.action == "DIVIDEND") "Hisse başı temettü" else "Birim fiyat", trade.unitPrice.toString())
        val fee = field(if (trade.action == "DIVIDEND") "Toplam stopaj tutarı" else "Komisyon", trade.fee.toString())
        val note = android.widget.EditText(this).apply { hint = "Not"; setText(trade.note); isSingleLine = true; inputType = android.text.InputType.TYPE_CLASS_TEXT }
        box.addView(TextView(this).apply { text = "İşlem türü"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)) })
        box.addView(action); box.addView(TextView(this).apply { text = "İşlem tarihi ve saati"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.portfolio_muted)); setPadding(0, dp(8), 0, dp(2)) }); box.addView(dateButton); box.addView(quantity); box.addView(price); box.addView(fee); box.addView(note)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("İşlemi düzenle").setView(box)
            .setNegativeButton("İptal", null).setPositiveButton("Kaydet") { _, _ ->
                val qty = parseNumber(quantity.text.toString()); val unitPrice = parseNumber(price.text.toString()); val commission = parseNumber(fee.text.toString()) ?: 0.0
                if (qty == null || qty <= 0.0 || unitPrice == null || unitPrice < 0.0 || commission < 0.0) Toast.makeText(this, "Miktar, fiyat ve komisyon değerlerini kontrol edin.", Toast.LENGTH_LONG).show()
                else if (trade.action == "DIVIDEND" && commission > qty * unitPrice) Toast.makeText(this, "Stopaj brüt temettü tutarından yüksek olamaz.", Toast.LENGTH_LONG).show()
                 else viewModel.editTrade(trade.id, trade.action, qty, unitPrice, commission, note.text.toString(), executedAt) { result -> runOnUiThread {
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
