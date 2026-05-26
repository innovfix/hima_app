package com.gmwapp.hima.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TrialOfferConfigResponse
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
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
 * Empty video_url is a valid state — admin hasn't uploaded a video
 * for the user's language yet. Bottom sheet falls back to the static
 * placeholder hero in that case. Same applies to each text override:
 * null/blank means the bottom sheet keeps its hardcoded XML default.
 */
object TrialOfferConfigCache {

    private const val PREFS = "TrialOfferConfigCache"
    private const val KEY_VIDEO = "video_url"
    // 2026-05-26 — restored YouTube fallback. mp4 (video_url) wins when both
    // are set; we surface the YouTube URL only as a fallback for admins who
    // already have a YouTube link saved and haven't switched to mp4 yet.
    private const val KEY_YOUTUBE = "youtube_url"
    private const val VIDEO_CACHE_DIR = "trial_videos"
    private val downloadExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var inFlightDownloadUrl: String? = null
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
        /**
         * @param videoUrl absolute mp4 URL the app should play in VideoView.
         *                 When non-blank, takes priority over [youtubeUrl].
         * @param youtubeUrl YouTube watch/share/shorts URL the app should
         *                   embed via YouTubePlayerView when [videoUrl] is null.
         */
        fun onConfig(videoUrl: String?, youtubeUrl: String?, texts: TextOverrides)
    }

    /**
     * Always invokes the listener at least once. If a fresh cache is
     * available, fires synchronously with the cached value. If stale
     * or missing, fires once with the cached value (for instant UI),
     * then again with the fresh value once the API responds.
     */
    fun load(context: Context, apiManager: ApiManager, userId: Int, listener: Listener) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString(KEY_VIDEO, null)
        val cachedYoutube = prefs.getString(KEY_YOUTUBE, null)
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
        listener.onConfig(cachedUrl, cachedYoutube, cachedTexts)

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
                // Kick off (or no-op if already cached) the file download
                // so the next bottom-sheet open plays from disk in <1 s
                // instead of streaming the 20+ MB clip on every open.
                data.video_url?.let { prefetchVideoFile(context.applicationContext, it) }
                prefs.edit()
                    .putString(KEY_VIDEO, data.video_url)
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
                if (data.video_url != cachedUrl || data.youtube_url != cachedYoutube || freshTexts != cachedTexts) {
                    listener.onConfig(data.video_url, data.youtube_url, freshTexts)
                }
            }
            override fun onFailure(call: Call<TrialOfferConfigResponse>, t: Throwable) {
                // Silent — UI already rendered from cache (or placeholder).
            }
            override fun onNoNetwork() { /* same — silent */ }
        })
        // Also re-attempt prefetch from the cached URL so cold-start
        // opens (no API yet) still start downloading immediately.
        prefs.getString(KEY_VIDEO, null)?.let {
            prefetchVideoFile(context.applicationContext, it)
        }
    }

    /**
     * Lightweight entry point for callers that just want to warm the
     * disk cache (MainActivity onResume etc.). Hits /trial_offer_config
     * and downloads the resulting mp4 in the background. No UI listener.
     */
    fun prefetch(context: Context, apiManager: ApiManager, userId: Int) {
        load(context, apiManager, userId, object : Listener {
            override fun onConfig(videoUrl: String?, youtubeUrl: String?, texts: TextOverrides) { /* no-op */ }
        })
    }

    /**
     * Extract the 11-char YouTube video id from common URL forms
     * (youtu.be/ID, youtube.com/watch?v=ID, youtube.com/shorts/ID,
     * youtube.com/embed/ID). Returns null on anything else so the
     * caller can fall back to the static placeholder hero.
     */
    fun extractYouTubeId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})"""),
            Regex("""[?&]v=([a-zA-Z0-9_-]{11})""")
        )
        for (p in patterns) {
            val m = p.find(url) ?: continue
            return m.groupValues[1]
        }
        return null
    }

    /**
     * Local file holding the cached video for the given remote URL, or
     * null if the file isn't on disk yet. BottomSheetTrialOffer hands
     * this to VideoView when present so playback starts from disk
     * instead of streaming the 20+ MB clip every open.
     */
    fun getCachedVideoFile(context: Context, remoteUrl: String?): File? {
        if (remoteUrl.isNullOrBlank()) return null
        val f = videoCacheFileFor(context, remoteUrl) ?: return null
        return if (f.exists() && f.length() > 0L) f else null
    }

    /**
     * First-frame still extracted from the cached mp4. Used as the
     * cover image while MediaPlayer is preparing so there's no visible
     * transition from a generic gradient to the actual video — the
     * cover IS the first frame, the swap is seamless.
     */
    fun getCachedPosterFile(context: Context, remoteUrl: String?): File? {
        if (remoteUrl.isNullOrBlank()) return null
        val f = posterCacheFileFor(context, remoteUrl) ?: return null
        return if (f.exists() && f.length() > 0L) f else null
    }

    /**
     * Downloads [remoteUrl] to the app's cache dir on a background
     * thread if not already present. Single-flight via [inFlightDownloadUrl]
     * so repeated prefetch() calls (every chat onResume etc.) don't
     * stack up. The cache key is a SHA-256 of the remote URL — when
     * admin uploads a new file (new URL), this becomes a different
     * cache entry, so stale clips don't keep playing forever.
     */
    private fun prefetchVideoFile(context: Context, remoteUrl: String) {
        if (remoteUrl.isBlank()) return
        val target = videoCacheFileFor(context, remoteUrl) ?: return
        if (target.exists() && target.length() > 0L) return
        // Single-flight: if we're already downloading this URL, skip.
        synchronized(this) {
            if (inFlightDownloadUrl == remoteUrl) return
            inFlightDownloadUrl = remoteUrl
        }
        downloadExecutor.execute {
            val tmp = File(target.parentFile, target.name + ".part")
            try {
                target.parentFile?.mkdirs()
                val conn = URL(remoteUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.requestMethod = "GET"
                if (conn.responseCode !in 200..299) {
                    Log.w("TrialOfferCache", "prefetch HTTP ${conn.responseCode} for $remoteUrl")
                    return@execute
                }
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                // Atomic rename so partial downloads (interrupted Wi-Fi)
                // never get served as if they were complete.
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                // Extract the first frame and write it next to the mp4.
                // The bottom sheet shows this poster as its cover while
                // MediaPlayer is preparing so the swap from cover→video
                // is invisible instead of "blue gradient → video".
                extractAndSavePoster(context, remoteUrl, target)
                // Drop other entries in the cache dir so it doesn't grow
                // unbounded across admin re-uploads.
                val keepNames = setOf(target.name, tmp.name, posterCacheFileFor(context, remoteUrl)?.name)
                target.parentFile?.listFiles()?.forEach { f ->
                    if (f.name !in keepNames) f.delete()
                }
            } catch (t: Throwable) {
                Log.w("TrialOfferCache", "prefetch failed: ${t.message}")
                tmp.delete()
            } finally {
                synchronized(this) {
                    if (inFlightDownloadUrl == remoteUrl) inFlightDownloadUrl = null
                }
            }
        }
    }

    private fun videoCacheFileFor(context: Context, remoteUrl: String): File? {
        val key = sha256(remoteUrl) ?: return null
        // Preserve the original extension if we can pull one off the URL —
        // some MediaPlayer extractors sniff format by suffix.
        val ext = Uri.parse(remoteUrl).lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.length in 1..5 }
            ?: "mp4"
        return File(File(context.cacheDir, VIDEO_CACHE_DIR), "$key.$ext")
    }

    private fun posterCacheFileFor(context: Context, remoteUrl: String): File? {
        val key = sha256(remoteUrl) ?: return null
        return File(File(context.cacheDir, VIDEO_CACHE_DIR), "$key.poster.jpg")
    }

    /**
     * Pull the first frame out of the cached mp4 with
     * MediaMetadataRetriever and write it as a JPEG poster next to
     * the video file. Best-effort — silent on failure so a missing
     * poster just leaves the bottom sheet's gradient cover in place.
     */
    private fun extractAndSavePoster(context: Context, remoteUrl: String, mp4: File) {
        val poster = posterCacheFileFor(context, remoteUrl) ?: return
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mp4.absolutePath)
            val frame: Bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return
            poster.outputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            frame.recycle()
        } catch (t: Throwable) {
            Log.w("TrialOfferCache", "poster extract failed: ${t.message}")
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun sha256(s: String): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) {
        null
    }
}
