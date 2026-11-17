package com.portfolio.app.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.portfolio.app.databinding.DialogAddAssetBinding

class AddAssetDialogFragment(private val onSave: (String, String, Double, Double) -> Unit) :
    BottomSheetDialogFragment() {

    private var _binding: DialogAddAssetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddAssetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val ticker = binding.etTicker.text.toString().trim()
            val quantity = binding.etQuantity.text.toString().toDoubleOrNull()
            val costPrice = binding.etCostPrice.text.toString().toDoubleOrNull()

            if (name.isNotEmpty() && ticker.isNotEmpty() && quantity != null && costPrice != null) {
                onSave(name, ticker, quantity, costPrice)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Lütfen tüm alanları doğru doldurun!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
