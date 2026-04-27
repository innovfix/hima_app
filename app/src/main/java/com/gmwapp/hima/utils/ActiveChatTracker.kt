package com.gmwapp.hima.utils

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks "which 1:1 chat is currently open on screen", used by the OneSignal
 * Notification Service Extension to decide whether to suppress a heads-up for
 * the active conversation.
 *
 * **Process scope (T11):** the in-memory [AtomicInteger] is JVM-local, so if
 * the NSE ever runs in a separate Android process from the Activity, it would
 * always read 0 and suppression would silently fail. To stay correct in either
 * topology we mirror the active-peer state into a [android.content.SharedPreferences]
 * file with a timestamp; readers fall back to the prefs value when the in-memory
 * value is 0 and the timestamp is recent (≤60 s).
 */
object ActiveChatTracker {
    private const val PREFS = "active_chat_tracker"
    private const val KEY_PEER = "active_peer_id"
    private const val KEY_TS = "active_peer_ts"
    private const val FRESH_WINDOW_MS = 60_000L

    private val peerId = AtomicInteger(0)

    fun setActive(context: Context?, peer: Int) {
        val safe = peer.coerceAtLeast(0)
        peerId.set(safe)
        context?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putInt(KEY_PEER, safe)
            ?.putLong(KEY_TS, System.currentTimeMillis())
            ?.apply()
    }

    /**
     * In-memory only setter retained for callers that don't have a [Context]
     * handy (e.g. tests, transient state changes). Same-process callers should
     * prefer the variant above so cross-process readers also see the update.
     */
    fun setActive(peer: Int) {
        peerId.set(peer.coerceAtLeast(0))
    }

    fun clear(context: Context?) {
        peerId.set(0)
        context?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.remove(KEY_PEER)
            ?.remove(KEY_TS)
            ?.apply()
    }

    fun clear() {
        peerId.set(0)
    }

    fun isActiveFor(context: Context?, peer: Int): Boolean {
        if (peer <= 0) return false
        if (peerId.get() == peer) return true
        // Fall back to prefs in case the NSE runs in a separate process.
        val sp = context?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?: return false
        val storedPeer = sp.getInt(KEY_PEER, 0)
        if (storedPeer != peer) return false
        val ts = sp.getLong(KEY_TS, 0L)
        return ts > 0L && System.currentTimeMillis() - ts <= FRESH_WINDOW_MS
    }

    fun isActiveFor(peer: Int): Boolean = peer > 0 && peerId.get() == peer

    fun currentPeer(): Int = peerId.get()
}
