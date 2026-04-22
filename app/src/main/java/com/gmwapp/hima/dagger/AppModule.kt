package com.gmwapp.hima.dagger

import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.retrofit.ApiInterface
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponse
import com.gmwapp.hima.retrofit.responses.FcmNotificationResponseDeserializer
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.Interceptor.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.greenrobot.eventbus.EventBus
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okio.Buffer


@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun providesHttpLoggingInterceptor() = HttpLoggingInterceptor()
        .apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Singleton
    @Provides
    fun providesOkHttpClient(httpLoggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        val okClientBuilder = OkHttpClient.Builder().connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)

        okClientBuilder.addInterceptor(object : Interceptor {
            @Throws(IOException::class)
            override fun intercept(chain: Chain): Response {
                val request: Request.Builder = chain.request().newBuilder()
                
                // ✅ Get auth token and check if it's valid
                val authToken = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken() ?: ""
                
                // ✅ Log COMPLETE REQUEST for FCM API calls (debug builds only)
                if (BuildConfig.DEBUG && chain.request().url.toString().contains("send-fcm-notification")) {
                    android.util.Log.i("FCM_REQUEST_DEBUG", "════════════════════════════════════════")
                    android.util.Log.i("FCM_REQUEST_DEBUG", "📤 FCM NOTIFICATION REQUEST DETAILS:")
                    android.util.Log.i("FCM_REQUEST_DEBUG", "════════════════════════════════════════")
                    
                    // Log URL and method
                    android.util.Log.i("FCM_REQUEST_DEBUG", "📝 URL: ${chain.request().url}")
                    android.util.Log.i("FCM_REQUEST_DEBUG", "📝 Method: ${chain.request().method}")
                    
                    // Log Authorization Header
                    android.util.Log.i("FCM_REQUEST_DEBUG", "🔐 Authorization Token:")
                    if (authToken.isEmpty()) {
                        android.util.Log.e("FCM_REQUEST_DEBUG", "❌ ERROR: Auth token is EMPTY!")
                    } else {
                        android.util.Log.i("FCM_REQUEST_DEBUG", "✅ Token (first 50 chars): ${authToken.take(50)}...")
                        android.util.Log.i("FCM_REQUEST_DEBUG", "📝 Full Token: $authToken")
                    }
                    
                    // Log all request headers
                    android.util.Log.i("FCM_REQUEST_DEBUG", "📋 All Request Headers:")
                    chain.request().headers.forEach { (name, value) ->
                        android.util.Log.i("FCM_REQUEST_DEBUG", "  ├─ $name: $value")
                    }
                    
                    // Log request body (Form parameters)
                    try {
                        val originalRequest = chain.request()
                        if (originalRequest.body != null) {
                            android.util.Log.i("FCM_REQUEST_DEBUG", "📨 Request Body (Form Parameters):")
                            
                            // For form-encoded requests, we need to read the body
                            val buffer = Buffer()
                            originalRequest.body?.writeTo(buffer)
                            val requestBodyString = buffer.readUtf8()
                            
                            android.util.Log.i("FCM_REQUEST_DEBUG", "📝 Raw Body: $requestBodyString")
                            
                            // Parse form parameters
                            val params = requestBodyString.split("&")
                            params.forEach { param ->
                                android.util.Log.i("FCM_REQUEST_DEBUG", "  ├─ $param")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FCM_REQUEST_DEBUG", "⚠️ Error reading body: ${e.message}")
                    }
                    
                    android.util.Log.i("FCM_REQUEST_DEBUG", "════════════════════════════════════════")
                }
                
                request.header("Authorization", "Bearer $authToken")
                val response = chain.proceed(request.build())
                
                // ✅ Log raw FCM notification responses for debugging (debug builds only)
                if (BuildConfig.DEBUG && request.build().url.toString().contains("send-fcm-notification")) {
                    try {
                        val responseBody = response.body
                        val responseBodyString = responseBody?.string() ?: ""
                        
                        // Log all details
                        android.util.Log.i("FCM_API_DEBUG", "════════════════════════════════════════")
                        android.util.Log.i("FCM_API_DEBUG", "🔍 FCM API Response (Raw):")
                        android.util.Log.i("FCM_API_DEBUG", "📝 URL: ${request.build().url}")
                        android.util.Log.i("FCM_API_DEBUG", "📝 Status Code: ${response.code}")
                        android.util.Log.i("FCM_API_DEBUG", "📝 Content-Type: ${response.header("content-type")}")
                        android.util.Log.i("FCM_API_DEBUG", "📝 Content-Length: ${response.header("content-length")}")
                        android.util.Log.i("FCM_API_DEBUG", "📝 Response Body Length: ${responseBodyString.length}")
                        
                        // Check if response is HTML (backend error)
                        if (responseBodyString.contains("<!DOCTYPE html>") || responseBodyString.contains("<html")) {
                            android.util.Log.e("FCM_API_DEBUG", "❌ CRITICAL ERROR: Backend returned HTML instead of JSON!")
                            android.util.Log.e("FCM_API_DEBUG", "❌ This indicates /send-fcm-notification endpoint is misconfigured or missing on backend")
                        }
                        
                        android.util.Log.i("FCM_API_DEBUG", "📝 Response Body (Raw):\n$responseBodyString")
                        android.util.Log.i("FCM_API_DEBUG", "════════════════════════════════════════")
                        
                        // Re-create response body since we consumed it
                        return response.newBuilder()
                            .body(okhttp3.ResponseBody.create(response.body?.contentType(), responseBodyString))
                            .build()
                    } catch (e: Exception) {
                        android.util.Log.e("FCM_API_DEBUG", "❌ Error logging response: ${e.message}", e)
                    }
                }
                
                if(response.code == 401){
                    EventBus.getDefault().post(UnauthorizedEvent());
                }
                return response
            }
        })
        if (BuildConfig.DEBUG) {
            okClientBuilder.addInterceptor(httpLoggingInterceptor)
        }
        return okClientBuilder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {

        val gson = GsonBuilder()
            .setLenient()
            // ✅ Register custom deserializer for FCM notification response
            .registerTypeAdapter(FcmNotificationResponse::class.java, FcmNotificationResponseDeserializer())
            .create()


        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL) // Replace with your base URL
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiInterface(retrofit: Retrofit): ApiInterface {
        return retrofit.create(ApiInterface::class.java)
    }
}

