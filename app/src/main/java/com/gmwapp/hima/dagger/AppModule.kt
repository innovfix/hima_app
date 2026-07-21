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
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Singleton
    @Provides
    fun providesOkHttpClient(httpLoggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        // Keep timeouts tight so a dying connection can't hold the UI hostage. The splash
        // screen has its own 8s fallback on top of this — both layers matter.
        val okClientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Don't silently follow redirects — a 302 to /login means auth failed and we
            // need to surface it, not swallow the login HTML as a successful response.
            .followRedirects(false)
            .followSslRedirects(false)

        okClientBuilder.addInterceptor(object : Interceptor {
            @Throws(IOException::class)
            override fun intercept(chain: Chain): Response {
                val request: Request.Builder = chain.request().newBuilder()
                
                // ✅ Get auth token and check if it's valid
                val authToken = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken() ?: ""

                // ✅ Log COMPLETE REQUEST for FCM API calls
                if (chain.request().url.toString().contains("send-fcm-notification")) {
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
                
                // ✅ Log raw FCM notification responses for debugging
                if (request.build().url.toString().contains("send-fcm-notification")) {
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
                
                // The web backend returns 302 -> /login when the bearer token is
                // invalid instead of a clean 401. With followRedirects disabled, we see
                // that 302 here and can surface it as an auth failure the same way.
                val location = response.header("Location").orEmpty()
                val isLoginRedirect = response.code in 300..399 &&
                    (location.contains("/login") || location.endsWith("login"))
                if (response.code == 401 || isLoginRedirect) {
                    EventBus.getDefault().post(UnauthorizedEvent())
                }
                return response
            }
        })
        // The support bot runs a model with tool calls and can legitimately
        // take up to the backend's 25s session ceiling. The global 20s
        // readTimeout would give up FIRST — the app would show a failure while
        // the backend was still producing a valid answer the user never sees.
        //
        // BOTH model-running endpoints need this, not just the first:
        //   support_bot_reply    — the first answer (step 3 -> 4).
        //   support_bot_feedback — the SECOND attempt after "No, still need
        //                          help" runs the model again on this path.
        // Missing feedback here was the original bug reappearing on the retry.
        // Every other call keeps the tight 20s.
        okClientBuilder.addInterceptor(object : Interceptor {
            @Throws(IOException::class)
            override fun intercept(chain: Chain): Response {
                val path = chain.request().url.encodedPath
                return if (path.endsWith("support_bot_reply") || path.endsWith("support_bot_feedback")) {
                    // The backend session ceiling is 35s, and a single answer can
                    // stack retries (mixed-script rewrite + grounding rewrite +
                    // language-guard rewrite), so a Telugu/native-script reply has
                    // been seen at ~18s and can climb toward the ceiling. The app
                    // MUST wait longer than the server's own limit or it shows
                    // "Something went wrong" while a valid answer is still coming.
                    // 45s > 35s ceiling, with margin for network.
                    chain.withReadTimeout(45, TimeUnit.SECONDS).proceed(chain.request())
                } else {
                    chain.proceed(chain.request())
                }
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

