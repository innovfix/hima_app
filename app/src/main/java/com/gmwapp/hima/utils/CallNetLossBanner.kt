package com.gmwapp.hima.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * NET-002 / NET-003 — shows a "No internet" banner over a call screen ONLY on a
 * REAL device-level internet loss (airplane mode / data pack ended / full tower
 * loss), and NEVER on brief Agora packet-loss blips.
 *
 * The distinction: a blip does NOT drop the OS network (the phone still has a
 * validated internet capability); a genuine loss does. So this keys on Android's
 * [ConnectivityManager] — not on Agora — which is exactly why it cannot
 * false-flash the way the old v1067 Agora-driven banner did. (That one is left
 * disabled; this is a separate, OS-driven signal.)
 *
 * Built to be maximally safe and non-invasive:
 *  - Creates/removes its OWN overlay TextView on android.R.id.content — no layout
 *    file changes and zero interaction with the existing reconnectBanner logic.
 *  - All view toggles run on the main thread.
 *  - 2.5s debounce → a momentary validation flap never flashes the banner.
 *  - Fail-OPEN on ANY error → never shows a false "no internet".
 *  - Auto-unregisters + removes the view on the activity's ON_DESTROY (no leak).
 *
 * Usage: `CallNetLossBanner.attach(this)` once in a call activity's onCreate.
 */
object CallNetLossBanner {

    private const val TAG = "CallNetLossBanner"
    private const val LOSS_DEBOUNCE_MS = 2_500L
    private const val BANNER_TEXT = "No internet — reconnecting…"

    /** Attach to a call activity; self-cleans on ON_DESTROY. Safe no-op on any failure. */
    fun attach(activity: Activity) {
        val owner = activity as? LifecycleOwner ?: return
        val w = Watcher(activity)
        if (!w.start()) return
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { w.stop() }
        })
    }

    private class Watcher(private val activity: Activity) {
        private val cm = activity.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        private val main = Handler(Looper.getMainLooper())
        private var registered = false
        private var banner: TextView? = null
        private val showRunnable = Runnable { showBanner() }

        private val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { main.post { evaluate() } }
            override fun onAvailable(network: Network) { main.post { evaluate() } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                main.post { evaluate() }
            }
        }

        /** True if the phone has a VALIDATED internet connection right now. Fail-open. */
        private fun hasInternet(): Boolean = try {
            val c = cm ?: return true
            val n = c.activeNetwork ?: return false
            val caps = c.getNetworkCapabilities(n) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (t: Throwable) {
            true // never show a false "no internet" on an unexpected error
        }

        private fun evaluate() {
            if (activity.isFinishing || activity.isDestroyed) return
            if (hasInternet()) {
                main.removeCallbacks(showRunnable)
                hideBanner()
            } else if (banner == null) {
                // Only commit to showing if STILL down after the debounce window.
                main.removeCallbacks(showRunnable)
                main.postDelayed(showRunnable, LOSS_DEBOUNCE_MS)
            }
        }

        fun start(): Boolean {
            val c = cm ?: return false
            if (registered) return true
            return try {
                c.registerDefaultNetworkCallback(cb)
                registered = true
                main.post { evaluate() }
                true
            } catch (t: Throwable) {
                Log.w(TAG, "register failed (safe): ${t.message}")
                false
            }
        }

        fun stop() {
            main.removeCallbacks(showRunnable)
            if (registered) {
                try { cm?.unregisterNetworkCallback(cb) } catch (_: Throwable) {}
                registered = false
            }
            hideBanner()
        }

        private fun showBanner() {
            if (banner != null || activity.isFinishing || activity.isDestroyed) return
            if (hasInternet()) return // recovered during the debounce — abort
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val tv = TextView(activity).apply {
                text = BANNER_TEXT
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#D0000000"))
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                val padV = (12 * resources.displayMetrics.density).toInt()
                setPadding(0, padV, 0, padV)
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            runCatching { root.addView(tv, lp) }
            banner = tv
        }

        private fun hideBanner() {
            banner?.let { b -> runCatching { (b.parent as? ViewGroup)?.removeView(b) } }
            banner = null
        }
    }
}
