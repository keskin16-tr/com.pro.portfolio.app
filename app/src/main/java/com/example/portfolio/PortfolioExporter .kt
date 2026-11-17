package com.example.portfolio.reports

import com.example.portfolio.model.PortfolioAsset
import com.example.portfolio.model.PortfolioTransaction
import com.example.portfolio.util.parseNumber
import com.example.portfolio.util.plainNumber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bir CSV satırından okunan işlem. */
data class ImportRow(
    val date: Long,
    val symbol: String,
    val name: String,
    val type: String,
    val currency: String,
    val side: String,
    val quantity: Double,
    val price: Double,
    val commission: Double
)

data class ParseResult(
    val rows: List<ImportRow>,
    val skipped: Int
)

/**
 * İşlem geçmişini CSV olarak dışa aktarır ve aynı biçimden içe aktarır.
 * Sütunlar: date,symbol,name,type,currency,side,quantity,price,commission
 * side: BUY veya SELL (ALIŞ / SATIŞ de kabul edilir)
 */
object PortfolioExporter {

    private const val HEADER = "date,symbol,name,type,currency,side,quantity,price,commission"

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun toCsv(
        assets: List<PortfolioAsset>,
        transactions: List<PortfolioTransaction>
    ): String {
        val byId = assets.associateBy { it.id }
        val format = dateFormat()
        val builder = StringBuilder()
        builder.append(HEADER).append('\n')

        for (tx in transactions.sortedBy { it.date }) {
            val asset = byId[tx.assetId] ?: continue

            builder.append(
                listOf(
                    format.format(Date(tx.date)),
                    asset.symbol,
                    asset.name,
                    asset.type,
                    asset.currency,
                    tx.type,
                    plainNumber(tx.quantity),
                    plainNumber(tx.price),
                    plainNumber(tx.commission)
                ).joinToString(",") { escape(it) }
            ).append('\n')
        }

        return builder.toString()
    }

    /** Aynı işlemin ikinci kez içe aktarılmasını önlemek için kullanılan anahtar. */
    fun key(
        symbol: String,
        side: String,
        quantity: Double,
        price: Double,
        date: Long
    ): String =
        "${symbol.uppercase()}|$side|${plainNumber(quantity)}|${plainNumber(price)}|${date / 1000}"

    fun parse(csv: String): ParseResult {
        val lines = csv.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParseResult(emptyList(), 0)

        val delimiter = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val first = splitLine(lines[0], delimiter).map { it.trim().lowercase() }
        val hasHeader = first.firstOrNull() == "date"

        val columns = if (hasHeader) first else HEADER.split(',')
        fun index(name: String) = columns.indexOf(name)

        val iDate = index("date")
        val iSymbol = index("symbol")
        val iName = index("name")
