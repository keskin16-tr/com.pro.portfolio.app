package com.portfolio.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Insert
    suspend fun insert(trade: TradeEntity)

    @Query("SELECT * FROM transactions ORDER BY executedAt DESC")
    fun observeAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM transactions WHERE assetId = :assetId ORDER BY executedAt DESC")
    fun observeForAsset(assetId: Long): Flow<List<TradeEntity>>
}