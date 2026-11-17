package com.portfolio.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,         // Varlık Adı (Örn: Bitcoin, Altın, THYAO)
    val ticker: String,       // Sembol (Örn: BTC, GRAM-ALTIN, THYAO)
    val quantity: Double,     // Miktar (Örn: 0.5, 10.0)
    val costPrice: Double     // Alış Maliyeti (Birim Başına)
)
