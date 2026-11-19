package com.portfolio.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "asset_price_snapshots",
    primaryKeys = ["assetId", "snapshotDate"],
    foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("snapshotDate")]
)
data class AssetPriceSnapshotEntity(
    val assetId: Long,
    val snapshotDate: Long,
    val price: Double,
    val currency: String
)
