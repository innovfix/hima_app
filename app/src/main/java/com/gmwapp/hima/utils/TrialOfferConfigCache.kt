package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TrialOfferConfigResponse
import retrofit2.Call
import retrofit2.Response

/**
 * SharedPreferences-backed cache for the trial-offer hero video
 * and per-language text overrides. The bottom sheet renders
 * instantly from cache on repeat opens; every load() also fires a
 * background fetch so admin uploads/edits propagate on the very
 * next sheet open. Result is delivered via [Listener.onConfig]
 * (twice if the fresh value differs from the cached one).
 *
 * Empty youtube_url is a valid state — admin hasn't uploaded a video
 * for the user's language yet. Bottom sheet falls back to the static
 * placeholder hero in that case. Same applies to each text override:
 * null/blank means the bottom sheet keeps its hardcoded XML default.
 */
object TrialOfferConfigCache {

    private const val PREFS = "TrialOfferConfigCache"
    private const val KEY_YOUTUBE = "youtube_url"
    private const val KEY_HEADLINE = "headline"
    private const val KEY_PRICE = "price_text"
    private const val KEY_FEATURE1 = "feature_1_text"
    private const val KEY_FEATURE2 = "feature_2_text"
    private const val KEY_FOOTER = "footer_text"
    private const val KEY_CTA = "cta_text"
    private const val KEY_SECONDARY = "secondary_link_text"
    private const val KEY_FETCHED_AT = "fetched_at"

    /**
     * Per-language admin overrides for every editable line on the
     * BottomSheetTrialOffer surface. Any field may be null or blank,
     * which means "use the app's hardcoded default for that line".
     */
    data class TextOverrides(
        val headline: String?,
        val priceText: String?,
        val feature1Text: String?,
        val feature2Text: String?,
        val footerText: String?,
        val ctaText: String?,
        val secondaryLinkText: String?
    ) {
        companion object {
            val EMPTY = TextOverrides(null, null, null, null, null, null, null)
        }
    }

    interface Listener {
        fun onConfig(youtubeUrl: String?, texts: TextOverrides)
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
        val cachedTexts = TextOverrides(
            headline = prefs.getString(KEY_HEADLINE, null),
            priceText = prefs.getString(KEY_PRICE, null),
            feature1Text = prefs.getString(KEY_FEATURE1, null),
            feature2Text = prefs.getString(KEY_FEATURE2, null),
            footerText = prefs.getString(KEY_FOOTER, null),
            ctaText = prefs.getString(KEY_CTA, null),
            secondaryLinkText = prefs.getString(KEY_SECONDARY, null)
        )

        // 1) Fire immediately with whatever the cache has (even if null/stale)
        //    so the bottom sheet renders without a network round-trip wait.
        listener.onConfig(cachedUrl, cachedTexts)

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
                val freshTexts = TextOverrides(
                    headline = data.headline,
                    priceText = data.price_text,
                    feature1Text = data.feature_1_text,
                    feature2Text = data.feature_2_text,
                    footerText = data.footer_text,
                    ctaText = data.cta_text,
                    secondaryLinkText = data.secondary_link_text
                )
                prefs.edit()
                    .putString(KEY_YOUTUBE, data.youtube_url)
                    .putString(KEY_HEADLINE, freshTexts.headline)
                    .putString(KEY_PRICE, freshTexts.priceText)
                    .putString(KEY_FEATURE1, freshTexts.feature1Text)
                    .putString(KEY_FEATURE2, freshTexts.feature2Text)
                    .putString(KEY_FOOTER, freshTexts.footerText)
                    .putString(KEY_CTA, freshTexts.ctaText)
                    .putString(KEY_SECONDARY, freshTexts.secondaryLinkText)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()
                // Only re-emit if values actually changed; avoids flicker.
                if (data.youtube_url != cachedUrl || freshTexts != cachedTexts) {
                    listener.onConfig(data.youtube_url, freshTexts)
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
