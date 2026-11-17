package com.example.portfolio.model

/** Varlık türleri. Veritabanında Türkçe etiketleriyle saklanır (eski kayıtlarla uyumlu). */
object AssetTypes {
    const val CRYPTO = "Kripto"
    const val STOCK = "Hisse"
    const val GOLD = "Altın"
    const val SILVER = "Gümüş"
    const val FX = "Döviz"
    const val FUND = "Fon"
    const val CASH = "Nakit"

    val all = listOf(CRYPTO, STOCK, GOLD, SILVER, FX, FUND, CASH)
}

/** Bir varlığın bulunan güncel fiyatı. [currency] fiyatın hangi para biriminde olduğunu söyler. */
data class Quote(
    val price: Double,
    val changePercent: Double?,
    val currency: String
)

/** Sembol aramasından dönen sonuç. */
data class SymbolMatch(
    val symbol: String,
    val name: String,
    val type: String,
    val exchange: String
)

/** "Hızlı ekle" düğmeleri için hazır varlıklar. */
data class QuickPreset(
    val label: String,
    val symbol: String,
    val name: String,
    val type: String,
    val currency: String
)

object Presets {
    val all = listOf(
        QuickPreset("Gram Altın", "GRAM-ALTIN", "Gram Altın", AssetTypes.GOLD, "TRY"),
        QuickPreset("Gram Gümüş", "GRAM-GUMUS", "Gram Gümüş", AssetTypes.SILVER, "TRY"),
        QuickPreset("USD", "USD", "Amerikan Doları", AssetTypes.FX, "TRY"),
        QuickPreset("EUR", "EUR", "Euro", AssetTypes.FX, "TRY"),
        QuickPreset("Bitcoin", "BTCUSDT", "Bitcoin", AssetTypes.CRYPTO, "USD"),
        QuickPreset("Ethereum", "ETHUSDT", "Ethereum", AssetTypes.CRYPTO, "USD"),
        QuickPreset("THYAO", "THYAO.IS", "Türk Hava Yolları", AssetTypes.STOCK, "TRY"),
        QuickPreset("ASELS", "ASELS.IS", "Aselsan", AssetTypes.STOCK, "TRY"),
        QuickPreset("Nakit (TL)", "NAKIT-TRY", "Nakit (TL)", AssetTypes.CASH, "TRY")
    )
}
