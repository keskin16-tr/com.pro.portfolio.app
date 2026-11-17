package com.example.portfolio.util

import android.content.Context
import java.math.BigDecimal

/**
 * Kullanıcının yazdığı sayıyı çözer.
 *
 * Türkçe klavyelerde ondalık ayraç virgüldür ("0,5"). Düz `toDoubleOrNull()`
 * bu girişi reddettiği için eskiden işlem ve varlık kaydı sessizce
 * başarısız oluyordu. Burada hem "0,5" hem "0.5" hem de "1.234,56" kabul edilir.
 */
fun parseNumber(text: String?): Double? {
    var s = text?.trim()?.replace(" ", "")?.replace(" ", "") ?: return null
    if (s.isEmpty()) return null

    val hasDot = s.contains('.')
    val hasComma = s.contains(',')

    s = when {
        hasDot && hasComma ->
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                s.replace(".", "").replace(',', '.')
            } else {
                s.replace(",", "")
            }
        hasComma -> s.replace(',', '.')
        else -> s
    }

    val value = s.toDoubleOrNull() ?: return null
    return if (value.isFinite()) value else null
}

/** 1.0E-8 gibi bilimsel gösterim yerine düz sayı metni üretir. */
fun plainNumber(value: Double): String {
    if (!value.isFinite()) return "0"
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

/** Kurları ve son yenileme zamanını cihazda saklar; uygulama çevrimdışıyken de değer gösterebilir. */
class FxStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("fx_rates", Context.MODE_PRIVATE)

    /** 1 birim para biriminin TL karşılığı. */
    fun load(): Map<String, Double> {
        val result = mutableMapOf("TRY" to 1.0)
        for ((key, value) in prefs.all) {
            if (key.startsWith("fx_") && value is String) {
                val rate = value.toDoubleOrNull()
                if (rate != null && rate > 0) {
                    result[key.removePrefix("fx_")] = rate
                }
            }
        }
        return result
    }

    fun save(rates: Map<String, Double>) {
        val editor = prefs.edit()
        for ((currency, rate) in rates) {
            if (currency != "TRY" && rate.isFinite() && rate > 0) {
                editor.putString("fx_$currency", rate.toString())
            }
        }
        editor.apply()
    }

    var lastRefresh: Long
        get() = prefs.getLong("last_refresh", 0L)
        set(value) {
            prefs.edit().putLong("last_refresh", value).apply()
        }
}
