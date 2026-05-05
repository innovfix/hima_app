package com.gmwapp.hima.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.LanguageConfigData
import com.gmwapp.hima.retrofit.responses.LanguageConfigResponse
import retrofit2.Call
import retrofit2.Response

/**
 * Per-language feature flag cache.
 *
 * The admin panel sets `enabled_feature` per language ("autopay",
 * "ai_onboarding", "none"). This cache is the single source of truth
 * the app consults at runtime to decide whether to show autopay UI
 * surfaces (Wallet subscribe banner, Chat lock, trial sheet, crown,
 * cancel flow, etc.).
 *
 * In-memory @Volatile fields for fast synchronous reads + SharedPreferences
 * persistence so the value survives process death (admin flips propagate
 * on next foreground refresh; until then the last-known value is used).
 *
 * 24h TTL — refresh-on-launch via [refresh]; cache stays valid between
 * foregrounds for a day. Fetched again sooner whenever HomeFragment
 * resumes, so admin changes typically propagate within minutes.
 */
object LanguageFeatureCache {

    const val FEATURE_AUTOPAY = "autopay"
    const val FEATURE_AI_ONBOARDING = "ai_onboarding"
    const val FEATURE_NONE = "none"

    private const val PREFS = "LanguageFeatureCache"
    private const val KEY_FEATURE = "feature"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_RESUB = "re_sub_enabled"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val TTL_MS = 24L * 60L * 60L * 1000L

    @Volatile private var cachedFeature: String? = null
    @Volatile private var cachedLanguage: String? = null
    // Default true — when the cache is empty or backend returned the older
    // shape without this field, callers see "re-subscribe allowed" which
    // matches the new app default behaviour. Admin must explicitly flip
    // OFF in the panel for a language to lock lapsed users out.
    @Volatile private var cachedReSubEnabled: Boolean = true
    @Volatile private var lastFetchedMs: Long = 0L
    @Volatile private var prefsLoaded: Boolean = false

    private val _updates = MutableLiveData<String?>()
    /** Posts the new feature value when it changes. Observed by active screens to re-gate. */
    val updates: LiveData<String?> = _updates

    private fun ensureLoaded(context: Context) {
        if (prefsLoaded) return
        synchronized(this) {
            if (prefsLoaded) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            cachedFeature = prefs.getString(KEY_FEATURE, null)
            cachedLanguage = prefs.getString(KEY_LANGUAGE, null)
            cachedReSubEnabled = prefs.getBoolean(KEY_RESUB, true)
            lastFetchedMs = prefs.getLong(KEY_FETCHED_AT, 0L)
            prefsLoaded = true
        }
    }

    fun update(context: Context, data: LanguageConfigData) {
        val prevFeature = cachedFeature
        val prevReSub = cachedReSubEnabled
        cachedFeature = data.enabled_feature
        cachedLanguage = data.language
        cachedReSubEnabled = data.re_subscription_enabled ?: true
        lastFetchedMs = System.currentTimeMillis()
        prefsLoaded = true
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FEATURE, data.enabled_feature)
            .putString(KEY_LANGUAGE, data.language)
            .putBoolean(KEY_RESUB, cachedReSubEnabled)
            .putLong(KEY_FETCHED_AT, lastFetchedMs)
            .apply()
        // Emit on either change so subscribe-CTA gating refreshes when admin
        // flips re_subscription_enabled even if enabled_feature stays the same.
        if (prevFeature != data.enabled_feature || prevReSub != cachedReSubEnabled) {
            _updates.postValue(data.enabled_feature)
        }
    }

    fun clear(context: Context) {
        cachedFeature = null
        cachedLanguage = null
        cachedReSubEnabled = true
        lastFetchedMs = 0L
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun feature(context: Context): String? {
        ensureLoaded(context)
        return cachedFeature
    }

    /**
     * True only when the admin flag is explicitly "autopay". Unknown / null /
     * any other value → false. Callers default to "no autopay UI" when the
     * cache is uninitialised, which is the safer behaviour: a Tamil user
     * never sees a banner just because the first network fetch hasn't
     * landed yet.
     */
    fun isAutopayEnabled(context: Context): Boolean = feature(context) == FEATURE_AUTOPAY

    fun isAiOnboardingEnabled(context: Context): Boolean = feature(context) == FEATURE_AI_ONBOARDING

    /**
     * Cache-driven autopay flag with a cold-start fallback to a static
     * North-Indian language whitelist. Same source of truth used by
     * SelectLanguageActivity.fallbackFeature() — kept here so any gate
     * (Home, Wallet, Checkout) can ask one question instead of duplicating
     * the optimistic logic. Returns true when:
     *   - the cache says autopay (admin flag is "autopay"), OR
     *   - the cache hasn't populated yet AND the user's stored language
     *     is in the static autopay whitelist (so the very first tap after
     *     install isn't routed wrong on a slow/failed language_config).
     */
    fun isAutopayEligible(context: Context): Boolean {
        if (isAutopayEnabled(context)) return true
        if (isPopulated(context)) return false
        val lang = BaseApplication.getInstance()?.getPrefs()
            ?.getUserData()?.language?.trim()?.lowercase().orEmpty()
        return lang in AUTOPAY_LANGUAGE_WHITELIST
    }

    private val AUTOPAY_LANGUAGE_WHITELIST = setOf(
        "hindi", "bengali", "assamese", "gujarati",
        "punjabi", "odia", "marathi"
    )

    /**
     * Whether lapsed (cancelled / failed) users in this language are
     * allowed to re-subscribe to autopay. Admin-controlled per language.
     * Only meaningful for autopay languages — other languages don't have
     * subscribe surfaces to gate. Defaults to true on a cold cache so the
     * first session before language_config has refreshed sees the new
     * "re-subscription allowed" behaviour.
     */
    fun isReSubscriptionEnabled(context: Context): Boolean {
        ensureLoaded(context)
        return cachedReSubEnabled
    }

    fun isPopulated(context: Context): Boolean {
        ensureLoaded(context)
        return cachedFeature != null
    }

    fun isFresh(context: Context): Boolean {
        ensureLoaded(context)
        return lastFetchedMs > 0 && (System.currentTimeMillis() - lastFetchedMs) < TTL_MS
    }

    /**
     * Fires the language_config API and updates the cache on success.
     * Safe to call repeatedly — the LiveData only emits when the value
     * actually changes, so observers don't churn.
     */
    fun refresh(context: Context, apiManager: ApiManager, userId: Int, language: String) {
        if (userId <= 0 || language.isBlank()) return
        apiManager.languageConfig(userId, language, object : NetworkCallback<LanguageConfigResponse> {
            override fun onResponse(
                call: Call<LanguageConfigResponse>,
                response: Response<LanguageConfigResponse>
            ) {
                val data = response.body()?.data ?: return
                update(context, data)
            }
            override fun onFailure(call: Call<LanguageConfigResponse>, t: Throwable) { /* silent */ }
            override fun onNoNetwork() { /* silent */ }
        })
    }
}
