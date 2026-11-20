package com.portfolio.app.data.export

import com.portfolio.app.data.local.AssetEntity
import com.portfolio.app.data.local.DividendEntity
import com.portfolio.app.data.local.TradeEntity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports the same two-sheet structure used by the user's portfolio workbook:
 * "Varlıklar" and "İşlemler". Column names intentionally match that workbook.
 */
object ExcelExporter {
    fun write(
        output: OutputStream,
        assets: List<AssetEntity>,
        trades: List<TradeEntity>,
        dividends: List<DividendEntity>
    ) {
        ZipOutputStream(output).use { zip ->
            entry(zip, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """.trimIndent())
            entry(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent())
            entry(zip, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Varlıklar" sheetId="1" r:id="rId1"/>
                    <sheet name="İşlemler" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
            """.trimIndent())
            entry(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                </Relationships>
            """.trimIndent())

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val assetRows = mutableListOf<List<Any?>>()
            assetRows += listOf(
                "id", "name", "symbol", "category", "account", "quantity", "unit_price",
                "currency", "cost_per_unit", "notes", "created_at", "updated_at",
                "price_source", "market_symbol"
            )
            assets.forEach { asset ->
                assetRows += listOf(
                    asset.id, asset.name, asset.ticker, categoryLabel(asset.category),
                    asset.broker, asset.quantity, asset.lastPrice ?: asset.costPrice,
                    asset.currency, asset.costPrice, asset.notes,
                    asset.createdAt?.let { dateFormat.format(Date(it)) },
                    asset.updatedAt?.let { dateFormat.format(Date(it)) },
                    sourceLabel(asset.priceSource), asset.marketSymbol
                )
            }

            val assetById = assets.associateBy { it.id }
            val tradeRows = mutableListOf<List<Any?>>()
            tradeRows += listOf(
                "id", "asset_id", "asset_name", "transaction_type", "quantity",
                "unit_price", "currency", "transaction_date", "notes", "created_at",
                "applied", "realized_pnl", "realized_pnl_try"
            )
            trades.forEach { trade ->
                val asset = assetById[trade.assetId]
                tradeRows += listOf(
                    trade.id, trade.assetId, asset?.name ?: asset?.ticker.orEmpty(),
                    actionLabel(trade.action), trade.quantity, trade.unitPrice, trade.currency,
                    dateFormat.format(Date(trade.executedAt)), trade.note,
                    trade.createdAt?.let { dateFormat.format(Date(it)) },
                    if (trade.applied) 1 else 0, trade.realizedProfit,
                    if (trade.currency.equals("TRY", true)) trade.realizedProfit else trade.realizedProfitTry
                )
            }

            entry(zip, "xl/worksheets/sheet1.xml", sheetXml(assetRows))
            entry(zip, "xl/worksheets/sheet2.xml", sheetXml(tradeRows))
            zip.finish()
        }
    }

    private fun categoryLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "STOCK", "BIST" -> "BIST Hisse"
        "US_STOCK" -> "ABD Hisse / ETF"
        "FUND" -> "Yatırım Fonu"
        "CRYPTO" -> "Kripto"
        "FX" -> "Döviz"
        "GOLD", "COMMODITY" -> "Altın / Emtia"
        "BOND" -> "Tahvil / Bono"
        "BANK" -> "Banka"
        "CASH" -> "Nakit"
        else -> "Diğer"
    }

    private fun sourceLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "YAHOO_FINANCE", "YAHOO" -> "yahoo"
        "COINGECKO" -> "coingecko"
        "FRANKFURTER" -> "frankfurter"
        "MANUAL" -> "manual"
        else -> "auto"
    }

    private fun actionLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "BUY" -> "Alış"
        "SELL" -> "Satış"
        "CASH_IN" -> "Para girişi"
        "CASH_OUT" -> "Para çıkışı"
        "TRANSFER" -> "Transfer"
        "DIVIDEND" -> "Temettü"
        else -> value
    }

    private fun sheetXml(rows: List<List<Any?>>): String {
        val body = rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { colIndex, value ->
                val ref = columnName(colIndex + 1) + (rowIndex + 1)
                when (value) {
                    null -> ""
                    is Number -> """<c r="$ref"><v>$value</v></c>"""
                    else -> """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>"""
                }
            }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>""".trimIndent()
    }

    private fun columnName(number: Int): String {
        var n = number
        val result = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            result.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return result.toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun entry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
