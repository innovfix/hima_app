package com.gmwapp.hima.mmp

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.gmwapp.hima.BuildConfig
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Lightweight MMP (Mobile Measurement Platform) client.
 *
 * Usage:
 *  1. Call MmpClient.init(appContext) once in Application.onCreate().
 *     This reads the GAID on a background thread, caches it, and fires the
 *     install event on first launch (guarded by SharedPreferences).
 *  2. Call the track* / identify methods from any thread — they are all
 *     non-blocking (fire-and-forget on an internal executor).
 *
 * All methods are safe to call before init() returns (the executor queues
 * them until the GAID is available because init itself runs on the same
 * single-thread executor).
 */
object MmpClient {

    private val TAG = "MmpClient"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    // Single-thread executor guarantees serial ordering: init → install → events.
    private val executor = Executors.newSingleThreadExecutor()

    private val http = OkHttpClient()

    @Volatile private var gaid: String = ""
    @Volatile private var appCtx: Context? = null

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Must be called once in Application.onCreate().
     * Caches the GAID (with UUID fallback if unavailable / opted-out) and,
     * on first ever launch, fires trackInstall.
     */
    fun init(context: Context) {
        appCtx = context.applicationContext
        executor.execute {
            val prefs = context.getSharedPreferences("mmp", Context.MODE_PRIVATE)
            gaid = resolveDeviceId(context, prefs)
            if (!prefs.getBoolean("install_tracked", false)) {
                trackInstallInternal(context)
                prefs.edit().putBoolean("install_tracked", true).apply()
            }
        }
    }

    /**
     * Fire after a user completes registration.
     *
     * Internally calls identify FIRST so the device row has customer_user_id
     * set before the signup event is recorded. Both calls are serialized on
     * the same single-thread executor, so identify is guaranteed to be POSTed
     * before the signup event.
     */
    fun trackSignup(customerUserId: String, email: String? = null) {
        identify(customerUserId, email)
        post("/api/v1/event", JSONObject().apply {
            put("gaid", gaid)
            put("name", "signup")
            put("customer_user_id", customerUserId)
        })
    }

    /**
     * Fire after a successful coin purchase or subscription payment.
     * @param revenueInr Amount in Indian Rupees, e.g. 99.0 for ₹99.
     */
    fun trackPurchase(
        revenueInr: Double,
        productId: String? = null,
        customerUserId: String? = null
    ) {
        post("/api/v1/event", JSONObject().apply {
            put("gaid", gaid)
            put("name", "purchase")
            put("revenue", revenueInr)
            put("currency", "INR")
            productId?.let { put("params", JSONObject().put("product_id", it)) }
            customerUserId?.let { put("customer_user_id", it) }
        })
    }

    /**
     * Track any custom event.
     * @param params Optional key/value map that will be serialised as the `params` object.
     */
    fun trackEvent(
        eventName: String,
        revenue: Double? = null,
        currency: String = "INR",
        params: Map<String, Any>? = null,
        customerUserId: String? = null
    ) {
        post("/api/v1/event", JSONObject().apply {
            put("gaid", gaid)
            put("name", eventName)
            revenue?.let {
                put("revenue", it)
                put("currency", currency)
            }
            params?.let {
                val p = JSONObject()
                it.forEach { (k, v) -> p.put(k, v) }
                put("params", p)
            }
            customerUserId?.let { put("customer_user_id", it) }
        })
    }

    /**
     * Associate the device's GAID with your internal user ID.
     * Call after login and after registration.
     */
    fun identify(customerUserId: String, email: String? = null) {
        post("/api/v1/identify", JSONObject().apply {
            put("gaid", gaid)
            put("customer_user_id", customerUserId)
            email?.let { put("email", it) }
        })
    }

    /** GDPR: request erasure of a user's PII from MMP. */
    fun eraseUser() {
        post("/api/v1/privacy/erase", JSONObject().apply {
            put("gaid", gaid)
        })
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun trackInstallInternal(ctx: Context) {
        val referrer = readInstallReferrer(ctx)
        val appVersion = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }

        val body = JSONObject().apply {
            put("gaid", gaid)
            put("install_referrer", referrer)
            put("device_model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("os_version", Build.VERSION.RELEASE)
            put("app_version", appVersion)
            put("locale", java.util.Locale.getDefault().toLanguageTag())
            put("first_launch_at", System.currentTimeMillis())
        }

        post("/api/v1/install", body) { _, deepLink ->
            if (!deepLink.isNullOrBlank()) {
                // TODO: route deep-link to your DeepLinkActivity or NavController
                Log.d(TAG, "Deferred deep-link from MMP: $deepLink")
            }
        }
    }

    private fun post(
        path: String,
        body: JSONObject,
        onSuccess: ((installId: Int?, deepLink: String?) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val json = body.toString()
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val sig = hmac("$timestamp.$json", BuildConfig.MMP_SDK_SECRET)

                // Full payload being sent to the MMP server — endpoint, JSON body and the
                // signed headers (key/timestamp/signature). Filter logcat with tag "data_sent".
                Log.d(
                    "data_sent",
                    "MMP POST ${BuildConfig.MMP_BASE_URL}$path | body=$json | " +
                        "X-MMP-Key=${BuildConfig.MMP_SDK_KEY} | X-MMP-Timestamp=$timestamp | X-MMP-Signature=$sig"
                )

                val req = Request.Builder()
                    .url("${BuildConfig.MMP_BASE_URL}$path")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-MMP-Key", BuildConfig.MMP_SDK_KEY)
                    .addHeader("X-MMP-Timestamp", timestamp)
                    .addHeader("X-MMP-Signature", sig)
                    .post(json.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                http.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    Log.d(
                        "data_sent",
                        "MMP RESPONSE $path -> HTTP ${resp.code} (success=${resp.isSuccessful}) body=$respBody"
                    )
                    if (resp.isSuccessful && onSuccess != null) {
                        val res = JSONObject(respBody.ifBlank { "{}" })
                        onSuccess(
                            res.optInt("install_id").takeIf { it > 0 },
                            res.optString("deferred_deep_link").ifBlank { null }
                        )
                    }
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Non-2xx response for $path: ${resp.code} body=$respBody")
                    }
                }
            } catch (_: IOException) {
                // Fire-and-forget: network failures are silent; will retry on next relevant action.
            } catch (e: Exception) {
                Log.w(TAG, "MMP post failed for $path: ${e.message}")
            }
        }
    }

    private fun readInstallReferrer(ctx: Context): String {
        var ref = ""
        val latch = CountDownLatch(1)
        val client = InstallReferrerClient.newBuilder(ctx).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(code: Int) {
                if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                    ref = client.installReferrer.installReferrer ?: ""
                }
                client.endConnection()
                latch.countDown()
            }
            override fun onInstallReferrerServiceDisconnected() = latch.countDown()
        })
        latch.await(5, TimeUnit.SECONDS)
        return ref
    }

    /**
     * Resolve a stable device identifier with this priority:
     *   1. Real GAID (when Play Services is present and ad-tracking isn't limited)
     *   2. Previously stored fallback UUID
     *   3. Freshly generated UUID (persisted for next launch)
     *
     * On Android 14+ with "Limit ad personalization" enabled — and on devices
     * without Play Services — AdvertisingIdClient returns an empty string or
     * the all-zero UUID. Without a fallback, every install/event would key on
     * "" and device matching would break.
     */
    private fun resolveDeviceId(ctx: Context, prefs: android.content.SharedPreferences): String {
        val realGaid = readGaid(ctx)
        if (realGaid.isNotBlank() && realGaid != ZERO_UUID) return realGaid

        prefs.getString(KEY_FALLBACK_DEVICE_ID, null)?.let { return it }

        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_FALLBACK_DEVICE_ID, newId).apply()
        Log.d(TAG, "GAID unavailable, using fallback device id")
        return newId
    }

    private fun readGaid(ctx: Context): String = try {
        AdvertisingIdClient.getAdvertisingIdInfo(ctx).id ?: ""
    } catch (_: Exception) { "" }

    private const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
    private const val KEY_FALLBACK_DEVICE_ID = "mmp_device_id"

    private fun hmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
