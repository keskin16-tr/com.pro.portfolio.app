package com.portfolio.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// --- Yahoo Finance API Yanıt Modelleri ---
data class YahooResponse(
    val chart: ChartData?
)

data class ChartData(
    val result: List<ChartResult?>?,
    val error: Any?
)

data class ChartResult(
    val meta: MetaData?
)

data class MetaData(
    val currency: String?,
    val symbol: String?,
    @SerializedName("regularMarketPrice") val regularMarketPrice: Double?,
    @SerializedName("previousClose") val previousClose: Double?
)

// --- Retrofit Servis Arayüzü ---
interface YahooFinanceApiService {

    @GET("v8/finance/chart/{ticker}")
    suspend fun getStockPrice(
        @Path("ticker") ticker: String
    ): YahooResponse

    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/"

        val instance: YahooFinanceApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YahooFinanceApiService::class.java)
        }
    }
}
