package com.gmwapp.hima.activities

import android.util.Log
import com.gmwapp.hima.retrofit.responses.UpiData
import com.gmwapp.hima.retrofit.responses.UpiDataDeserializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://himaapp.in/"

    val instance: ApiService by lazy {
        // Setup logger
        val logging = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                Log.d("API_LOG", message)
            }
        }).apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Build OkHttp client with logger
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        // Build Retrofit
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}