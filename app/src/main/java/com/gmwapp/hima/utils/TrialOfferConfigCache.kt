package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TrialOfferConfigResponse
import retrofit2.Call
import retrofit2.Response

/**
 * SharedPreferences-backed cache for the trial-offer hero video
 * config. The bottom sheet renders instantly from cache on repeat
 * opens; every load() also fires a background fetch so admin
 * uploads/edits propagate on the very next sheet open. Result is
 * delivered via [Listener.onConfig] (twice if the fresh value differs
 * from the cached one).
 *
 * Empty youtube_url is a valid state — admin hasn't uploaded a video
 * for the user's language yet. Bottom sheet falls back to the static
 * placeholder hero in that case.
 */
object TrialOfferConfigCache {

    private const val PREFS = "TrialOfferConfigCache"
    private const val KEY_YOUTUBE = "youtube_url"
    private const val KEY_HEADLINE = "headline"
    private const val KEY_FETCHED_AT = "fetched_at"

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
        val cachedUrl = prefs.getString(KEY_YOUTUBE, null)
        val cachedHeadline = prefs.getString(KEY_HEADLINE, null)

        // 1) Fire immediately with whatever the cache has (even if null/stale)
        //    so the bottom sheet renders without a network round-trip wait.
        listener.onConfig(cachedUrl, cachedHeadline)

        // 2) Always background refresh — admin uploads/edits should propagate
        //    on the very next sheet open, not after a 24h TTL. The cached
        //    value above already gave the UI something to render; this just
        //    keeps it honest. Re-emits only if the value actually changed.
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
