package com.gmwapp.hima.utils

import android.app.Activity
import android.view.View
import android.widget.ImageView
import com.gmwapp.hima.R
import io.agora.rtc2.Constants

/**
 * Central place for mapping Agora quality/connection callbacks to the shared
 * in-call UI (signal-strength icon + "Reconnecting…" banner). Each of the four
 * calling activities wires its own [io.agora.rtc2.IRtcEngineEventHandler] but
 * the visual response is identical — keep that logic in one place so a future
 * tweak doesn't drift across four files.
 *
 * I006 — three behavioural improvements over the original drop-in mapping:
 *
 *  1. Callers pass the *worse* of tx/rx quality (computed at the call site),
 *     so upload problems show on our icon, not just download problems.
 *
 *  2. Downgrades are debounced — a single bad tick on an otherwise-good link
 *     is ignored. Two consecutive non-improving ticks are required before
 *     the icon flips to a worse bucket. Improvements (or recovery to the
 *     same bucket) apply immediately so the icon flickers less.
 *
 *  3. While the SDK reports CONNECTION_STATE_RECONNECTING / FAILED, we force
 *     the icon to "poor" so it can't sit on a stale green value while the
 *     reconnect banner is showing. CONNECTED resets the debounce state so
 *     the first quality tick of the recovered call applies immediately.
 *
 * All updates marshal to the main thread because Agora callbacks fire on an
 * internal worker thread. State below is a singleton — fine, because only one
 * calling activity is alive at any moment (parallel calls are blocked by
 * BaseApplication.isInRealCall / FcmUtils.isUserAvailable), and CONNECTED
 * resets it for each fresh call.
 */
object CallQualityUi {

    private enum class Bucket { STRONG, WEAK, POOR }

    @Volatile private var lastAppliedBucket: Bucket? = null
    @Volatile private var consecutiveWorseCount: Int = 0
    @Volatile private var forcedPoorByReconnect: Boolean = false

    /**
     * @param activity        the hosting activity (used for runOnUiThread)
     * @param signalIcon      the signal-strength image view
     * @param banner          the "Reconnecting…" pill view (gone by default)
     * @param quality         Agora quality bucket. Callers should pass
     *                        `maxOf(txQuality, rxQuality)` so the worse of
     *                        the two directions drives the icon. Pass
     *                        [Constants.QUALITY_UNKNOWN] if this call is
     *                        purely about a connection-state change.
     * @param connectionState Agora state from `onConnectionStateChanged`, or
     *                        `null` when this invocation is only about quality.
     */
    fun apply(
        activity: Activity,
        signalIcon: ImageView?,
        banner: View?,
        quality: Int,
        connectionState: Int?
    ) {
        activity.runOnUiThread {
            if (connectionState != null) {
                applyConnectionBanner(banner, connectionState)
                applyForcedIconForState(signalIcon, connectionState)
            }
            if (!forcedPoorByReconnect) {
                signalIcon?.let { applyQualityIcon(it, quality) }
            }
        }
    }

    /**
     * I006 #3 — while reconnecting, hold the icon at "poor" so the visible
     * pill ("Reconnecting…") and the icon can't disagree. Reset debounce
     * state on CONNECTED so the first quality tick of the recovered call is
     * applied immediately (no stale 2-tick wait).
     */
    private fun applyForcedIconForState(icon: ImageView?, state: Int) {
        when (state) {
            Constants.CONNECTION_STATE_RECONNECTING,
            Constants.CONNECTION_STATE_FAILED -> {
                forcedPoorByReconnect = true
                if (icon != null && lastAppliedBucket != Bucket.POOR) {
                    icon.setImageResource(R.drawable.ic_signal_poor)
                    lastAppliedBucket = Bucket.POOR
                }
            }
            Constants.CONNECTION_STATE_CONNECTED -> {
                forcedPoorByReconnect = false
                lastAppliedBucket = null
                consecutiveWorseCount = 0
            }
            // DISCONNECTED / CONNECTING: leave the icon alone.
        }
    }

    private fun applyQualityIcon(icon: ImageView, quality: Int) {
        val newBucket = bucketFor(quality) ?: return  // UNKNOWN — leave alone

        if (lastAppliedBucket == newBucket) {
            // No change in displayed bucket. Reset the worsening counter so a
            // future blip isn't counted against an old one.
            consecutiveWorseCount = 0
            return
        }

        val previous = lastAppliedBucket
        val isWorsening = previous != null && newBucket.ordinal > previous.ordinal

        if (isWorsening) {
            // I006 #2 — require two consecutive non-improving ticks before
            // downgrading. One blip on an otherwise-good link is absorbed.
            consecutiveWorseCount++
            if (consecutiveWorseCount < 2) return
        }

        consecutiveWorseCount = 0
        lastAppliedBucket = newBucket
        icon.setImageResource(drawableFor(newBucket))
    }

    private fun bucketFor(quality: Int): Bucket? = when (quality) {
        Constants.QUALITY_EXCELLENT,
        Constants.QUALITY_GOOD -> Bucket.STRONG
        Constants.QUALITY_POOR,
        Constants.QUALITY_BAD -> Bucket.WEAK
        Constants.QUALITY_VBAD,
        Constants.QUALITY_DOWN -> Bucket.POOR
        else -> null  // QUALITY_UNKNOWN and any forward-compat values
    }

    private fun drawableFor(bucket: Bucket): Int = when (bucket) {
        Bucket.STRONG -> R.drawable.ic_signal_strong
        Bucket.WEAK -> R.drawable.ic_signal_weak
        Bucket.POOR -> R.drawable.ic_signal_poor
    }

    private fun applyConnectionBanner(banner: View?, state: Int) {
        if (banner == null) return
        val visible = when (state) {
            Constants.CONNECTION_STATE_RECONNECTING,
            Constants.CONNECTION_STATE_FAILED -> true
            Constants.CONNECTION_STATE_CONNECTED,
            Constants.CONNECTION_STATE_DISCONNECTED -> false
            else -> return
        }
        banner.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
