package com.gmwapp.hima.utils

import android.os.SystemClock
import android.util.Log
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.models.ChatMessage
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped cache for last successful chat history per peer, plus a short per-peer
 * rate-limit cool-down after HTTP 429 so rapid reopen does not hammer the API.
 *
 * The cool-down duration is driven by the server's `Retry-After` header when present
 * (see [parseRetryAfterMs]); otherwise falls back to [DEFAULT_COOLDOWN_MS].
 */
@Singleton
class ChatHistoryMemoryCache @Inject constructor() {

    companion object {
        private const val TAG = "ChatReopenTrace"

        /** Fallback cool-down when the server does not send a usable `Retry-After` header. */
        const val DEFAULT_COOLDOWN_MS = 3000L

        /** Guardrails so a bad server value can't either spam the API or lock the user out. */
        private const val MIN_COOLDOWN_MS = 500L
        private const val MAX_COOLDOWN_MS = 120_000L

        private const val MIN_FETCH_GAP_MS = 250L
        private const val MAX_CACHED_PEERS = 30

        /**
         * Parses the `Retry-After` response header (delta-seconds form per RFC 7231 §7.1.3).
         * HTTP-date form is not supported (Laravel's ThrottleRequests middleware always
         * emits seconds). Returns cool-down in ms clamped to the guardrails, or null if
         * the header is absent / non-numeric / non-positive.
         */
        fun parseRetryAfterMs(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val seconds = header.trim().toLongOrNull() ?: return null
            if (seconds <= 0L) return null
            return (seconds * 1000L).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
        }
    }

    private val snapshots = ConcurrentHashMap<Int, List<ChatMessage>>()
    private val snapshotStoredAtMs = ConcurrentHashMap<Int, Long>()

    /** Absolute epoch-ms at which the per-peer rate-limit cool-down expires. */
    private val rateLimitExpiresAtMs = ConcurrentHashMap<Int, Long>()

    @Volatile
    private var lastGlobalFetchElapsedMs: Long = 0L

    /**
     * Currently-signed-in user the cache belongs to. The cache is keyed only by peer id,
     * so an account switch (or re-login) on the same device must wipe everything to
     * prevent the previous user's chats from being shown to the next user.
     */
    @Volatile
    private var ownerUserId: Int = 0

    /**
     * Bind this cache to [userId]. If [userId] differs from the current owner, all
     * cached snapshots and rate-limit state are cleared first.
     *
     * Call sites: [com.gmwapp.hima.BaseApplication.onCreate] (process start),
     * the OTP success path in NewLoginActivity, and `ChatActivityInHouse.onCreate`.
     */
    @Synchronized
    fun setOwner(userId: Int) {
        if (userId == ownerUserId) return
        Log.d(TAG, "OWNER CHANGE oldOwner=$ownerUserId newOwner=$userId — clearing cache")
        snapshots.clear()
        snapshotStoredAtMs.clear()
        rateLimitExpiresAtMs.clear()
        lastGlobalFetchElapsedMs = 0L
        ownerUserId = userId
    }

    /**
     * Drop every snapshot, timestamp, and rate-limit entry. Used on logout, FCM
     * `clear_data`, and 401 (`onUnauthorizedEvent`) so a subsequent login on this
     * device starts from a clean cache.
     */
    @Synchronized
    fun clearAll() {
        snapshots.clear()
        snapshotStoredAtMs.clear()
        rateLimitExpiresAtMs.clear()
        lastGlobalFetchElapsedMs = 0L
        ownerUserId = 0
        Log.d(TAG, "CACHE CLEAR_ALL")
    }

    fun hasSnapshot(peerId: Int): Boolean = snapshots.containsKey(peerId)

    /** Returns a defensive copy safe to mutate in the activity list. */
    fun getSnapshot(peerId: Int): List<ChatMessage>? {
        val list = snapshots[peerId] ?: return null
        return list.map { it.copy(reactions = it.reactions.toMap()) }
    }

    fun snapshotAgeMs(peerId: Int): Long {
        val t = snapshotStoredAtMs[peerId] ?: return -1L
        return System.currentTimeMillis() - t
    }

    fun putSnapshot(peerId: Int, messages: List<ChatMessage>) {
        val oldCount = snapshots[peerId]?.size
        val copy = messages.map { it.copy(reactions = it.reactions.toMap()) }
        snapshots[peerId] = copy
        snapshotStoredAtMs[peerId] = System.currentTimeMillis()
        evictOldSnapshotsIfNeeded()
        Log.d(TAG, "CACHE PUT peer=$peerId count=${copy.size} replacedCount=$oldCount sizeMap=${snapshots.size}")
    }

    private fun evictOldSnapshotsIfNeeded() {
        while (snapshots.size > MAX_CACHED_PEERS) {
            val oldestPeer = snapshotStoredAtMs.minByOrNull { it.value }?.key ?: return
            snapshots.remove(oldestPeer)
            snapshotStoredAtMs.remove(oldestPeer)
            rateLimitExpiresAtMs.remove(oldestPeer)
            Log.d(TAG, "CACHE EVICT peer=$oldestPeer sizeMap=${snapshots.size}")
        }
    }

    /**
     * Records a rate-limit cool-down for [peerId]. Pass the value derived from the server's
     * `Retry-After` header (via [parseRetryAfterMs]) when available so we wait exactly as
     * long as the server asks; otherwise rely on the default.
     */
    fun recordRateLimit(
        peerId: Int,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        source: String = "default"
    ) {
        val effective = cooldownMs.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
        val expiresAt = System.currentTimeMillis() + effective
        rateLimitExpiresAtMs[peerId] = expiresAt
        Log.d(
            TAG,
            "RATE_LIMIT RECORDED peer=$peerId cooldownMs=$effective expiresAtMs=$expiresAt source=$source"
        )
    }

    fun clearRateLimit(peerId: Int, reason: String) {
        rateLimitExpiresAtMs.remove(peerId)
        Log.d(TAG, "RATE_LIMIT CLEARED peer=$peerId reason=$reason")
    }

    fun cooldownRemainMs(peerId: Int): Long {
        val expiresAt = rateLimitExpiresAtMs[peerId] ?: return 0L
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun shouldSkipFetch(peerId: Int): Boolean {
        val remain = cooldownRemainMs(peerId)
        val active = remain > 0L
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "RATE_LIMIT CHECK peer=$peerId active=$active remainMs=$remain")
        }
        return active
    }

    fun suggestedDelayMs(): Long {
        if (lastGlobalFetchElapsedMs == 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val since = now - lastGlobalFetchElapsedMs
        val delay = (MIN_FETCH_GAP_MS - since).coerceAtLeast(0L)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "THROTTLE CHECK sinceLastFetchMs=$since suggestedDelayMs=$delay")
        }
        return delay
    }

    fun recordFetchStarted() {
        lastGlobalFetchElapsedMs = SystemClock.elapsedRealtime()
    }
}
