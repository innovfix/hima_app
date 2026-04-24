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
 * All updates marshal to the main thread because Agora callbacks fire on an
 * internal worker thread.
 */
object CallQualityUi {

    /**
     * @param activity        the hosting activity (used for runOnUiThread)
     * @param signalIcon      the signal-strength image view
     * @param banner          the "Reconnecting…" pill view (gone by default)
     * @param quality         Agora rx quality from `onNetworkQuality`; pass
     *                        [Constants.QUALITY_UNKNOWN] if this call is purely
     *                        about a connection-state change.
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
            signalIcon?.let { applyQualityIcon(it, quality) }
            banner?.let { applyConnectionBanner(it, connectionState) }
        }
    }

    private fun applyQualityIcon(icon: ImageView, quality: Int) {
        // Agora guarantees these constants are stable ints.
        val drawable = when (quality) {
            Constants.QUALITY_EXCELLENT,
            Constants.QUALITY_GOOD -> R.drawable.ic_signal_strong
            Constants.QUALITY_POOR,
            Constants.QUALITY_BAD -> R.drawable.ic_signal_weak
            Constants.QUALITY_VBAD,
            Constants.QUALITY_DOWN -> R.drawable.ic_signal_poor
            // QUALITY_UNKNOWN (and any forward-compat values) — leave the icon
            // alone so a connection-state-only call doesn't wipe the last known
            // quality.
            else -> return
        }
        icon.setImageResource(drawable)
    }

    private fun applyConnectionBanner(banner: View, state: Int?) {
        if (state == null) return
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
