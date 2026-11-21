package com.portfolio.app.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface TradeDao {
 @Insert suspend fun insert(trade:TradeEntity):Long
 @Query("SELECT * FROM transactions WHERE assetId=:assetId ORDER BY executedAt DESC,id DESC") fun getForAsset(assetId:Long):Flow<List<TradeEntity>>
 @Query("SELECT * FROM transactions WHERE assetId=:assetId ORDER BY executedAt ASC,id ASC") suspend fun getForAssetOnce(assetId:Long):List<TradeEntity>
 @Query("SELECT * FROM transactions WHERE id=:tradeId LIMIT 1") suspend fun getById(tradeId:Long):TradeEntity?
 @Update suspend fun update(trade:TradeEntity)
 @Delete suspend fun delete(trade:TradeEntity)
 @Query("SELECT * FROM transactions ORDER BY executedAt DESC,id DESC") fun getAll():Flow<List<TradeEntity>>
 @Query("SELECT * FROM transactions ORDER BY executedAt ASC,id ASC") suspend fun getAllOnce():List<TradeEntity>
 @Query("SELECT COUNT(*) FROM transactions") fun count():Flow<Int>
    @Query("DELETE FROM transactions") suspend fun deleteAll()
}
