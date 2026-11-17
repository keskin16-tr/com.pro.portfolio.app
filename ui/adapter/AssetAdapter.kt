package com.portfolio.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.databinding.ItemAssetBinding

class AssetAdapter(private val onDeleteClick: (AssetEntity) -> Unit) :
    ListAdapter<AssetEntity, AssetAdapter.AssetViewHolder>(AssetDiffCallback()) {

    inner class AssetViewHolder(private val binding: ItemAssetBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: AssetEntity) {
            binding.tvAssetName.text = asset.name
            binding.tvAssetTicker.text = asset.ticker
            binding.tvAssetQuantity.text = "Miktar: ${asset.quantity}"
            binding.tvAssetCost.text = "Maliyet: ${asset.costPrice} TL"

            val totalCost = asset.quantity * asset.costPrice
            binding.tvTotalCost.text = "Toplam: ${String.format("%.2f", totalCost)} TL"

            binding.btnDelete.setOnClickListener {
                onDeleteClick(asset)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AssetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AssetDiffCallback : DiffUtil.ItemCallback<AssetEntity>() {
        override fun areItemsTheSame(oldItem: AssetEntity, newItem: AssetEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AssetEntity, newItem: AssetEntity): Boolean =
            oldItem == newItem
    }
}
