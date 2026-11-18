package com.portfolio.app.data.local
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
@Entity(tableName="assets")
data class AssetEntity(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val name:String,val ticker:String,val quantity:Double,val costPrice:Double,
 val category:String="OTHER",val portfolioName:String="Ana Portföy",
 val currency:String="TRY",val marketSymbol:String="",val lastPrice:Double?=null,val priceUpdatedAt:Long?=null,
 @ColumnInfo(defaultValue="'Belirtilmedi'") val broker:String="Belirtilmedi"
)