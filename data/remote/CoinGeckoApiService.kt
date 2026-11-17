package com.portfolio.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApiService {
    @GET("api/v3/simple/price")
    suspend fun getCryptoPrices(
        @Query("ids") ids: String, // Örn: "bitcoin,ethereum,tether"
        @Query("vs_currencies") vsCurrencies: String = "usd"
    ): Map<String, Map<String, Double>>

    companion object {
        private const val BASE_URL = "https://coingecko.com/" // Veya resmi API endpoint

        val instance: CoinGeckoApiService by lazy {
            Retrofit.Builder()
                .baseUrl("https://api.coingecko.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CoinGeckoApiService::class.java)
        }
    }
}
