package com.portfolio.app.data.local
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(tableName="transactions",foreignKeys=[ForeignKey(entity=AssetEntity::class,parentColumns=["id"],childColumns=["assetId"],onDelete=ForeignKey.CASCADE)],indices=[Index("assetId"),Index("executedAt")])
data class TradeEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val assetId:Long,val action:String,val quantity:Double,val unitPrice:Double,val fee:Double=0.0,val executedAt:Long=System.currentTimeMillis(),val note:String="")