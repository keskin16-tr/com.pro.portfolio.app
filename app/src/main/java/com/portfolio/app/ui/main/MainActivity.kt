package com.portfolio.app.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.portfolio.app.R
import com.portfolio.app.databinding.ActivityMainBinding
import com.portfolio.app.ui.adapter.AssetAdapter
import com.portfolio.app.ui.dialog.AddAssetDialogFragment
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var assetAdapter: AssetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        binding.fabAddAsset.setOnClickListener {
            AddAssetDialogFragment { name, ticker, quantity, costPrice ->
                viewModel.addAsset(name, ticker, quantity, costPrice)
            }.show(supportFragmentManager, "AddAssetDialog")
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assets.collect { assets ->
                    assetAdapter.submitList(assets)
                    val invested = assets.sumOf { it.quantity * it.costPrice }
                    binding.tvTotalValue.text = currency(invested)
                    binding.tvAssetCount.text = getString(R.string.asset_count, assets.size)
                    binding.tvHoldingsCount.text = "${assets.size} kayıt"
                    binding.tvEmptyState.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvChartEmpty.visibility = if (assets.isEmpty()) View.VISIBLE else View.GONE
                    binding.portfolioChart.setValues(assets.map { (it.quantity * it.costPrice).toFloat() })
                }
            }
        }
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetAdapter { asset -> viewModel.deleteAsset(asset) }
        binding.recyclerViewAssets.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = assetAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun currency(value: Double): String = NumberFormat.getCurrencyInstance(Locale("tr", "TR")).format(value)
}
