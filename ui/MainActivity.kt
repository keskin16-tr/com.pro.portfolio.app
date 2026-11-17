package com.portfolio.app.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.portfolio.app.databinding.ActivityMainBinding
import com.portfolio.app.ui.adapter.AssetAdapter
import com.portfolio.app.ui.dialog.AddAssetDialogFragment
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
            val dialog = AddAssetDialogFragment { name, ticker, quantity, costPrice ->
                viewModel.addAsset(name, ticker, quantity, costPrice)
            }
            dialog.show(supportFragmentManager, "AddAssetDialog")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assets.collect { assets ->
                    assetAdapter.submitList(assets)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        assetAdapter = AssetAdapter { asset ->
            viewModel.deleteAsset(asset)
        }
        binding.recyclerViewAssets.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = assetAdapter
        }
    }
}
