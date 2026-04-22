package com.gmwapp.hima.activities

import com.gmwapp.hima.retrofit.responses.UpiData
import com.gmwapp.hima.retrofit.responses.UpiDataDeserializer
import com.gmwapp.hima.utils.Config
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Config.APP_ROOT)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

}