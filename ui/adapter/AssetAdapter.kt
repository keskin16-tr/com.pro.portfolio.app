package com.portfolio.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.databinding.ItemAssetBinding
import java.text.NumberFormat
import java.util.Locale

class AssetAdapter(private val onDeleteClick: (AssetEntity) -> Unit) : ListAdapter<AssetEntity, AssetAdapter.AssetViewHolder>(AssetDiffCallback()) {
    inner class AssetViewHolder(private val binding: ItemAssetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: AssetEntity) {
            binding.tvAssetName.text = asset.name
            binding.tvAssetTicker.text = asset.ticker.uppercase(Locale.ROOT)
            binding.tvAssetQuantity.text = "Miktar: ${formatNumber(asset.quantity)}"
            binding.tvAssetCost.text = "Birim maliyet: ${currency(asset.costPrice)}"
            binding.tvTotalCost.text = "Toplam maliyet  ${currency(asset.quantity * asset.costPrice)}"
            binding.btnDelete.setOnClickListener { onDeleteClick(asset) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssetViewHolder(binding)
    }
    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) = holder.bind(getItem(position))
    private fun currency(value: Double) = NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(value)
    private fun formatNumber(value: Double) = NumberFormat.getNumberInstance(Locale("tr", "TR")).format(value)
    class AssetDiffCallback : DiffUtil.ItemCallback<AssetEntity>() {
        override fun areItemsTheSame(oldItem: AssetEntity, newItem: AssetEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AssetEntity, newItem: AssetEntity) = oldItem == newItem
    }
}
