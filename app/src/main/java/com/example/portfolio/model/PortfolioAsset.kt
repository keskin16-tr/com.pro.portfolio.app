
package com.example.portfolio.model

data class PortfolioAsset(
    val id: Long = 0,
    val symbol: String,
    val name: String,
    val type: String,
    val quantity: Double,
    val averageCost: Double,
    val currentPrice: Double,
    val currency: String = "TRY"
) {
    val totalCost: Double
        get() = quantity * averageCost

    val marketValue: Double
        get() = quantity * currentPrice

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
