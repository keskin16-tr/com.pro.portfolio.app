package com.portfolio.app.data.local
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface AssetDao {
 @Query("SELECT * FROM assets ORDER BY id DESC") fun getAllAssets():Flow<List<AssetEntity>>
 @Query("SELECT * FROM assets ORDER BY id DESC") suspend fun getAssetsOnce():List<AssetEntity>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertAsset(asset:AssetEntity):Long
 @Delete suspend fun deleteAsset(asset:AssetEntity)
 @Query("UPDATE assets SET quantity=:quantity, costPrice=:costPrice WHERE id=:assetId") suspend fun updatePosition(assetId:Long,quantity:Double,costPrice:Double)
 @Query("UPDATE assets SET lastPrice=:price, priceUpdatedAt=:updatedAt WHERE id=:assetId") suspend fun updatePrice(assetId:Long,price:Double,updatedAt:Long)
 @Query("SELECT * FROM assets WHERE id=:assetId LIMIT 1") suspend fun getById(assetId:Long):AssetEntity?
 @Query("UPDATE assets SET category=:category, broker=:broker WHERE id=:assetId") suspend fun updateClassification(assetId:Long,category:String,broker:String)
}
