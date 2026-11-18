package com.portfolio.app.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface DividendDao{
 @Insert suspend fun insert(dividend:DividendEntity):Long
 @Query("SELECT * FROM dividends ORDER BY paymentDate DESC,id DESC") fun getAll():Flow<List<DividendEntity>>
 @Query("SELECT * FROM dividends WHERE tradeId=:tradeId LIMIT 1") suspend fun getByTradeId(tradeId:Long):DividendEntity?
 @androidx.room.Update suspend fun update(dividend:DividendEntity)
 @androidx.room.Delete suspend fun delete(dividend:DividendEntity)
 @Query("SELECT COALESCE(SUM(netAmount),0) FROM dividends") fun totalNet():Flow<Double>
 @Query("SELECT COALESCE(SUM(netAmount),0) FROM dividends WHERE paymentDate>=:from AND paymentDate<:to") suspend fun netBetween(from:Long,to:Long):Double
}
