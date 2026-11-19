package com.portfolio.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.portfolio.app.data.remote.MarketRate

/**
 * Stale-while-revalidate cache for market rates.
 * The last successful response is rendered immediately; network refresh never clears it.
 */
class RatesCacheManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("market_rates_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun read(): List<MarketRate> {
        val json = preferences.getString(KEY_RATES, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<MarketRate>>() {}.type
            gson.fromJson<List<MarketRate>>(json, type).orEmpty()
                .filter { it.value.isFinite() && it.value > 0.0 }
        }.getOrDefault(emptyList())
    }

    fun write(rates: List<MarketRate>) {
        if (rates.isEmpty()) return
        preferences.edit()
            .putString(KEY_RATES, gson.toJson(rates))
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun savedAt(): Long = preferences.getLong(KEY_SAVED_AT, 0L)

    companion object {
        private const val KEY_RATES = "rates_json"
        private const val KEY_SAVED_AT = "saved_at"
    }
}
