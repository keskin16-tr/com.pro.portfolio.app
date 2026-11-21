package com.portfolio.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioSnapshotDao {
    @Query("SELECT * FROM portfolio_snapshots ORDER BY snapshotDate ASC")
    fun getAll(): Flow<List<PortfolioSnapshotEntity>>

    @Query("SELECT MAX(snapshotDate) FROM portfolio_snapshots")
    suspend fun latestDate(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: PortfolioSnapshotEntity)
    @Query("DELETE FROM portfolio_snapshots") suspend fun deleteAll()
}
