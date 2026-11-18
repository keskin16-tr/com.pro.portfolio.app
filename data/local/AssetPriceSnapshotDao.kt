package com.portfolio.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AssetPriceSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: AssetPriceSnapshotEntity)

    @Query("SELECT * FROM asset_price_snapshots WHERE assetId=:assetId AND snapshotDate<:before ORDER BY snapshotDate DESC LIMIT 1")
    suspend fun previousForAsset(assetId: Long, before: Long): AssetPriceSnapshotEntity?

    @Query("SELECT * FROM asset_price_snapshots WHERE assetId=:assetId AND snapshotDate=:date LIMIT 1")
    suspend fun forDate(assetId: Long, date: Long): AssetPriceSnapshotEntity?
}
