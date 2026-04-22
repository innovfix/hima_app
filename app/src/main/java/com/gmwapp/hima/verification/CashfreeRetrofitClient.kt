package com.gmwapp.hima.verification

import com.gmwapp.hima.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CashfreeRetrofitClient {
    private const val BASE_URL = "https://sandbox.cashfree.com/"

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-client-id", BuildConfig.CASHFREE_CLIENT_ID)
                .addHeader("x-client-secret", BuildConfig.CASHFREE_CLIENT_SECRET)
                .build()
            chain.proceed(request)
        }
        .build()

    val apiService: CashfreeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CashfreeApiService::class.java)
    }
}
