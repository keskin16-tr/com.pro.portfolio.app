package com.example.portfolio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.portfolio.database.PortfolioDatabase
import com.example.portfolio.model.AssetTypes
import com.example.portfolio.model.PortfolioAsset
import com.example.portfolio.network.PriceService
import com.example.portfolio.reports.ImportRow
import com.example.portfolio.reports.PortfolioExporter
import com.example.portfolio.ui.AssetDialogs
import com.example.portfolio.ui.Fmt
import com.example.portfolio.ui.MATCH
import com.example.portfolio.ui.Palette
import com.example.portfolio.ui.PortfolioChart
import com.example.portfolio.ui.WRAP
import com.example.portfolio.ui.applySystemBarPadding
import com.example.portfolio.ui.cardBox
import com.example.portfolio.ui.dp
import com.example.portfolio.ui.hbox
import com.example.portfolio.ui.label
import com.example.portfolio.ui.lp
import com.example.portfolio.ui.makeClickable
import com.example.portfolio.ui.oval
import com.example.portfolio.ui.rounded
import com.example.portfolio.ui.textButton
import com.example.portfolio.ui.vbox
import com.example.portfolio.ui.withAlpha
import com.example.portfolio.util.FxStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class SortMode(val label: String) {
        VALUE("Değer"),
        PROFIT("K/Z %"),
        DAILY("Günlük %"),
        NAME("İsim")
    }

    private companion object {
        const val MENU_REFRESH = 1
        const val MENU_HISTORY = 2
        const val MENU_EXPORT = 3
        const val MENU_IMPORT = 4
        const val STALE_AFTER_MS = 5 * 60 * 1000L
    }

    private lateinit var db: PortfolioDatabase
    private lateinit var fxStore: FxStore
    private lateinit var dialogs: AssetDialogs
    private val prices = PriceService()

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var statusText: TextView
    private lateinit var totalValueText: TextView
    private lateinit var dayText: TextView
    private lateinit var profitText: TextView
