package com.portfolio.app.data.repository

import androidx.room.withTransaction
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.PortfolioSnapshotEntity
import com.portfolio.app.data.local.AssetPriceSnapshotEntity
import com.portfolio.app.data.importer.ImportedHolding
import com.portfolio.app.data.importer.ImportedPortfolio
import com.portfolio.app.data.importer.ImportedAsset
import com.portfolio.app.data.importer.ImportedTrade
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class PortfolioRepository(private val database: AppDatabase) {
    private val assetDao = database.assetDao()
    private val tradeDao = database.tradeDao()
    private val dividendDao = database.dividendDao()
    private val snapshotDao = database.portfolioSnapshotDao()
    private val priceSnapshotDao = database.assetPriceSnapshotDao()

    val allAssets: Flow<List<AssetEntity>> = assetDao.getAllAssets()
    val allTrades: Flow<List<TradeEntity>> = tradeDao.getAll()
    val tradeCount: Flow<Int> = tradeDao.count()
    val allDividends: Flow<List<DividendEntity>> = dividendDao.getAll()
    val totalNetDividends: Flow<Double> = dividendDao.totalNet()
    val allSnapshots: Flow<List<PortfolioSnapshotEntity>> = snapshotDao.getAll()

    suspend fun recordBuy(
        name: String, ticker: String, quantity: Double, unitPrice: Double,
        category: String = "OTHER", currency: String = "TRY", fee: Double = 0.0,
        broker: String = "Belirtilmedi", priceSource: String = "AUTO", manualPrice: Double? = null,
        note: String = "Alış işlemi"
    ) {
        require(currency.uppercase() in listOf("TRY", "USD")) { "Varlık para birimi yalnızca TRY veya USD olabilir." }
        if (priceSource == "MANUAL") require(manualPrice != null && manualPrice >= 0.0) { "Elle giriş için güncel fiyat girin." }
        require(quantity > 0.0 && unitPrice >= 0.0 && fee >= 0.0)
        database.withTransaction {
            val existing = assetDao.getAssetsOnce().firstOrNull {
                it.ticker.equals(ticker, ignoreCase = true) &&
                    it.currency.equals(currency, ignoreCase = true) &&
                    it.broker.equals(broker, ignoreCase = true)
            }
            val assetId = existing?.id ?: assetDao.insertAsset(
                AssetEntity(
                    name = name.trim(), ticker = ticker.trim().uppercase(), quantity = 0.0, costPrice = 0.0,
                    category = category, currency = currency, marketSymbol = defaultMarketSymbol(ticker, category),
                    broker = broker, lastPrice = if (priceSource == "MANUAL") manualPrice else unitPrice, priceUpdatedAt = System.currentTimeMillis(), priceSource = priceSource
                )
            )
            val current = assetDao.getById(assetId) ?: error("Varlık kaydı oluşturulamadı")
            require(current.currency.equals(currency, true)) {
                "Aynı sembol farklı para birimiyle zaten kayıtlı. Farklı para birimi için ayrı bir sembol/varlık kaydı kullanın."
            }
            val newQuantity = current.quantity + quantity
            val averageCost = (current.quantity * current.costPrice + quantity * unitPrice + fee) / newQuantity
            tradeDao.insert(TradeEntity(
                assetId = assetId, action = "BUY", quantity = quantity, unitPrice = unitPrice,
                fee = fee, note = note, currency = currency
            ))
            assetDao.updatePosition(assetId, newQuantity, averageCost)
        }
    }

    suspend fun recordSell(assetId: Long, quantity: Double, unitPrice: Double, fee: Double = 0.0, note: String = "Satış işlemi") {
        require(quantity > 0.0 && unitPrice >= 0.0 && fee >= 0.0)
        database.withTransaction {
            val asset = assetDao.getById(assetId) ?: error("Varlık bulunamadı")
            require(quantity <= asset.quantity) { "Satış miktarı mevcut bakiyeden fazla olamaz." }
            val realized = (unitPrice - asset.costPrice) * quantity - fee
            tradeDao.insert(TradeEntity(
                assetId = assetId, action = "SELL", quantity = quantity, unitPrice = unitPrice,
                fee = fee, realizedProfit = realized,
                realizedProfitTry = if (asset.currency == "TRY") realized else 0.0,
                note = note, currency = asset.currency
            ))
            assetDao.updatePosition(assetId, asset.quantity - quantity, asset.costPrice)
        }
    }

    suspend fun recordCashFlow(assetId: Long, action: String, amount: Double, note: String = "") {
        require(action == "CASH_IN" || action == "CASH_OUT") { "Geçersiz para hareketi." }
        require(amount > 0.0 && amount.isFinite()) { "Tutar sıfırdan büyük olmalı." }
        database.withTransaction {
            val asset = assetDao.getById(assetId) ?: error("Nakit varlığı bulunamadı")
            require(asset.category.uppercase() in setOf("CASH", "BANK") || asset.ticker.uppercase() in setOf("TRY", "USD")) {
                "Para girişi/çıkışı için Nakit kategorisindeki varlığı seçin."
            }
            tradeDao.insert(TradeEntity(
                assetId = assetId, action = action, quantity = amount, unitPrice = 1.0,
                fee = 0.0, note = note.ifBlank { if (action == "CASH_IN") "Para girişi" else "Para çıkışı" },
                currency = asset.currency
            ))
            recalculateAsset(assetId)
        }
    }

    suspend fun recordTransfer(assetId: Long, destinationAccount: String, note: String = "") {
        require(destinationAccount.isNotBlank()) { "Hedef hesap/kurum girin." }
        database.withTransaction {
            val asset = assetDao.getById(assetId) ?: error("Varlık bulunamadı")
            val sourceAccount = asset.broker.ifBlank { "Belirtilmedi" }
            require(!sourceAccount.equals(destinationAccount.trim(), true)) { "Kaynak ve hedef hesap aynı olamaz." }
            val description = listOf("Hesap transferi: $sourceAccount → ${destinationAccount.trim()}", note.trim())
                .filter { it.isNotBlank() }.joinToString(" • ")
            tradeDao.insert(TradeEntity(
                assetId = assetId, action = "TRANSFER", quantity = asset.quantity,
                unitPrice = asset.lastPrice ?: asset.costPrice, fee = 0.0,
                note = description, currency = asset.currency
            ))
            assetDao.updateClassification(assetId, asset.category, destinationAccount.trim())
        }
    }

    suspend fun recordDividend(
        ticker: String, grossAmount: Double, taxPercent: Double = 0.0,
        currency: String = "TRY", paymentDate: Long = System.currentTimeMillis(), note: String = "",
        broker: String? = null
    ) {
        require(currency.uppercase() in listOf("TRY", "USD")) { "Temettü para birimi yalnızca TRY veya USD olabilir." }
        require(grossAmount > 0.0 && taxPercent in 0.0..100.0)
        database.withTransaction {
            val asset = assetDao.getAssetsOnce().firstOrNull {
                it.ticker.equals(ticker.trim(), true) &&
                    it.currency.equals(currency, true) &&
                    (broker.isNullOrBlank() || it.broker.equals(broker, true))
            } ?: error("Bu sembol ve hesap için portföy varlığı bulunamadı")
            val tax = grossAmount * taxPercent / 100.0
            val tradeId = tradeDao.insert(TradeEntity(
                assetId = asset.id, action = "DIVIDEND", quantity = 1.0, unitPrice = grossAmount,
                fee = tax, realizedProfit = grossAmount,
                realizedProfitTry = if (currency.equals("TRY", true)) grossAmount else 0.0,
                executedAt = paymentDate, note = note.ifBlank { "Temettü ödemesi" }, currency = currency
            ))
            dividendDao.insert(DividendEntity(
                assetId = asset.id, ticker = asset.ticker, quantityAtPayment = asset.quantity,
                grossAmount = grossAmount, withholdingTax = tax, netAmount = grossAmount - tax,
                currency = currency, exDate = paymentDate, paymentDate = paymentDate, note = note, tradeId = tradeId
            ))
        }
    }

    suspend fun importHoldings(holdings: List<ImportedHolding>): Pair<Int, Int> {
        var added = 0
        var skipped = 0
        database.withTransaction {
            val existing = assetDao.getAssetsOnce().map { it.ticker.uppercase() }.toMutableSet()
            holdings.forEach { row ->
                require(row.currency.uppercase() in listOf("TRY", "USD")) { "${row.ticker} için para birimi TRY veya USD olmalı." }
                if (row.ticker.uppercase() in existing) {
                    skipped++
                } else {
                    assetDao.insertAsset(AssetEntity(
                        name = row.name, ticker = row.ticker, quantity = row.quantity, costPrice = row.costPrice,
                        category = row.category, portfolioName = row.portfolioName, currency = row.currency,
                        marketSymbol = defaultMarketSymbol(row.ticker, row.category), lastPrice = row.lastPrice,
                        broker = row.broker, openingQuantity = row.quantity, openingCostPrice = row.costPrice, priceSource = row.priceSource
                    ))
                    existing.add(row.ticker.uppercase())
                    added++
                }
            }
        }
        return added to skipped
    }


    data class RestoreResult(val assetsAdded: Int, val assetsSkipped: Int, val tradesAdded: Int, val tradesSkipped: Int)

    suspend fun restorePortfolio(backup: ImportedPortfolio, overwrite: Boolean): RestoreResult {
        require(backup.assets.isNotEmpty()) { "Yedekte varlık bulunamadı." }
        val sourceIds = backup.assets.mapNotNull { it.sourceId }
        require(sourceIds.distinct().size == sourceIds.size) { "Varlık ID alanında tekrar eden kayıt bulundu." }
        require(backup.assets.all { it.currency in listOf("TRY", "USD") }) {
            "Bu sürümde yalnızca TRY ve USD varlıklar destekleniyor."
        }
        require(backup.trades.all { it.currency in listOf("TRY", "USD") }) {
            "İşlem geçmişinde TRY veya USD dışında para birimi bulundu."
        }
        var addedAssets = 0
        var skippedAssets = 0
        var addedTrades = 0
        var skippedTrades = 0
        database.withTransaction {
            if (overwrite) {
                tradeDao.deleteAll()
                dividendDao.deleteAll()
                priceSnapshotDao.deleteAll()
                snapshotDao.deleteAll()
                assetDao.deleteAll()
            }
            val existingAssets = assetDao.getAssetsOnce()
            fun assetKey(ticker: String, currency: String, broker: String) =
                ticker.uppercase() + "|" + currency.uppercase() + "|" + broker.trim().uppercase()
            val existingByKey = existingAssets.associateBy { assetKey(it.ticker, it.currency, it.broker) }
            val sourceToLocal = mutableMapOf<Long, Long>()
            val acceptedKeys = mutableSetOf<String>()
            val acceptedAssets = mutableMapOf<String, Pair<Long, ImportedAsset>>()

            backup.assets.forEach { item ->
                val key = assetKey(item.ticker, item.currency, item.broker)
                val existing = existingByKey[key]
                if (!overwrite && existing != null) {
                    skippedAssets++
                    item.sourceId?.let { sourceToLocal[it] = existing.id }
                    return@forEach
                }
                if (!acceptedKeys.add(key)) {
                    skippedAssets++
                    return@forEach
                }
                val related = backup.trades.filter { trade -> trade.applied && (
                    (item.sourceId != null && trade.sourceAssetId == item.sourceId) ||
                        (trade.sourceAssetId == null && trade.ticker.equals(item.ticker, true)))
                }.sortedBy { it.executedAt }
                val openingQuantity = if (related.isEmpty()) item.quantity else
                    item.quantity -
                        related.filter { it.action in setOf("BUY", "CASH_IN") }.sumOf { it.quantity } +
                        related.filter { it.action in setOf("SELL", "CASH_OUT") }.sumOf { it.quantity }
                require(openingQuantity >= -1e-8) {
                    "${item.ticker}: işlem geçmişi mevcut miktarla uyuşmuyor. Yedek dosyayı kontrol edin."
                }
                val openingQty = openingQuantity.coerceAtLeast(0.0)
                val buyCost = related.filter { it.action in setOf("BUY", "CASH_IN") }.sumOf { it.quantity * it.unitPrice + it.fee }
                val openingCost = if (openingQty > 1e-8) {
                    ((item.costPerUnit * item.quantity - buyCost) / openingQty).coerceAtLeast(0.0)
                } else 0.0
                val id = assetDao.insertAsset(AssetEntity(
                    id = if (overwrite) item.sourceId?.takeIf { it > 0L } ?: 0L else 0L,
                    name = item.name, ticker = item.ticker, quantity = item.quantity,
                    costPrice = item.costPerUnit, category = item.category, portfolioName = item.portfolioName,
                    currency = item.currency, marketSymbol = item.marketSymbol, lastPrice = item.unitPrice,
                    broker = item.broker, openingQuantity = openingQty, openingCostPrice = openingCost,
                    priceSource = item.priceSource, notes = item.notes,
                    createdAt = item.createdAt, updatedAt = item.updatedAt
                ))
                item.sourceId?.let { sourceToLocal[it] = id }
                acceptedAssets[key] = id to item
                addedAssets++
            }

            val importedFingerprints = mutableSetOf<String>()
            val existingTrades = if (overwrite) emptyList() else tradeDao.getAllOnce()
            fun fingerprint(assetId: Long, action: String, qty: Double, price: Double, fee: Double, date: Long, currency: String, note: String) =
                listOf(assetId, action, qty, price, fee, date, currency, note.trim()).joinToString("|")
            val existingFingerprints = existingTrades.map { fingerprint(it.assetId, it.action, it.quantity, it.unitPrice, it.fee, it.executedAt, it.currency, it.note) }.toMutableSet()

            backup.trades.sortedBy { it.executedAt }.forEach { item ->
                val sourceAsset = backup.assets.firstOrNull { asset ->
                    (item.sourceAssetId != null && asset.sourceId == item.sourceAssetId) ||
                        asset.ticker.equals(item.ticker, true) ||
                        asset.name.equals(item.ticker, true)
                }
                val mappedId = item.sourceAssetId?.let { sourceToLocal[it] }
                    ?: sourceAsset?.sourceId?.let { sourceToLocal[it] }
                val asset = mappedId?.let { assetDao.getById(it) }
                    ?: assetDao.getAssetsOnce().firstOrNull {
                        (it.ticker.equals(item.ticker, true) || it.name.equals(item.ticker, true)) &&
                            it.currency.equals(item.currency, true) &&
                            (sourceAsset == null || it.broker.equals(sourceAsset.broker, true))
                    } ?: return@forEach
                val key = fingerprint(asset.id, item.action, item.quantity, item.unitPrice, item.fee, item.executedAt, item.currency, item.note)
                val isNewAssetInThisRestore = acceptedAssets.values.any { it.first == asset.id }
                if (!overwrite && key in existingFingerprints) {
                    skippedTrades++
                    return@forEach
                }
                if (!importedFingerprints.add(key)) { skippedTrades++; return@forEach }
                // A merge keeps an existing position untouched; its imported rows become history-only.
                val applyToBalance = item.applied && (overwrite || isNewAssetInThisRestore)
                val tradeId = tradeDao.insert(TradeEntity(
                    id = if (overwrite) item.sourceId?.takeIf { it > 0L } ?: 0L else 0L,
                    assetId = asset.id, action = item.action, quantity = item.quantity,
                    unitPrice = item.unitPrice, fee = item.fee, realizedProfit = item.realizedProfit ?: 0.0,
                    executedAt = item.executedAt, note = item.note, currency = item.currency, applied = applyToBalance,
                    createdAt = item.createdAt,
                    realizedProfitTry = item.realizedProfitTry ?: if (item.currency == "TRY") (item.realizedProfit ?: 0.0) else 0.0
                ))
                if (item.action == "DIVIDEND" && applyToBalance) {
                    dividendDao.insert(DividendEntity(
                        assetId = asset.id, ticker = asset.ticker, quantityAtPayment = asset.quantity,
                        grossAmount = item.quantity * item.unitPrice, withholdingTax = item.fee,
                        netAmount = item.quantity * item.unitPrice - item.fee, currency = item.currency,
                        exDate = item.executedAt, paymentDate = item.executedAt, note = item.note, tradeId = tradeId
                    ))
                }
                addedTrades++
            }
            acceptedAssets.values.forEach { (assetId, _) -> recalculateAsset(assetId) }
        }
        return RestoreResult(addedAssets, skippedAssets, addedTrades, skippedTrades)
    }

    suspend fun classifyAsset(assetId: Long, category: String, broker: String) {
        require(category.isNotBlank() && broker.isNotBlank())
        assetDao.updateClassification(assetId, category, broker)
    }

    suspend fun updateAssetDetails(
        assetId: Long, name: String, ticker: String, category: String, broker: String,
        portfolioName: String, marketSymbol: String, notes: String,
        quantity: Double? = null, costPrice: Double? = null, currency: String = "TRY",
        priceSource: String = "AUTO", manualPrice: Double? = null
    ) {
        require(currency.uppercase() in listOf("TRY", "USD")) { "Varlık para birimi yalnızca TRY veya USD olabilir." }
        require(name.isNotBlank() && ticker.isNotBlank() && category.isNotBlank() && broker.isNotBlank())
        database.withTransaction {
            val existing = assetDao.getById(assetId) ?: error("Varlık bulunamadı")
            val trades = tradeDao.getForAssetOnce(assetId)
            require(trades.isEmpty() || existing.currency.equals(currency, true)) {
                "İşlem geçmişi bulunan varlığın para birimi değiştirilemez."
            }
            if (priceSource == "MANUAL") require(manualPrice != null && manualPrice >= 0.0) {
                "Elle giriş için geçerli güncel fiyat girin."
            }
            if (quantity != null || costPrice != null) {
                require(quantity != null && costPrice != null && quantity >= 0.0 && costPrice >= 0.0) {
                    "Miktar ve maliyet geçerli olmalı."
                }
                require(trades.isEmpty()) {
                    "İşlem geçmişi bulunan varlıkların miktar ve maliyeti İşlemler sayfasından düzenlenmelidir."
                }
                assetDao.updateOpeningPosition(assetId, quantity, costPrice)
            }
            val cleanTicker = ticker.trim().uppercase()
            assetDao.updateAssetDetails(
                assetId, name.trim(), cleanTicker, category, broker, portfolioName.trim().ifBlank { "Ana Portföy" },
                marketSymbol.trim().ifBlank { defaultMarketSymbol(cleanTicker, category) }, currency, priceSource, notes.trim()
            )
            if (priceSource == "MANUAL" && manualPrice != null) {
                assetDao.updatePrice(assetId, manualPrice, System.currentTimeMillis())
            }
        }
    }

    suspend fun editTrade(
        tradeId: Long, newAssetId: Long, action: String, quantity: Double, unitPrice: Double,
        fee: Double, note: String, executedAt: Long
    ) {
        require(action in setOf("BUY", "SELL", "CASH_IN", "CASH_OUT", "TRANSFER", "DIVIDEND"))
        require(quantity > 0.0 && unitPrice >= 0.0 && fee >= 0.0)
        database.withTransaction {
            val current = tradeDao.getById(tradeId) ?: error("İşlem bulunamadı")
            val oldAssetId = current.assetId
            val asset = assetDao.getById(newAssetId) ?: error("Seçilen varlık bulunamadı")
            if (action == "DIVIDEND") require(fee <= quantity * unitPrice) {
                "Stopaj brüt temettü tutarını aşmamalı."
            }
            tradeDao.update(current.copy(
                assetId = newAssetId, currency = asset.currency,
                action = action, quantity = quantity, unitPrice = unitPrice, fee = fee,
                note = note, executedAt = executedAt
            ))
            val existingDividend = dividendDao.getByTradeId(tradeId)
            if (action == "DIVIDEND") {
                val updated = DividendEntity(
                    id = existingDividend?.id ?: 0L, assetId = newAssetId, ticker = asset.ticker,
                    quantityAtPayment = asset.quantity, grossAmount = quantity * unitPrice, withholdingTax = fee,
                    netAmount = quantity * unitPrice - fee, currency = asset.currency, exDate = executedAt,
                    paymentDate = executedAt, note = note, tradeId = tradeId
                )
                if (existingDividend == null) dividendDao.insert(updated) else dividendDao.update(updated)
            } else if (existingDividend != null) {
                dividendDao.delete(existingDividend)
            }
            recalculateAsset(oldAssetId)
            if (newAssetId != oldAssetId) recalculateAsset(newAssetId)
        }
    }

    suspend fun deleteTrade(tradeId: Long) {
        database.withTransaction {
            val trade = tradeDao.getById(tradeId) ?: return@withTransaction
            dividendDao.getByTradeId(tradeId)?.let { dividendDao.delete(it) }
            tradeDao.delete(trade)
            if (trade.action !in setOf("DIVIDEND", "TRANSFER")) recalculateAsset(trade.assetId)
        }
    }

    private suspend fun recalculateAsset(assetId: Long) {
        val asset = assetDao.getById(assetId) ?: error("Varlık bulunamadı")
        var quantity = asset.openingQuantity
        var averageCost = asset.openingCostPrice
        tradeDao.getForAssetOnce(assetId).filter { it.applied && it.action != "DIVIDEND" }.forEach { trade ->
            when (trade.action) {
                "BUY", "CASH_IN" -> {
                    val nextQuantity = quantity + trade.quantity
                    averageCost = if (nextQuantity > 0.0) {
                        (quantity * averageCost + trade.quantity * trade.unitPrice + trade.fee) / nextQuantity
                    } else 0.0
                    quantity = nextQuantity
                    if (trade.realizedProfit != 0.0) tradeDao.update(trade.copy(realizedProfit = 0.0))
                }
                "SELL", "CASH_OUT" -> {
                    require(trade.quantity <= quantity + 1e-8) {
                        "Bu işlem düzenlemesi, önceki bakiyeden fazla çıkış oluşturuyor. İşlem geri alındı."
                    }
                    val realized = if (trade.action == "SELL") (trade.unitPrice - averageCost) * trade.quantity - trade.fee else 0.0
                    quantity = (quantity - trade.quantity).coerceAtLeast(0.0)
                    tradeDao.update(trade.copy(
                        realizedProfit = realized,
                        realizedProfitTry = if (asset.currency == "TRY") realized else trade.realizedProfitTry
                    ))
                    if (quantity == 0.0) averageCost = 0.0
                }
                "TRANSFER" -> Unit
            }
        }
        assetDao.updatePosition(assetId, quantity, averageCost)
    }

    suspend fun recordDailySnapshot() {
        val assets = assetDao.getAssetsOnce()
        if (assets.isEmpty()) return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val day = calendar.timeInMillis
        if ((snapshotDao.latestDate() ?: Long.MIN_VALUE) >= day) return
        val marketValue = assets.sumOf { it.quantity * (it.lastPrice ?: it.costPrice) }
        val investedValue = assets.sumOf { it.quantity * it.costPrice }
        snapshotDao.insert(PortfolioSnapshotEntity(day, marketValue, investedValue, assets.size))
    }

    suspend fun delete(asset: AssetEntity) { assetDao.deleteAsset(asset) }

    suspend fun updatePrice(assetId: Long, price: Double, updatedAt: Long = System.currentTimeMillis()) {
        if (price.isFinite() && price >= 0.0) {
            assetDao.updatePrice(assetId, price, updatedAt)
            recordCurrentPriceSnapshot(assetId)
        }
    }

    suspend fun recordCurrentPriceSnapshot(assetId: Long) {
        val asset = assetDao.getById(assetId) ?: return
        val price = asset.lastPrice ?: return
        val day = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        priceSnapshotDao.upsert(AssetPriceSnapshotEntity(assetId, day, price, asset.currency))
    }

    suspend fun dailyProfitLoss(currency: String): Double? {
        val assets = assetDao.getAssetsOnce().filter { it.currency.equals(currency, true) && it.quantity > 0.0 }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val comparable = assets.mapNotNull { asset ->
            val previous = priceSnapshotDao.previousForAsset(asset.id, today) ?: return@mapNotNull null
            ((asset.lastPrice ?: asset.costPrice) - previous.price) * asset.quantity
        }
        return if (comparable.size == assets.size && comparable.isNotEmpty()) comparable.sum() else null
    }

    private fun defaultMarketSymbol(ticker: String, category: String): String {
        val symbol = ticker.trim().uppercase()
        return if (category.uppercase() in listOf("STOCK", "BIST", "HISSE") && !symbol.endsWith(".IS")) symbol + ".IS" else symbol
    }
}
