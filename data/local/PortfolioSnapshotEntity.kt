package com.portfolio.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshotEntity(
    @PrimaryKey val snapshotDate: Long,
    val marketValue: Double,
    val investedValue: Double,
    val assetCount: Int
)
