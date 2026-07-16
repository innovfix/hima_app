package com.gmwapp.hima.utils

import android.util.Log
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Records the user's acceptance of the call-safety disclosure shown on the login
 * screen ("community guidelines & moderation policy").
 *
 * The version is never hardcoded: the server names the current one via /config,
 * and this echoes it back. A server-side version bump therefore invalidates every
 * prior acceptance and re-collects on next login with no client release.
 *
 * Capture stays unavailable until a consent row exists, so this must only be
 * called from a path where the disclosure was actually presented.
 */
object CallModerationConsent {

    private const val TAG = "CallModerationConsent"

    private const val APP_VERSION_CODE_HEADER = "X-Hima-Version-Code"

    private val executor = Executors.newSingleThreadExecutor()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fire-and-forget. Never blocks the caller and never surfaces an error: a
     * failure here leaves consent unrecorded, which fails closed (no capture)
     * and is retried on the user's next login.
     */
    fun submitIfRequired() {
        executor.execute {
            runCatching { submitBlocking() }
                .onFailure { Log.w(TAG, "Consent submit skipped: ${it.message}") }
        }
    }

    private fun submitBlocking() {
        val token = BaseApplication.getInstance()?.getPrefs()?.getAuthenticationToken().orEmpty()
        if (token.isBlank()) return

        val config = fetchConfig(token) ?: return
        if (!config.consentRequired || config.consentVersion.isBlank()) return

        val body = FormBody.Builder()
            .add("consent_version", config.consentVersion)
            .add("accepted", "1")
            .add("app_version", BuildConfig.VERSION_NAME)
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-moderation/consent")
            .header("Authorization", "Bearer $token")
            .header(APP_VERSION_CODE_HEADER, BuildConfig.VERSION_CODE.toString())
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "Consent recorded for ${config.consentVersion}")
            } else {
                // 409 means the disclosure changed under us; the next login picks
                // up the new version from /config and records that one instead.
                Log.w(TAG, "Consent rejected (${response.code}) for ${config.consentVersion}")
            }
        }
    }

    private data class Config(
        val consentRequired: Boolean,
        val consentVersion: String,
    )

    private fun fetchConfig(token: String): Config? {
        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}call-moderation/config")
            .header("Authorization", "Bearer $token")
            .header(APP_VERSION_CODE_HEADER, BuildConfig.VERSION_CODE.toString())
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val data = JSONObject(response.body?.string().orEmpty())
                .optJSONObject("data") ?: return@use null
            Config(
                consentRequired = data.optBoolean("consent_required", false),
                consentVersion = data.optString("consent_version", ""),
            )
        }
    }
}
