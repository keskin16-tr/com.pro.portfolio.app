package com.portfolio.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.repository.PortfolioRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PortfolioRepository

    val assets: StateFlow<List<AssetEntity>>

    init {
        val assetDao = AppDatabase.getDatabase(application).assetDao()
        repository = PortfolioRepository(assetDao)
        assets = repository.allAssets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun addAsset(name: String, ticker: String, quantity: Double, costPrice: Double) {
        viewModelScope.launch {
            repository.insert(AssetEntity(name = name, ticker = ticker, quantity = quantity, costPrice = costPrice))
        }
    }

    fun deleteAsset(asset: AssetEntity) {
        viewModelScope.launch {
            repository.delete(asset)
        }
    }
}
