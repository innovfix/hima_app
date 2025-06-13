package com.gmwapp.hima.verification

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CashfreeRetrofitClient {
    private const val BASE_URL = "https://sandbox.cashfree.com/"

    val apiService: CashfreeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CashfreeApiService::class.java)
    }
}
