package com.portfolio.app.data.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class ImportedHolding(
    val ticker: String,
    val name: String,
    val category: String,
    val portfolioName: String,
    val quantity: Double,
    val costPrice: Double,
    val lastPrice: Double?,
    val currency: String,
    val broker: String,
    val priceSource: String = "AUTO"
)

object ExcelImporter {
    fun readHoldings(input: InputStream): List<ImportedHolding> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (
                    entry.name == "xl/workbook.xml" ||
                    entry.name == "xl/_rels/workbook.xml.rels" ||
                    entry.name == "xl/sharedStrings.xml" ||
                    entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml")
                )) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val sheetFiles = entries.filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
        if (sheetFiles.isEmpty()) error("Excel dosyasında çalışma sayfası bulunamadı.")
        val sheetMap = resolveSheets(entries["xl/workbook.xml"], entries["xl/_rels/workbook.xml.rels"])
        val preferred = sheetMap.firstOrNull { normalize(it.first) in setOf("VARLIKLAR", "PORTFOY", "HOLDINGS") }
        val sheetPath = preferred?.second ?: sheetFiles.keys.sorted().first()
        val sheet = entries[sheetPath] ?: error("Varlıklar çalışma sayfası okunamadı.")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val rows = parseRows(sheet, shared)
        if (rows.isEmpty()) error("Excel sayfasında veri bulunamadı.")
        val headers = rows.first().mapIndexed { index, value -> normalize(value) to index }.toMap()
        fun column(vararg names: String): Int? = names.firstNotNullOfOrNull { headers[normalize(it)] }
        val tickerCol = column("Sembol", "Ticker", "Symbol", "Hisse Kodu", "Kod", "symbol")
            ?: error("Sembol/Ticker başlığı bulunamadı.")
        val nameCol = column("Varlık", "Varlık Adı", "Ad", "Name", "name")
        val categoryCol = column("Kategori", "Category", "Tür", "Tip", "category")
        val portfolioCol = column("Portföy", "Portfolio", "account")
        val quantityCol = column("Adet", "Miktar", "Quantity", "Qty", "quantity")
            ?: error("Adet/Quantity başlığı bulunamadı.")
        val costCol = column("Ortalama Maliyet", "Maliyet", "Alış Fiyatı", "Cost Price", "Average Cost", "cost_per_unit")
            ?: error("Ortalama maliyet başlığı bulunamadı.")
        val lastCol = column("Son Fiyat", "Güncel Fiyat", "Last Price", "Price", "unit_price")
        val currencyCol = column("Para Birimi", "Currency", "currency")
        val brokerCol = column("Aracı Kurum", "Kurum", "Broker", "Brokerage", "account")
        val sourceCol = column("Fiyat Kaynağı", "Price Source", "Provider", "price_source")
        return rows.drop(1).mapNotNull { row ->
            fun value(index: Int?): String = index?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()
            val ticker = value(tickerCol).uppercase(Locale.ROOT)
            val quantity = parseNumber(value(quantityCol))
            val cost = parseNumber(value(costCol))
            if (ticker.isBlank() || quantity == null || quantity <= 0.0 || cost == null || cost < 0.0) null
            else ImportedHolding(
                ticker, value(nameCol).ifBlank { ticker }, normalizeCategory(value(categoryCol)),
                value(portfolioCol).ifBlank { "Ana Portföy" }, quantity, cost,
                lastCol?.let { parseNumber(value(it)) },
                value(currencyCol).ifBlank { if (normalizeCategory(value(categoryCol)) == "US_STOCK") "USD" else "TRY" }.uppercase(Locale.ROOT),
                value(brokerCol).ifBlank { value(portfolioCol).ifBlank { "Belirtilmedi" } },
                normalizePriceSource(value(sourceCol))
            )
        }.distinctBy { it.ticker }
    }

    private fun resolveSheets(workbook: ByteArray?, relationships: ByteArray?): List<Pair<String, String>> {
        if (workbook == null || relationships == null) return emptyList()
        val targets = mutableMapOf<String, String>()
        val relParser = Xml.newPullParser()
        relParser.setInput(relationships.inputStream(), "UTF-8")
        var event = relParser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && relParser.name == "Relationship") {
                val id = relParser.getAttributeValue(null, "Id").orEmpty()
                val target = relParser.getAttributeValue(null, "Target").orEmpty()
                if (id.isNotBlank() && target.isNotBlank()) {
                    targets[id] = if (target.startsWith("/")) target.removePrefix("/")
                    else "xl/" + target.removePrefix("xl/")
                }
            }
            event = relParser.next()
        }
        val result = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(workbook.inputStream(), "UTF-8")
        event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                var relationId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id").orEmpty()
                if (relationId.isBlank()) {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "id") relationId = parser.getAttributeValue(i)
                    }
                }
                targets[relationId]?.let { path ->
                    val normalizedPath = if (path.startsWith("xl/")) path else "xl/$path"
                    result += name to normalizedPath
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseRows(xml: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")
        val rows = mutableListOf<List<String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "row") {
                val cells = mutableMapOf<Int, String>()
                var child = parser.next()
                while (!(child == XmlPullParser.END_TAG && parser.name == "row")) {
                    if (child == XmlPullParser.START_TAG && parser.name == "c") {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        val type = parser.getAttributeValue(null, "t").orEmpty()
                        val index = columnIndex(ref)
                        var cellText = ""
                        var nested = parser.next()
                        while (!(nested == XmlPullParser.END_TAG && parser.name == "c")) {
                            if (nested == XmlPullParser.START_TAG && parser.name == "t") cellText += parser.nextText()
                            else if (nested == XmlPullParser.START_TAG && parser.name == "v") {
                                val raw = parser.nextText()
                                cellText = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                            }
                            nested = parser.next()
                        }
                        if (index >= 0) cells[index] = cellText
                    }
                    child = parser.next()
                }
                val max = cells.keys.maxOrNull() ?: -1
                rows += (0..max).map { cells[it].orEmpty() }
            }
            event = parser.next()
        }
        return rows
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")
        val values = mutableListOf<String>()
        var text = StringBuilder()
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "si") {
                inItem = true
                text = StringBuilder()
            } else if (event == XmlPullParser.START_TAG && parser.name == "t" && inItem) {
                text.append(parser.nextText())
            } else if (event == XmlPullParser.END_TAG && parser.name == "si") {
                values += text.toString()
                inItem = false
            }
            event = parser.next()
        }
        return values
    }

    private fun columnIndex(reference: String): Int {
        var result = 0
        reference.takeWhile { it.isLetter() }.uppercase(Locale.ROOT).forEach { result = result * 26 + (it - 'A' + 1) }
        return result - 1
    }

    private fun normalize(value: String): String = value.trim().uppercase(Locale("tr", "TR"))
        .replace("İ", "I").replace("Ş", "S").replace("Ğ", "G")
        .replace("Ü", "U").replace("Ö", "O").replace("Ç", "C")
        .replace(Regex("[^A-Z0-9]"), "")

    private fun normalizePriceSource(value: String): String = when (normalize(value)) {
        "YAHOOFINANCE", "YAHOO" -> "YAHOO_FINANCE"
        "COINGECKO" -> "COINGECKO"
        "FRANKFURTER" -> "FRANKFURTER"
        "MANUEL", "ELLEGIRIS" -> "MANUAL"
        else -> "AUTO"
    }

    private fun normalizeCategory(value: String): String = when (normalize(value)) {
        "HISSE", "HISSEBIST", "HISSESENEDI", "STOCK", "BIST" -> "STOCK"
        "AMERIKANBORSALARI", "AMERIKANHISSE", "USSTOCK", "USSTOCKS", "NASDAQ", "NYSE", "USA" -> "US_STOCK"
        "FON", "YATIRIMFONU", "FUND" -> "FUND"
        "KRIPTO", "KRIPTOPARA", "CRYPTO" -> "CRYPTO"
        "DOVIZ", "FX", "CURRENCY" -> "FX"
        "ALTIN", "GOLD", "KIYMETLIMADEN" -> "GOLD"
        "EMTIA", "COMMODITY" -> "COMMODITY"
        "TAHVIL", "BONO", "TAHVILBONO", "BOND" -> "BOND"
        "NAKIT", "CASH" -> "CASH"
        else -> "OTHER"
    }

    private fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim().replace(" ", "")
        val normalized = if (cleaned.contains(",")) cleaned.replace(".", "").replace(",", ".") else cleaned
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
