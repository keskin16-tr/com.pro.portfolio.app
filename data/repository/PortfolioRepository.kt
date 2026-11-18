package com.portfolio.app.data.repository

import com.portfolio.app.data.local.AssetDao
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.TradeDao
import com.portfolio.app.data.local.TradeEntity
import kotlinx.coroutines.flow.Flow

class PortfolioRepository(private val assetDao: AssetDao, private val tradeDao: TradeDao) {
    val allAssets: Flow<List<AssetEntity>> = assetDao.getAllAssets()
    val allTrades: Flow<List<TradeEntity>> = tradeDao.observeAll()

    suspend fun insert(asset: AssetEntity): Long = assetDao.insertAsset(asset)
    suspend fun delete(asset: AssetEntity) = assetDao.deleteAsset(asset)
    suspend fun updatePrice(id: Long, price: Double) = assetDao.updatePrice(id, price, System.currentTimeMillis())
    suspend fun addTrade(trade: TradeEntity) = tradeDao.insert(trade)
}