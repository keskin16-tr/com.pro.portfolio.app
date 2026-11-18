package com.portfolio.app.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.portfolio.app.databinding.DialogAddAssetBinding
import java.util.Locale

class AddAssetDialogFragment(private val onSave: (String, String, Double, Double, String, String, String, String) -> Unit) : BottomSheetDialogFragment() {
    private var _binding: DialogAddAssetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddAssetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSave.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val ticker = binding.etTicker.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
            val quantity = parseNumber(binding.etQuantity.text?.toString().orEmpty())
            val costPrice = parseNumber(binding.etCostPrice.text?.toString().orEmpty())
            val categories = listOf("STOCK", "US_STOCK", "FUND", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "CASH", "OTHER")
            val category = categories.getOrElse(binding.spinnerCategory.selectedItemPosition) { "OTHER" }
            val currency = if (binding.spinnerCurrency.selectedItemPosition == 1) "USD" else "TRY"
            val priceSource = listOf("AUTO", "YAHOO_FINANCE", "COINGECKO", "FRANKFURTER", "MANUAL").getOrElse(binding.spinnerPriceSource.selectedItemPosition) { "AUTO" }
            val selectedBroker = binding.spinnerBroker.selectedItem?.toString().orEmpty()
            val broker = if (selectedBroker == "Diğer") binding.etBrokerCustom.text?.toString()?.trim().orEmpty().ifBlank { "Belirtilmedi" } else selectedBroker
            if (name.isNotBlank() && ticker.isNotBlank() && quantity != null && costPrice != null && quantity > 0.0 && costPrice >= 0.0) {
                onSave(name, ticker, quantity, costPrice, category, broker, currency, priceSource)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Geçerli ad, sembol, pozitif miktar ve maliyet girin.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parseNumber(raw: String): Double? = raw.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()?.takeIf { it.isFinite() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
