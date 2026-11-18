package com.portfolio.app.data.repository

import androidx.room.withTransaction
import com.portfolio.app.data.local.AppDatabase
import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.TradeEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.PortfolioSnapshotEntity
import com.portfolio.app.data.local.AssetPriceSnapshotEntity
import com.portfolio.app.data.importer.ImportedHolding
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
        broker: String = "Belirtilmedi", priceSource: String = "AUTO"
    ) {
        require(quantity > 0.0 && unitPrice >= 0.0 && fee >= 0.0)
        database.withTransaction {
            val existing = assetDao.getAssetsOnce().firstOrNull { it.ticker.equals(ticker, ignoreCase = true) }
            val assetId = existing?.id ?: assetDao.insertAsset(
                AssetEntity(
                    name = name.trim(), ticker = ticker.trim().uppercase(), quantity = 0.0, costPrice = 0.0,
                    category = category, currency = currency, marketSymbol = defaultMarketSymbol(ticker, category),
                    broker = broker, lastPrice = unitPrice, priceUpdatedAt = System.currentTimeMillis(), priceSource = priceSource
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
                fee = fee, note = "Alış işlemi", currency = currency
            ))
            assetDao.updatePosition(assetId, newQuantity, averageCost)
        }
    }

    suspend fun recordSell(assetId: Long, quantity: Double, unitPrice: Double, fee: Double = 0.0) {
        require(quantity > 0.0 && unitPrice >= 0.0 && fee >= 0.0)
        database.withTransaction {
            val asset = assetDao.getById(assetId) ?: error("Varlık bulunamadı")
            require(quantity <= asset.quantity) { "Satış miktarı mevcut bakiyeden fazla olamaz." }
            val realized = (unitPrice - asset.costPrice) * quantity - fee
            tradeDao.insert(TradeEntity(
                assetId = assetId, action = "SELL", quantity = quantity, unitPrice = unitPrice,
                fee = fee, realizedProfit = realized, note = "Satış işlemi", currency = asset.currency
            ))
            assetDao.updatePosition(assetId, asset.quantity - quantity, asset.costPrice)
        }
    }

    suspend fun recordDividend(
        ticker: String, grossAmount: Double, taxPercent: Double = 0.0,
        currency: String = "TRY", paymentDate: Long = System.currentTimeMillis(), note: String = ""
    ) {
        require(grossAmount > 0.0 && taxPercent in 0.0..100.0)
        database.withTransaction {
            val asset = assetDao.getAssetsOnce().firstOrNull { it.ticker.equals(ticker.trim(), true) }
                ?: error("Bu sembole ait portföy varlığı bulunamadı")
            val tax = grossAmount * taxPercent / 100.0
            val tradeId = tradeDao.insert(TradeEntity(
                assetId = asset.id, action = "DIVIDEND", quantity = 1.0, unitPrice = grossAmount,
                fee = tax, executedAt = paymentDate, note = note.ifBlank { "Temettü ödemesi" }, currency = currency
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
                if (row.ticker.uppercase() in existing) {
                    skipped++
                } else {
                    assetDao.insertAsset(AssetEntity(
                        name = row.name, ticker = row.ticker, quantity = row.quantity, costPrice = row.costPrice,
                        category = row.category, portfolioName = row.portfolioName, currency = row.currency,
                        marketSymbol = defaultMarketSymbol(row.ticker, row.category), lastPrice = row.lastPrice,
                        broker = row.broker, openingQuantity = row.quantity, openingCostPrice = row.costPrice
                    ))
                    existing.add(row.ticker.uppercase())
                    added++
                }
            }
        }
        return added to skipped
    }

    suspend fun classifyAsset(assetId: Long, category: String, broker: String) {
        require(category.isNotBlank() && broker.isNotBlank())
        assetDao.updateClassification(assetId, category, broker)
    }

    suspend fun updateAssetDetails(
        assetId: Long, name: String, ticker: String, category: String, broker: String,
        quantity: Double? = null, costPrice: Double? = null, currency: String = "TRY",
        priceSource: String = "AUTO", manualPrice: Double? = null
    ) {
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
                assetId, name.trim(), cleanTicker, category, broker,
                defaultMarketSymbol(cleanTicker, category), currency, priceSource
            )
            if (priceSource == "MANUAL" && manualPrice != null) {
                assetDao.updatePrice(assetId, manualPrice, System.currentTimeMillis())
            }
        }
    }

    suspend fun editTrade(
        tradeId: Long, action: String, quantity: Double, unitPrice: Double,
        fee: Double, note: String, executedAt: Long
    ) {
        require(action == "BUY" || action == "SELL" || action == "DIVIDEND")
        require(quantity > 0.0 && unitPrice >= 0.0 && fee >= 0.0)
        database.withTransaction {
            val current = tradeDao.getById(tradeId) ?: error("İşlem bulunamadı")
            val asset = assetDao.getById(current.assetId) ?: error("Varlık bulunamadı")
            tradeDao.update(current.copy(
                action = action, quantity = quantity, unitPrice = unitPrice, fee = fee,
                note = note, executedAt = executedAt
            ))
            if (action == "DIVIDEND") {
                require(quantity == 1.0 && fee <= unitPrice) { "Temettü miktarı 1 olmalı ve stopaj brüt tutarı aşmamalı." }
                val existingDividend = dividendDao.getByTradeId(tradeId)
                val updated = DividendEntity(
                    id = existingDividend?.id ?: 0L, assetId = current.assetId, ticker = asset.ticker,
                    quantityAtPayment = asset.quantity, grossAmount = unitPrice, withholdingTax = fee,
                    netAmount = unitPrice - fee, currency = current.currency, exDate = executedAt,
                    paymentDate = executedAt, note = note, tradeId = tradeId
                )
                if (existingDividend == null) dividendDao.insert(updated) else dividendDao.update(updated)
            } else {
                if (current.action == "DIVIDEND") dividendDao.getByTradeId(tradeId)?.let { dividendDao.delete(it) }
                recalculateAsset(current.assetId)
            }
        }
    }

    suspend fun deleteTrade(tradeId: Long) {
        database.withTransaction {
            val trade = tradeDao.getById(tradeId) ?: return@withTransaction
            dividendDao.getByTradeId(tradeId)?.let { dividendDao.delete(it) }
            tradeDao.delete(trade)
            if (trade.action != "DIVIDEND") recalculateAsset(trade.assetId)
        }
    }

    private suspend fun recalculateAsset(assetId: Long) {
        val asset = assetDao.getById(assetId) ?: error("Varlık bulunamadı")
        var quantity = asset.openingQuantity
        var averageCost = asset.openingCostPrice
        tradeDao.getForAssetOnce(assetId).filter { it.action != "DIVIDEND" }.forEach { trade ->
            if (trade.action == "BUY") {
                val nextQuantity = quantity + trade.quantity
                averageCost = if (nextQuantity > 0.0) {
                    (quantity * averageCost + trade.quantity * trade.unitPrice + trade.fee) / nextQuantity
                } else 0.0
                quantity = nextQuantity
                if (trade.realizedProfit != 0.0) tradeDao.update(trade.copy(realizedProfit = 0.0))
            } else {
                require(trade.quantity <= quantity + 1e-8) {
                    "Bu işlem düzenlemesi, önceki alışlardan fazla satış oluşturuyor. İşlem geri alındı."
                }
                val realized = (trade.unitPrice - averageCost) * trade.quantity - trade.fee
                quantity = (quantity - trade.quantity).coerceAtLeast(0.0)
                tradeDao.update(trade.copy(realizedProfit = realized))
                if (quantity == 0.0) averageCost = 0.0
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
        return comparable.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun defaultMarketSymbol(ticker: String, category: String): String {
        val symbol = ticker.trim().uppercase()
        return if (category.uppercase() in listOf("STOCK", "BIST", "HISSE") && !symbol.endsWith(".IS")) symbol + ".IS" else symbol
    }
}
