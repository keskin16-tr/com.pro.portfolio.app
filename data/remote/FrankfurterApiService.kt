package com.portfolio.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class FrankfurterResponse(
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

interface FrankfurterApiService {
    @GET("latest")
    suspend fun getExchangeRates(
        @Query("from") base: String = "USD",
        @Query("to") symbols: String = "TRY,EUR,GBP"
    ): FrankfurterResponse

    companion object {
        private const val BASE_URL = "https://api.frankfurter.app/"

        val instance: FrankfurterApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FrankfurterApiService::class.java)
        }
    }
}
