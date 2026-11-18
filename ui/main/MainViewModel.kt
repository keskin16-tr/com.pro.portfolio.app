package com.portfolio.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.data.remote.MarketDataRepository
import com.portfolio.app.data.repository.PortfolioRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = PortfolioRepository(db.assetDao(), db.tradeDao())
    private val market = MarketDataRepository()

    val assets: StateFlow<List<AssetEntity>> = repository.allAssets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trades: StateFlow<List<TradeEntity>> = repository.allTrades.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _marketMessage = MutableStateFlow("Fiyatlar henüz güncellenmedi")
    val marketMessage = _marketMessage.asStateFlow()

    fun addAsset(name: String, ticker: String, quantity: Double, costPrice: Double) {
        viewModelScope.launch {
            val category = when {
                ticker.equals("USD", true) || ticker.equals("EUR", true) || ticker.equals("GBP", true) -> "FX"
                ticker.equals("XAU", true) || ticker.contains("ALTIN", true) || ticker.equals("GRAM", true) -> "GOLD"
                ticker.equals("BTC", true) || ticker.equals("ETH", true) || ticker.equals("SOL", true) -> "CRYPTO"
                ticker.endsWith(".IS", true) || ticker.matches(Regex("[A-Za-z]{3,6}")) -> "STOCK"
                else -> "OTHER"
            }
            val asset = AssetEntity(name = name, ticker = ticker, quantity = quantity, costPrice = costPrice, category = category)
            val id = repository.insert(asset)
            repository.addTrade(TradeEntity(assetId = id, action = "BUY", quantity = quantity, unitPrice = costPrice, note = "İlk kayıt"))
        }
    }

    fun deleteAsset(asset: AssetEntity) { viewModelScope.launch { repository.delete(asset) } }

    fun refreshPrices() {
        viewModelScope.launch {
            if (_refreshing.value) return@launch
            _refreshing.value = true
            var updated = 0
            var failed = 0
            try {
                assets.value.map { asset ->
                    async {
                        val category = if (asset.category == "OTHER") "STOCK" else asset.category
                        market.quote(asset.ticker, category).onSuccess {
                            repository.updatePrice(asset.id, it.price)
                            updated++
                        }.onFailure { failed++ }
                    }
                }.awaitAll()
                _marketMessage.value = "$updated fiyat güncellendi · $failed başarısız"
            } finally { _refreshing.value = false }
        }
    }
}