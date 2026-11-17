package com.example.portfolio.ui

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.portfolio.R
import com.example.portfolio.database.PortfolioDatabase
import com.example.portfolio.model.AssetTypes
import com.example.portfolio.model.PortfolioAsset
import com.example.portfolio.model.PortfolioTransaction
import com.example.portfolio.model.Presets
import com.example.portfolio.model.Quote
import com.example.portfolio.model.SymbolMatch
import com.example.portfolio.network.PriceService
import com.example.portfolio.util.parseNumber
import com.example.portfolio.util.plainNumber
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * Varlık ekleme, alış/satış, elle fiyat girme ve silme diyalogları.
 * Hem ana ekran hem de varlık detay ekranı tarafından kullanılır.
 */
class AssetDialogs(
    private val activity: AppCompatActivity,
    private val db: PortfolioDatabase,
    private val prices: PriceService,
    private val onChanged: () -> Unit,
    private val onAssetAdded: () -> Unit = {}
) {

    private val ctx: Context get() = activity

    private val currencies = listOf("TRY", "USD", "EUR", "GBP", "CHF", "JPY")

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    // ===================================================================
    // Yeni varlık
    // ===================================================================

    fun showAddAsset() {
        val form = ctx.vbox().apply {
            setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(4))
        }
        val scroll = ScrollView(ctx).apply { addView(form) }

        val (typeLayout, typeView) = ctx.dropdown("Tür", AssetTypes.all)
        val (symbolLayout, symbolInput) = ctx.textInput("Sembol")
        val (nameLayout, nameInput) = ctx.textInput("Varlık adı")
        val (currencyLayout, currencyView) = ctx.dropdown("Para birimi", currencies)
        val (qtyLayout, qtyInput) =
            ctx.textInput("Mevcut adet (isteğe bağlı)", DECIMAL_INPUT)
        val (costLayout, costInput) =
            ctx.textInput("Ortalama alış fiyatı (isteğe bağlı)", DECIMAL_INPUT)
        val info = ctx.label("", 12f, Palette.textSecondary)

        var fetched: Quote? = null

        fun currentType(): String = typeView.text.toString()
        fun currentCurrency(): String = currencyView.text.toString().trim().uppercase()
