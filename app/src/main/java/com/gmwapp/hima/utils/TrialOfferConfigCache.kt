package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TrialOfferConfigResponse
import retrofit2.Call
import retrofit2.Response

/**
 * 24h SharedPreferences-backed cache for the trial-offer hero video
 * config. The bottom sheet renders instantly from cache on repeat
 * opens; a stale cache (or first open) triggers a background fetch
 * whose result is delivered via [Listener.onConfig] when ready.
 *
 * Empty youtube_url is a valid state — admin hasn't uploaded yet.
 * Bottom sheet falls back to the static placeholder hero in that case.
 */
object TrialOfferConfigCache {

    private const val PREFS = "TrialOfferConfigCache"
    private const val KEY_YOUTUBE = "youtube_url"
    private const val KEY_HEADLINE = "headline"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val TTL_MS = 24L * 60L * 60L * 1000L  // 24 hours

    interface Listener {
        fun onConfig(youtubeUrl: String?, headline: String?)
    }

    /**
     * Always invokes the listener at least once. If a fresh cache is
     * available, fires synchronously with the cached value. If stale
     * or missing, fires once with the cached value (for instant UI),
     * then again with the fresh value once the API responds.
     */
    fun load(context: Context, apiManager: ApiManager, userId: Int, listener: Listener) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        val cachedUrl = prefs.getString(KEY_YOUTUBE, null)
        val cachedHeadline = prefs.getString(KEY_HEADLINE, null)
        val isFresh = fetchedAt > 0 && (System.currentTimeMillis() - fetchedAt) < TTL_MS

        // 1) Fire immediately with whatever the cache has (even if null/stale)
        //    so the bottom sheet renders without a network round-trip wait.
        listener.onConfig(cachedUrl, cachedHeadline)

        if (isFresh) return

        // 2) Background refresh — only if stale or missing.
        apiManager.trialOfferConfig(userId, object : NetworkCallback<TrialOfferConfigResponse> {
            override fun onResponse(
                call: Call<TrialOfferConfigResponse>,
                response: Response<TrialOfferConfigResponse>
            ) {
                val data = response.body()?.data ?: return
                prefs.edit()
                    .putString(KEY_YOUTUBE, data.youtube_url)
                    .putString(KEY_HEADLINE, data.headline)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()
                // Only re-emit if values actually changed; avoids flicker.
                if (data.youtube_url != cachedUrl || data.headline != cachedHeadline) {
                    listener.onConfig(data.youtube_url, data.headline)
                }
            }
            override fun onFailure(call: Call<TrialOfferConfigResponse>, t: Throwable) {
                // Silent — UI already rendered from cache (or placeholder).
            }
            override fun onNoNetwork() { /* same — silent */ }
        })
    }

    /** Extracts the 11-char YouTube video ID from any common URL form. */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/embed/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/shorts/([A-Za-z0-9_-]{11})"),
        )
        for (p in patterns) {
            val m = p.find(url) ?: continue
            return m.groupValues[1]
        }
        return null
    }
}
