package com.portfolio.app.data.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class ImportedAsset(
    val sourceId: Long?, val ticker: String, val name: String, val category: String,
    val portfolioName: String, val quantity: Double, val unitPrice: Double?,
    val currency: String, val costPerUnit: Double, val broker: String,
    val priceSource: String, val marketSymbol: String,
    val notes: String, val createdAt: Long?, val updatedAt: Long?
)
// Backward-compatible model used by the legacy single-sheet import flow.
data class ImportedHolding(
    val ticker: String,
    val name: String,
    val quantity: Double,
    val costPrice: Double,
    val category: String,
    val portfolioName: String,
    val currency: String,
    val lastPrice: Double?,
    val broker: String,
    val priceSource: String
)
data class ImportedTrade(
    val sourceId: Long?, val sourceAssetId: Long?, val ticker: String,
    val action: String, val quantity: Double, val unitPrice: Double, val fee: Double,
    val currency: String, val executedAt: Long, val note: String, val realizedProfit: Double?,
    val applied: Boolean, val createdAt: Long?, val realizedProfitTry: Double?
)
data class ImportedPortfolio(val assets: List<ImportedAsset>, val trades: List<ImportedTrade>)

object ExcelImporter {
    fun readPortfolio(input: InputStream): ImportedPortfolio {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (entry.name == "xl/workbook.xml" ||
                    entry.name == "xl/_rels/workbook.xml.rels" || entry.name == "xl/sharedStrings.xml" ||
                    (entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml")))) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val sheets = resolveSheets(entries["xl/workbook.xml"], entries["xl/_rels/workbook.xml.rels"])
        if (sheets.isEmpty()) error("Excel çalışma sayfaları bulunamadı.")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        fun rowsFor(vararg names: String): List<List<String>> {
            val wanted = names.map(::normalize).toSet()
            val sheet = sheets.firstOrNull { normalize(it.first) in wanted } ?: return emptyList()
            val bytes = entries[sheet.second] ?: return emptyList()
            return parseRows(bytes, shared)
        }
        val assetRows = rowsFor("Varlıklar", "Portföy", "Portfolio", "Holdings")
        if (assetRows.isEmpty()) error("Varlıklar/Portföy çalışma sayfası bulunamadı.")
        val assets = parseAssets(assetRows)
        if (assets.isEmpty()) error("Varlıklar sayfasında geçerli kayıt bulunamadı.")
        val tradeRows = rowsFor("İşlemler", "Transactions", "Trades")
        val trades = if (tradeRows.isEmpty()) emptyList() else parseTrades(tradeRows)
        return ImportedPortfolio(assets, trades)
    }

    private fun parseAssets(rows: List<List<String>>): List<ImportedAsset> {
        val h = headers(rows)
        fun col(vararg names: String) = findColumn(h, *names)
        val tickerCol = col("symbol", "ticker", "sembol", "hisse kodu", "kod")
            ?: error("Varlıklar sayfasında symbol/ticker sütunu bulunamadı.")
        val qtyCol = col("quantity", "qty", "adet", "miktar")
            ?: error("Varlıklar sayfasında quantity sütunu bulunamadı.")
        val costCol = col("cost_per_unit", "costprice", "average cost", "ortalama maliyet", "maliyet", "alış fiyatı")
            ?: error("Varlıklar sayfasında cost_per_unit/ortalama maliyet sütunu bulunamadı.")
        val idCol = col("id", "varlık id", "asset id")
        val nameCol = col("name", "varlık", "varlık adı", "ad")
        val categoryCol = col("category", "kategori", "tür", "tip")
        val accountCol = col("account", "portfolio", "portföy", "hesap")
        val unitCol = col("unit_price", "lastprice", "last price", "son fiyat", "güncel fiyat", "price")
        val currencyCol = col("currency", "para birimi")
        val brokerCol = col("broker", "aracı kurum", "kurum")
        val sourceCol = col("price_source", "price source", "fiyat kaynağı")
        val marketCol = col("market_symbol", "market symbol")
        val notesCol = col("notes", "note", "not", "açıklama")
        val createdCol = col("created_at", "created at", "oluşturulma tarihi")
        val updatedCol = col("updated_at", "updated at", "güncelleme tarihi")
        return rows.drop(1).mapNotNull { row ->
            fun v(i: Int?) = i?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()
            val marketSymbol = v(marketCol)
            val fallbackSymbol = marketSymbol.takeIf { it.any { ch -> ch.isLetterOrDigit() } }.orEmpty()
            val ticker = v(tickerCol).ifBlank { fallbackSymbol }.ifBlank { v(nameCol) }.uppercase(Locale.ROOT)
            val qty = number(v(qtyCol)); val cost = number(v(costCol))
            if (ticker.isBlank() || qty == null || qty < 0.0 || cost == null || cost < 0.0) null
            else {
                val category = category(v(categoryCol))
                val account = v(accountCol).ifBlank { "Ana Portföy" }
                ImportedAsset(v(idCol).toLongOrNull(), ticker, v(nameCol).ifBlank { ticker },
                    category, account, qty, number(v(unitCol)), v(currencyCol).ifBlank { if (category == "US_STOCK") "USD" else "TRY" }.uppercase(Locale.ROOT),
                    cost, v(brokerCol).ifBlank { account }, priceSource(v(sourceCol)),
                    marketSymbol.ifBlank { defaultMarketSymbol(ticker, category) },
                    v(notesCol), parseDate(v(createdCol)), parseDate(v(updatedCol)))
            }
        }
    }

    private fun parseTrades(rows: List<List<String>>): List<ImportedTrade> {
        val h = headers(rows)
        fun col(vararg names: String) = findColumn(h, *names)
        val actionCol = col("transaction_type", "transaction type", "işlem", "işlem türü", "type", "action")
            ?: error("İşlemler sayfasında transaction_type/İşlem sütunu bulunamadı.")
        val qtyCol = col("quantity", "miktar", "adet", "qty")
        val priceCol = col("unit_price", "unit price", "birim fiyat", "fiyat", "price")
        val tickerCol = col("symbol", "ticker", "sembol", "hisse kodu", "kod")
        val assetIdCol = col("asset_id", "asset id", "varlık id")
        val assetNameCol = col("asset_name", "varlık adı", "varlık")
        val currencyCol = col("currency", "para birimi")
        val dateCol = col("transaction_date", "transaction date", "tarih", "işlem tarihi", "executed_at")
        val noteCol = col("notes", "note", "not", "açıklama")
        val idCol = col("id", "işlem id")
        val feeCol = col("fee", "commission", "komisyon", "komisyon / stopaj", "stopaj")
        val realizedCol = col("realized_pnl", "realized pnl", "gerçekleşen k/z", "realizedprofit")
        val appliedCol = col("applied", "uygulandı", "aktif")
        val createdCol = col("created_at", "created at", "oluşturulma tarihi")
        val realizedTryCol = col("realized_pnl_try", "realized pnl try", "gerçekleşen k/z try")
        return rows.drop(1).mapNotNull { row ->
            fun v(i: Int?) = i?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()
            val action = when (normalize(v(actionCol))) {
                "BUY", "ALIS", "AL" -> "BUY"
                "SELL", "SATIS", "SAT" -> "SELL"
                "CASHIN", "PARAGIRISI", "PARAYATIRMA", "DEPOSIT" -> "CASH_IN"
                "CASHOUT", "PARACIKISI", "PARACEKME", "WITHDRAWAL" -> "CASH_OUT"
                "TRANSFER", "HAVALE", "VIRMAN" -> "TRANSFER"
                "DIVIDEND", "TEMETTU" -> "DIVIDEND"
                else -> return@mapNotNull null
            }
            val rawQty = number(v(qtyCol)) ?: if (action == "DIVIDEND") 1.0 else return@mapNotNull null
            val rawPrice = number(v(priceCol)) ?: if (action in setOf("CASH_IN", "CASH_OUT", "TRANSFER")) 1.0 else return@mapNotNull null
            // Kaynak çalışma kitabındaki miktar ve birim fiyat değerlerini aynen koru.
            // Temettüde quantity × unit_price toplam temettü tutarını verir.
            val date = parseDate(v(dateCol)) ?: System.currentTimeMillis()
            val note = v(noteCol)
            val ticker = v(tickerCol).ifBlank {
                if (action == "DIVIDEND" && v(assetIdCol).isBlank()) note else v(assetNameCol)
            }.uppercase(Locale.ROOT)
            ImportedTrade(v(idCol).toLongOrNull(), v(assetIdCol).toLongOrNull(), ticker, action,
                rawQty, rawPrice, number(v(feeCol)) ?: 0.0, v(currencyCol).ifBlank { "TRY" }.uppercase(Locale.ROOT),
                date, note, number(v(realizedCol)), v(appliedCol).let { it.isBlank() || normalize(it) in setOf("TRUE", "1", "YES", "EVET", "UYGULANDI") },
                parseDate(v(createdCol)), number(v(realizedTryCol)))
        }
    }

    private fun headers(rows: List<List<String>>) = rows.firstOrNull().orEmpty().mapIndexed { i, v -> normalize(v) to i }.toMap()
    private fun findColumn(h: Map<String, Int>, vararg names: String): Int? = names.firstNotNullOfOrNull { h[normalize(it)] }
    private fun resolveSheets(workbook: ByteArray?, relationships: ByteArray?): List<Pair<String, String>> {
        if (workbook == null || relationships == null) return emptyList()
        val targets = mutableMapOf<String, String>()
        val rp = Xml.newPullParser().apply { setInput(relationships.inputStream(), "UTF-8") }
        var e = rp.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && rp.name == "Relationship") {
                val id = rp.getAttributeValue(null, "Id").orEmpty()
                val target = rp.getAttributeValue(null, "Target").orEmpty()
                if (id.isNotBlank() && target.isNotBlank()) targets[id] =
                    if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("xl/")
            }
            e = rp.next()
        }
        val result = mutableListOf<Pair<String, String>>()
        val p = Xml.newPullParser().apply { setInput(workbook.inputStream(), "UTF-8") }
        e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "sheet") {
                val name = p.getAttributeValue(null, "name").orEmpty()
                var id = p.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id").orEmpty()
                if (id.isBlank()) for (i in 0 until p.attributeCount) if (p.getAttributeName(i) == "id") id = p.getAttributeValue(i)
                targets[id]?.let { result += name to it }
            }
            e = p.next()
        }
        return result
    }
    private fun parseRows(xml: ByteArray, shared: List<String>): List<List<String>> {
        val p = Xml.newPullParser().apply { setInput(xml.inputStream(), "UTF-8") }
        val rows = mutableListOf<List<String>>()
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "row") {
                val cells = mutableMapOf<Int, String>(); var child = p.next()
                while (!(child == XmlPullParser.END_TAG && p.name == "row")) {
                    if (child == XmlPullParser.START_TAG && p.name == "c") {
                        val index = columnIndex(p.getAttributeValue(null, "r").orEmpty())
                        val type = p.getAttributeValue(null, "t").orEmpty()
                        var value = ""; var n = p.next()
                        while (!(n == XmlPullParser.END_TAG && p.name == "c")) {
                            if (n == XmlPullParser.START_TAG && p.name == "t") value += p.nextText()
                            else if (n == XmlPullParser.START_TAG && p.name == "v") {
                                val raw = p.nextText(); value = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                            }
                            n = p.next()
                        }
                        if (index >= 0) cells[index] = value
                    }
                    child = p.next()
                }
                val max = cells.keys.maxOrNull() ?: -1
                rows += (0..max).map { cells[it].orEmpty() }
            }
            e = p.next()
        }
        return rows
    }
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val p = Xml.newPullParser().apply { setInput(xml.inputStream(), "UTF-8") }
        val out = mutableListOf<String>(); var current = StringBuilder(); var inside = false; var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "si") { inside = true; current = StringBuilder() }
            else if (e == XmlPullParser.START_TAG && p.name == "t" && inside) current.append(p.nextText())
            else if (e == XmlPullParser.END_TAG && p.name == "si") { out += current.toString(); inside = false }
            e = p.next()
        }
        return out
    }
    private fun columnIndex(ref: String): Int {
        var n = 0
        ref.takeWhile { it.isLetter() }.uppercase(Locale.ROOT).forEach { n = n * 26 + (it - 'A' + 1) }
        return n - 1
    }
    private fun normalize(v: String) = v.trim().uppercase(Locale("tr", "TR"))
        .replace("İ", "I").replace("Ş", "S").replace("Ğ", "G").replace("Ü", "U").replace("Ö", "O").replace("Ç", "C")
        .replace(Regex("[^A-Z0-9]"), "")
    private fun number(raw: String): Double? {
        val s = raw.trim().replace(" ", "")
        val normalized = if (s.contains(",") && s.contains(".")) {
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(",", ".") else s.replace(",", "")
        } else if (s.contains(",")) s.replace(",", ".") else s
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    private fun parseDate(raw: String): Long? {
        val s = raw.trim()
        number(s)?.let { serial ->
            if (serial in 1.0..100000.0) return ((serial - 25569.0) * 86_400_000.0).toLong()
        }
        s.toLongOrNull()?.let { return when { it > 10_000_000_000L -> it; it > 0 -> it * 1000L; else -> null } }
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy", "MM/dd/yyyy")
        patterns.forEach { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(s)?.time }.getOrNull()?.let { return it } }
        return null
    }
    private fun category(v: String) = when (normalize(v)) {
        "HISSE", "HISSEBIST", "BISTHISSE", "HISSESENEDI", "STOCK", "BIST" -> "STOCK"
        "ABD HISSE ETF", "ABDHISSEETF", "AMERIKANBORSALARI", "AMERIKANHISSE", "USSTOCK", "USSTOCKS", "NASDAQ", "NYSE", "USA" -> "US_STOCK"
        "FON", "YATIRIMFONU", "FUND" -> "FUND"
        "KRIPTO", "KRIPTOPARA", "CRYPTO" -> "CRYPTO"
        "DOVIZ", "FX", "CURRENCY" -> "FX"
        "ALTIN", "GOLD", "KIYMETLIMADEN" -> "GOLD"
        "ALTIN EMTIA", "ALTINEMTIA", "EMTIA", "COMMODITY" -> "COMMODITY"
        "TAHVIL", "BONO", "TAHVILBONO", "BOND" -> "BOND"
        "BANKA", "NAKIT", "CASH" -> "CASH"
        else -> "OTHER"
    }
    private fun priceSource(v: String) = when (normalize(v)) {
        "YAHOOFINANCE", "YAHOO" -> "YAHOO_FINANCE"
        "COINGECKO" -> "COINGECKO"
        "FRANKFURTER" -> "FRANKFURTER"
        "MANUEL", "ELLEGIRIS" -> "MANUAL"
        else -> "AUTO"
    }
    private fun defaultMarketSymbol(ticker: String, category: String) =
        if (category == "STOCK" && !ticker.endsWith(".IS")) "$ticker.IS" else ticker
}
