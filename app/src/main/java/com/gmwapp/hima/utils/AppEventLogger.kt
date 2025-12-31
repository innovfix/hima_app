package com.gmwapp.hima.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.LogAppEventResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.gmwapp.hima.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AppEventLogger {
    private val gson = Gson()
    
    private fun getApiManager(context: Context): ApiManager? {
        return try {
            val okHttpClientBuilder = OkHttpClient.Builder()
            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor()
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                okHttpClientBuilder.addInterceptor(loggingInterceptor)
            }
            val okHttpClient = okHttpClientBuilder.build()
            
            val gson = GsonBuilder().setLenient().create()
            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(okHttpClient)
                .build()
            
            ApiManager(retrofit)
        } catch (e: Exception) {
            Log.e("AppEventLogger", "Failed to create ApiManager: ${e.message}")
            null
        }
    }

    /**
     * Log an app event to the backend
     * This should be called whenever an event is logged to Firebase/Meta
     */
    fun logEvent(
        context: Context,
        eventName: String,
        platform: String, // "firebase", "meta", or "appsflyer"
        userId: Int? = null,
        params: Map<String, Any>? = null,
        value: Double? = null
    ) {
        try {
            // Get user ID from preferences if not provided
            val finalUserId = userId ?: BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id

            // Convert params map to JSON string
            val paramsJson = if (params != null && params.isNotEmpty()) {
                gson.toJson(params)
            } else {
                null
            }

            // Get device info
            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
            val appVersion = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
            val osVersion = "Android ${Build.VERSION.RELEASE}"

            // Get ApiManager instance
            val apiManager = getApiManager(context)
            if (apiManager == null) {
                Log.w("AppEventLogger", "ApiManager not available, skipping backend log")
                return
            }

            // Log to backend (fire-and-forget, non-blocking)
            apiManager.logAppEvent(
                eventName = eventName,
                userId = finalUserId,
                platform = platform,
                eventParams = paramsJson,
                eventValue = value,
                deviceInfo = deviceInfo,
                appVersion = appVersion,
                osVersion = osVersion,
                callback = object : NetworkCallback<LogAppEventResponse> {
                    override fun onResponse(
                        call: Call<LogAppEventResponse>,
                        response: Response<LogAppEventResponse>
                    ) {
                        if (response.isSuccessful && response.body() != null) {
                            Log.d("AppEventLogger", "✅ Event logged: $eventName ($platform)")
                        } else {
                            Log.e("AppEventLogger", "❌ Failed to log event $eventName: HTTP ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<LogAppEventResponse>, t: Throwable) {
                        Log.e("AppEventLogger", "❌ Failed to log event $eventName: ${t.message}")
                    }

                    override fun onNoNetwork() {
                        Log.w("AppEventLogger", "⚠️ No network for event: $eventName")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("AppEventLogger", "Error logging event $eventName: ${e.message}", e)
        }
    }

    /**
     * Helper to convert Bundle to Map for easier parameter handling
     */
    fun bundleToMap(bundle: android.os.Bundle): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        bundle.keySet().forEach { key ->
            val value = bundle.get(key)
            when (value) {
                is String -> map[key] = value
                is Int -> map[key] = value
                is Long -> map[key] = value
                is Double -> map[key] = value
                is Float -> map[key] = value
                is Boolean -> map[key] = value
                else -> map[key] = value.toString()
            }
        }
        return map
    }
}

