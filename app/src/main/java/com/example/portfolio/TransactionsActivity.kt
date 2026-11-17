package com.example.portfolio

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.example.portfolio.database.PortfolioDatabase
import com.example.portfolio.network.PriceService
import com.example.portfolio.ui.AssetDialogs
import com.example.portfolio.ui.MATCH
import com.example.portfolio.ui.Palette
import com.example.portfolio.ui.WRAP
import com.example.portfolio.ui.applySystemBarPadding
import com.example.portfolio.ui.dp
import com.example.portfolio.ui.label
import com.example.portfolio.ui.lp
import com.example.portfolio.ui.transactionRow
import com.example.portfolio.ui.vbox
import com.google.android.material.appbar.MaterialToolbar

/** Tüm varlıkların işlem geçmişi. Bir işleme dokunmak silme onayını açar. */
class TransactionsActivity : AppCompatActivity() {

    private lateinit var db: PortfolioDatabase
    private lateinit var dialogs: AssetDialogs
    private lateinit var summary: TextView
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = PortfolioDatabase(this)
        dialogs = AssetDialogs(
            activity = this,
            db = db,
            prices = PriceService(),
            onChanged = { render() }
        )

        val root = FrameLayout(this).apply { setBackgroundColor(Palette.background) }
        val column = vbox()
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))

        val toolbar = MaterialToolbar(this).apply {
            title = "İşlem geçmişi"
            setTitleTextColor(Palette.textPrimary)
            setBackgroundColor(Palette.background)
            elevation = 0f
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }
        column.addView(toolbar, lp(MATCH, dp(56)))

        val scroll = NestedScrollView(this).apply { isFillViewport = true }
        val content = vbox().apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        column.addView(scroll, lp(MATCH, 0, 1f))

        summary = label("", 12f, Palette.textSecondary)
        content.addView(summary, lp(start = dp(4)))

        listBox = vbox()
        content.addView(listBox, lp(top = dp(8)))

        root.applySystemBarPadding()
        setContentView(root)

        render()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    private fun render() {
        listBox.removeAllViews()

        val transactions = db.getTransactions()
        val currencies = db.getAssets().associate { it.symbol to it.currency }

        if (transactions.isEmpty()) {
            summary.text = ""
            listBox.addView(
                label(
                    "Henüz işlem yok. Bir varlığın detayından alış veya satış ekleyebilirsiniz.",
