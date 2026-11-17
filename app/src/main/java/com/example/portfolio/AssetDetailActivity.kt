package com.example.portfolio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.example.portfolio.analytics.bollinger
import com.example.portfolio.analytics.ema
import com.example.portfolio.analytics.rsi
import com.example.portfolio.database.PortfolioDatabase
import com.example.portfolio.model.AssetTypes
import com.example.portfolio.model.PortfolioAsset
import com.example.portfolio.network.ChartRange
import com.example.portfolio.network.PriceService
import com.example.portfolio.ui.AssetDialogs
import com.example.portfolio.ui.CandlestickChart
import com.example.portfolio.ui.Fmt
import com.example.portfolio.ui.IndicatorChart
import com.example.portfolio.ui.MATCH
import com.example.portfolio.ui.Palette
import com.example.portfolio.ui.WRAP
import com.example.portfolio.ui.applySystemBarPadding
import com.example.portfolio.ui.cardBox
import com.example.portfolio.ui.dp
import com.example.portfolio.ui.filledButton
import com.example.portfolio.ui.hbox
import com.example.portfolio.ui.label
import com.example.portfolio.ui.lp
import com.example.portfolio.ui.roundCorners
import com.example.portfolio.ui.transactionRow
import com.example.portfolio.ui.vbox
import com.example.portfolio.util.FxStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Tek bir varlığın ayrıntısı: fiyat, pozisyon, grafik, göstergeler ve işlem geçmişi. */
class AssetDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SYMBOL = "symbol"
        private const val MENU_REFRESH = 1
        private const val MENU_MANUAL_PRICE = 2
        private const val MENU_DELETE = 3

        fun intent(context: Context, symbol: String): Intent =
            Intent(context, AssetDetailActivity::class.java).putExtra(EXTRA_SYMBOL, symbol)
    }

    private lateinit var db: PortfolioDatabase
    private lateinit var fxStore: FxStore
    private lateinit var dialogs: AssetDialogs
    private val prices = PriceService()

    private var symbol = ""
    private var asset: PortfolioAsset? = null
    private var range = ChartRange.M6
    private var chartJob: Job? = null
    private var fx: Map<String, Double> = mapOf("TRY" to 1.0)

    private lateinit var toolbar: MaterialToolbar
    private lateinit var subtitleText: TextView
    private lateinit var priceText: TextView
    private lateinit var changeText: TextView
    private lateinit var quoteNote: TextView
    private lateinit var statsBox: LinearLayout
    private lateinit var chartCard: LinearLayout
    private lateinit var chart: CandlestickChart
    private lateinit var chartNote: TextView
    private lateinit var rsiLabel: TextView
    private lateinit var rsiChart: IndicatorChart
