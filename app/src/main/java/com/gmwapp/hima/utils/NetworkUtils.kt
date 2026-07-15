package com.gmwapp.hima.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * B_002 — shared connectivity check for the incoming-call ACCEPT paths.
 *
 * Why: with no network the creator could still tap Accept. The app then walked
 * into the call UI, Agora could never join, and because the only safety timer
 * (startTimeoutTracking) is armed INSIDE onJoinChannelSuccess, it never armed —
 * so the call sat "connected" with no audio, forever, until force-closed.
 * The root cause is simply that an offline device is allowed to accept, so this
 * blocks that at the source instead of ending a dead call after the fact.
 *
 * Deliberately LENIENT + FAIL-OPEN:
 *  - Uses NET_CAPABILITY_INTERNET (not NET_CAPABILITY_VALIDATED): a captive
 *    portal / dead-but-present Wi-Fi still passes, so we never wrongly block an
 *    accept over a link that might work. That case is covered by the calling
 *    activities' never-joined watchdog instead.
 *  - Any lookup failure returns TRUE (assume online), so a bug or an odd OEM
 *    device can never stop a user answering a call.
 * Only a genuinely absent network (airplane mode / no signal → activeNetwork
 * null) is treated as offline.
 *
 * ACCESS_NETWORK_STATE is already declared in the manifest; ConnectivityManager
 * is already used elsewhere at runtime (BaseApplication, HomeFragment).
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    fun isOnline(context: Context?): Boolean {
        if (context == null) return true // fail-open
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true // fail-open
            val network = cm.activeNetwork ?: run {
                Log.d(TAG, "isOnline=false (no active network)")
                return false
            }
            val caps = cm.getNetworkCapabilities(network) ?: return true // fail-open
            val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!online) Log.d(TAG, "isOnline=false (active network has no INTERNET capability)")
            online
        } catch (t: Throwable) {
            Log.w(TAG, "isOnline check threw — assuming online (fail-open): ${t.message}")
            true // fail-open
        }
    }
}
