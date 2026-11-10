
package com.example.portfolio

import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.portfolio.database.PortfolioDatabase
import com.example.portfolio.model.PortfolioAsset
import com.example.portfolio.network.PriceRepository
import com.example.portfolio.ui.PortfolioChart
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: PortfolioDatabase
    private val prices = PriceRepository()

    private lateinit var content: LinearLayout
    private lateinit var summary: TextView
    private lateinit var profit: TextView
    private lateinit var chart: PortfolioChart
    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    private val formatter =
        NumberFormat.getCurrencyInstance(Locale("tr", "TR"))

    private val background = Color.rgb(11, 18, 32)
    private val panel = Color.rgb(24, 35, 55)
    private val white = Color.WHITE
    private val green = Color.rgb(54, 214, 180)
    private val red = Color.rgb(255, 105, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = PortfolioDatabase(this)

        window.statusBarColor = background
        window.navigationBarColor = background

        buildScreen()
        loadPortfolio()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(background)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 28)
        }

        scroll.addView(content)
        setContentView(scroll)

        val header = TextView(this).apply {
            text = "Portfolio"
            textSize = 30f
            setTextColor(white)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        content.addView(header)

        status = TextView(this).apply {
            text = "Piyasa verileri bekleniyor"
            textSize = 12f
            setTextColor(0xFF9AAAC3.toInt())
        }
        content.addView(status)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(panel)
        }

        val title = TextView(this).apply {
            text = "TOPLAM PORTFÖY DEĞERİ"
            textSize = 12f
            setTextColor(0xFF9AAAC3.toInt())
        }

        summary = TextView(this).apply {
            text = "₺0,00"
            textSize = 32f
            setTextColor(white)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }

        profit = TextView(this).apply {
            text = "Kâr / Zarar: ₺0,00"
            textSize = 16f
            setTextColor(green)
        }

        card.addView(title)
        card.addView(summary)
        card.addView(profit)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                -1, -2
            ).apply { topMargin = 20 }
        )

        chart = PortfolioChart(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                -1, 280
            ).apply {
                topMargin = 20
                bottomMargin = 10
            }
        }
        content.addView(chart)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        buttons.addView(actionButton("Varlık Ekle") {
            showAddAssetDialog()
        }, weightedParams())

        buttons.addView(actionButton("Yenile") {
            refreshPrices()
        }, weightedParams())

        content.addView(buttons)

        content.addView(sectionTitle("VARLIKLAR"))

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(list)

        content.addView(sectionTitle("İŞLEMLER"))

        val transactionButton = actionButton(
            "Alış / Satış İşlemi"
        ) {
            showTransactionDialog()
        }
        content.addView(transactionButton)

        val historyButton = actionButton(
            "İşlem Geçmişi"
        ) {
            showHistory()
        }
        content.addView(historyButton)

        content.addView(
            TextView(this).apply {
                text = "Pro Portfolio Manager 2.0"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(0xFF75849C.toInt())
                setPadding(0, 30, 0, 10)
            }
        )
    }

    private fun weightedParams() =
        LinearLayout.LayoutParams(0, 52, 1f).apply {
            marginEnd = 5
            marginStart = 5
        }

    private fun actionButton(
        text: String,
        action: () -> Unit
    ): View {
        return MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(71, 120, 245))
            setOnClickListener { action() }
        }
    }

    private fun sectionTitle(text: String) =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF9AAAC3.toInt())
            setPadding(0, 24, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun loadPortfolio() {
        val assets = db.getAssets()

        list.removeAllViews()

        val totalValue = assets.sumOf { it.marketValue }
        val totalCost = assets.sumOf { it.totalCost }
        val difference = totalValue - totalCost
        val percent = if (totalCost > 0)
            difference / totalCost * 100 else 0.0

        summary.text = formatter.format(totalValue)

        profit.text = String.format(
            Locale("tr", "TR"),
            "Kâr / Zarar: %s  (%+.2f%%)",
            formatter.format(difference),
            percent
        )

        profit.setTextColor(
            if (difference >= 0) green else red
        )

        chart.setAssets(assets)

        assets.forEach { asset ->
            list.addView(createAssetCard(asset))
        }

        if (assets.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Henüz portföyünüzde varlık bulunmuyor."
                setTextColor(white)
                setPadding(8, 20, 8, 20)
            })
        }
    }

    private fun createAssetCard(
        asset: PortfolioAsset
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(panel)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val name = TextView(this).apply {
            text = "${asset.name} (${asset.symbol})"
            textSize = 16f
            setTextColor(white)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val value = TextView(this).apply {
            text = formatter.format(asset.marketValue)
            textSize = 15f
            setTextColor(white)
        }

        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(value)

        val details = TextView(this).apply {
            text = "Miktar: ${asset.quantity}   " +
                "Maliyet: ${formatter.format(asset.totalCost)}\n" +
                "Fiyat: ${formatter.format(asset.currentPrice)}   " +
                "K/Z: ${formatter.format(asset.profitLoss)} " +
                "(%+.2f%%)".format(asset.profitPercent)

            textSize = 12f
            setTextColor(
                if (asset.profitLoss >= 0) green else red
            )
            setPadding(0, 10, 0, 0)
        }

        card.addView(row)
        card.addView(details)

        card.setOnClickListener {
            showAssetActions(asset)
        }

        card.layoutParams = LinearLayout.LayoutParams(
            -1, -2
        ).apply {
            bottomMargin = 10
        }

        return card
    }

    private fun field(
        hint: String
    ): TextInputEditText {
        return TextInputEditText(this).apply {
            this.hint = hint
            setSingleLine()
            setTextColor(white)
            setHintTextColor(0xFF9AAAC3.toInt())
        }
    }

    private fun showAddAssetDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        val symbol = field("Sembol (BTCUSDT, THYAO.IS)")
        val name = field("Varlık adı")
        val type = Spinner(this)

        val types = listOf(
            "Kripto", "Hisse", "Altın",
            "Gümüş", "Döviz", "Fon", "Nakit"
        )

        type.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            types
        )

        box.addView(symbol)
        box.addView(name)
        box.addView(type)

        MaterialAlertDialogBuilder(this)
            .setTitle("Yeni varlık")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val s = symbol.text.toString()
                    .trim().uppercase()
                val n = name.text.toString().trim()

                if (s.isBlank() || n.isBlank()) {
                    toast("Sembol ve isim gerekli")
                    return@setPositiveButton
                }

                val id = db.addAsset(
                    s, n, types[type.selectedItemPosition]
                )

                if (id == -1L) {
                    toast("Bu sembol zaten kayıtlı")
                } else {
                    loadPortfolio()
                    toast("Varlık eklendi")
                }
            }
            .show()
    }

    private fun showTransactionDialog(
        selected: PortfolioAsset? = null
    ) {
        val assets = db.getAssets()

        if (assets.isEmpty()) {
            toast("Önce bir varlık ekleyin")
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        val assetSpinner = Spinner(this)
        assetSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            assets.map { "${it.name} (${it.symbol})" }
        )

        if (selected != null) {
            assetSpinner.setSelection(
                assets.indexOfFirst { it.id == selected.id }
                    .coerceAtLeast(0)
            )
        }

        val operation = Spinner(this)
        operation.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Alış", "Satış")
        )

        val quantity = field("Adet / Miktar")
        val price = field("Birim fiyat (TL)")
        val commission = field("Komisyon (TL)")

        price.setText(
            selected?.currentPrice?.takeIf { it > 0 }?.toString()
                ?: ""
        )

        box.addView(assetSpinner)
        box.addView(operation)
        box.addView(quantity)
        box.addView(price)
        box.addView(commission)

        MaterialAlertDialogBuilder(this)
            .setTitle("İşlem kaydı")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val asset = assets[assetSpinner.selectedItemPosition]

                val q = quantity.text.toString().toDoubleOrNull()
                val p = price.text.toString().toDoubleOrNull()
                val c = commission.text.toString()
                    .toDoubleOrNull() ?: 0.0

                if (q == null || p == null ||
                    q <= 0 || p <= 0 || c < 0 ||
                    !q.isFinite() || !p.isFinite() ||
                    !c.isFinite()
                ) {
                    toast("Geçerli işlem bilgileri girin")
                    return@setPositiveButton
                }

                val type = if (
                    operation.selectedItemPosition == 0
                ) "BUY" else "SELL"

                val success = db.addTransaction(
                    asset.symbol, type, q, p, c
                )

                if (success) {
                    loadPortfolio()
                    toast("İşlem kaydedildi")
                } else {
                    toast("İşlem başarısız. Satış miktarını kontrol edin.")
                }
            }
            .show()
    }

    private fun showAssetActions(asset: PortfolioAsset) {
        MaterialAlertDialogBuilder(this)
            .setTitle(asset.name)
            .setItems(
                arrayOf(
                    "Alış / Satış",
                    "Fiyatı elle güncelle",
                    "Varlığı sil"
                )
            ) { _, which ->
                when (which) {
                    0 -> showTransactionDialog(asset)
                    1 -> showManualPriceDialog(asset)
                    2 -> {
                        val success = db.deleteAsset(asset.id)
                        toast(
                            if (success)
                                "Varlık silindi"
                            else
                                "İşlem geçmişi olan varlık silinemez"
                        )
                        loadPortfolio()
                    }
                }
            }
            .show()
    }

    private fun showManualPriceDialog(asset: PortfolioAsset) {
        val input = field("Yeni fiyat (TL)")
        input.setText(asset.currentPrice.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle("Fiyat güncelle")
            .setView(input)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val p = input.text.toString().toDoubleOrNull()

                if (p != null && p > 0 && p.isFinite()) {
                    db.updatePrice(asset.symbol, p)
                    loadPortfolio()
                } else {
                    toast("Geçerli fiyat girin")
                }
            }
            .show()
    }

    private fun refreshPrices() {
        lifecycleScope.launch {
            status.text = "Fiyatlar güncelleniyor..."

            val assets = db.getAssets()
            var updated = 0

            for (asset in assets) {
                val price = when (asset.type) {
                    "Kripto" -> prices.getBinancePrice(asset.symbol)
                    "Hisse" -> prices.getYahooPrice(asset.symbol)
                    else -> null
                }

                
                if (price != null && price > 0) {
                    val isTryQuoted = when (asset.type) {
                        "Hisse" -> asset.symbol.endsWith(".IS")
                        "Kripto" -> asset.symbol.endsWith("TRY")
                        else -> false
                    }

                    if (isTryQuoted) {
                        db.updatePrice(asset.symbol, price)
                        updated++
                    }
                }   
            }

            status.text = "Son kontrol: " +
                java.text.SimpleDateFormat(
                    "HH:mm:ss", Locale.getDefault()
                ).format(java.util.Date()) +
                " • $updated fiyat güncellendi"

            loadPortfolio()
        }
    }

    private fun showHistory() {
        val transactions = db.getTransactions()

        val text = transactions.joinToString("\n\n") {
            val date = java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale.getDefault()
            ).format(java.util.Date(it.date))

            val type = if (it.type == "BUY") "ALIŞ" else "SATIŞ"

            "$date\n${it.symbol} • $type\n" +
                "Miktar: ${it.quantity}\n" +
                "Fiyat: ${formatter.format(it.price)}\n" +
                "Komisyon: ${formatter.format(it.commission)}"
        }

        val view = TextView(this).apply {
            this.text = if (text.isBlank())
                "Henüz işlem bulunmuyor." else text
            setTextColor(white)
            setPadding(28, 20, 28, 20)
            textSize = 14f
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("İşlem geçmişi")
            .setView(ScrollView(this).apply {
                addView(view)
            })
            .setPositiveButton("Kapat", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(
            this, message, Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
