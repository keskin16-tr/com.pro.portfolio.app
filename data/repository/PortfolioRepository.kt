package com.portfolio.app.data.repository

import com.portfolio.app.data.local.AssetDao
import com.portfolio.app.data.local.AssetEntity
import kotlinx.coroutines.flow.Flow

class PortfolioRepository(private val assetDao: AssetDao) {
    val allAssets: Flow<List<AssetEntity>> = assetDao.getAllAssets()

    suspend fun insert(asset: AssetEntity) {
        assetDao.insertAsset(asset)
    }

    suspend fun delete(asset: AssetEntity) {
        assetDao.deleteAsset(asset)
    }
}
