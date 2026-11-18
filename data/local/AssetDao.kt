package com.portfolio.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY id DESC")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity): Long

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)

    @Query("UPDATE assets SET lastPrice = :price, priceUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePrice(id: Long, price: Double, updatedAt: Long)
}