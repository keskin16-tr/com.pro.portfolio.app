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
 * Builds a standards-compliant OOXML workbook without a heavyweight spreadsheet dependency.
 * The workbook contains separate Holdings, Transactions and Dividends worksheets.
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
                  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
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
                    <sheet name="Portföy" sheetId="1" r:id="rId1"/>
                    <sheet name="İşlemler" sheetId="2" r:id="rId2"/>
                    <sheet name="Temettüler" sheetId="3" r:id="rId3"/>
                  </sheets>
                </workbook>
            """.trimIndent())
            entry(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
                </Relationships>
            """.trimIndent())

            val holdingsRows = mutableListOf<List<Any?>>()
            holdingsRows += listOf("id", "Sembol", "Varlık", "Kategori", "Portföy", "Adet", "Ortalama Maliyet", "Son Fiyat", "Piyasa Değeri", "Maliyet Tutarı", "Kâr/Zarar", "Para Birimi", "Aracı Kurum", "Fiyat Kaynağı")
            assets.forEach { a ->
                val price = a.lastPrice ?: a.costPrice
                holdingsRows += listOf(a.id, a.ticker, a.name, a.category, a.portfolioName, a.quantity,
                    a.costPrice, price, a.quantity * price, a.quantity * a.costPrice,
                    a.quantity * (price - a.costPrice), a.currency, a.broker, a.priceSource)
            }
            val tradeRows = mutableListOf<List<Any?>>()
            tradeRows += listOf("id", "asset_id", "Tarih", "Sembol", "İşlem", "Miktar", "Birim Fiyat", "Tutar", "Komisyon / Stopaj", "Gerçekleşen K/Z", "Para Birimi", "Not")
            val assetById = assets.associateBy { it.id }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            trades.forEach { t ->
                tradeRows += listOf(t.id, t.assetId, dateFormat.format(Date(t.executedAt)), assetById[t.assetId]?.ticker ?: "ID ${t.assetId}",
                    when (t.action) { "SELL" -> "SATIŞ"; "DIVIDEND" -> "TEMETTÜ"; else -> "ALIŞ" }, t.quantity, t.unitPrice,
                    t.quantity * t.unitPrice, t.fee, t.realizedProfit, t.currency, t.note)
            }
            val dividendRows = mutableListOf<List<Any?>>()
            dividendRows += listOf("Ödeme Tarihi", "Sembol", "Ödeme Anındaki Adet", "Brüt Temettü", "Stopaj", "Net Temettü", "Para Birimi", "Not")
            dividends.forEach { d ->
                dividendRows += listOf(dateFormat.format(Date(d.paymentDate)), d.ticker, d.quantityAtPayment,
                    d.grossAmount, d.withholdingTax, d.netAmount, d.currency, d.note)
            }
            entry(zip, "xl/worksheets/sheet1.xml", sheetXml(holdingsRows))
            entry(zip, "xl/worksheets/sheet2.xml", sheetXml(tradeRows))
            entry(zip, "xl/worksheets/sheet3.xml", sheetXml(dividendRows))
            zip.finish()
        }
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
