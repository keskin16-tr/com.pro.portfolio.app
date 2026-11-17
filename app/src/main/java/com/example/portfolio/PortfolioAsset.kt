package com.example.portfolio.model

data class PortfolioAsset(
    val id: Long = 0,
    val symbol: String,
    val name: String,
    val type: String,
    val quantity: Double,
    val averageCost: Double,
    val currentPrice: Double,
    val currency: String = "TRY",
    val dayChangePercent: Double = 0.0,
    val lastUpdated: Long = 0L,
    val realizedPl: Double = 0.0
) {
    /** Henüz hiç fiyat alınmadıysa false. */
    val hasPrice: Boolean
        get() = currentPrice > 0

    /** Fiyat yoksa değerleme maliyet üzerinden yapılır; böylece yeni eklenen varlık 0 TL görünmez. */
    val valuationPrice: Double
        get() = if (hasPrice) currentPrice else averageCost

    val totalCost: Double
        get() = quantity * averageCost

    val marketValue: Double
        get() = quantity * valuationPrice

    val profitLoss: Double
        get() = marketValue - totalCost

    val profitPercent: Double
        get() = if (totalCost > 0)
            profitLoss / totalCost * 100 else 0.0
}

data class PortfolioTransaction(
    val id: Long = 0,
    val assetId: Long,
    val symbol: String,
    val type: String,
    val quantity: Double,
    val price: Double,
    val commission: Double,
    val date: Long
)
