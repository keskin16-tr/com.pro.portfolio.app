package com.portfolio.app.data.local
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(tableName="dividends",foreignKeys=[ForeignKey(entity=AssetEntity::class,parentColumns=["id"],childColumns=["assetId"],onDelete=ForeignKey.CASCADE)],indices=[Index("assetId"),Index("paymentDate")])
data class DividendEntity(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val assetId:Long,
 val ticker:String,
 val quantityAtPayment:Double,
 val grossAmount:Double,
 val withholdingTax:Double,
 val netAmount:Double,
 val currency:String="TRY",
 val exDate:Long=System.currentTimeMillis(),
 val paymentDate:Long=System.currentTimeMillis(),
 val note:String="",
 val tradeId:Long?=null
)
