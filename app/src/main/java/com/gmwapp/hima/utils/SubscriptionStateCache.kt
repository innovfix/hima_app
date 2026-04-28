package com.gmwapp.hima.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmwapp.hima.activities.DummySubscriptionActivity
import com.gmwapp.hima.retrofit.responses.SubscriptionStatusData

/**
 * In-memory cache of the user's subscription state, populated by the
 * SubscriptionStatusResponse from /api/auth/subscription_status.
 *
 * All synchronous callsites in the app (Home, Chat, adapters, etc.)
 * read through [isActive]/[everActive] which prefer the cached value
 * when available, falling back to the legacy SharedPreferences dummy
 * layer (DummySubscriptionActivity.PREFS) when the cache hasn't been
 * populated yet.
 *
 * The cache is invalidated and re-populated on:
 *   • Home onResume (refresh)
 *   • After autopay_initiate succeeds
 *   • After subscription_cancel succeeds
 *   • On OneSignal "subscription_status" push (instant flip)
 *
 * Once the dummy layer is removed (Chunk 9), the PREFS fallback can
 * be deleted — the cache will be the only source of truth.
 */
object SubscriptionStateCache {

    @Volatile private var cachedIsActive: Boolean? = null
    @Volatile private var cachedEverActive: Boolean? = null
    @Volatile private var cachedStatus: String? = null
    @Volatile private var cachedNextBillingDate: String? = null
    @Volatile private var cachedCancelledAt: String? = null
    @Volatile private var lastFetchedMs: Long = 0L

    fun update(data: SubscriptionStatusData) {
        cachedIsActive = data.is_active
        cachedEverActive = data.ever_active
        cachedStatus = data.status
        cachedNextBillingDate = data.next_billing_date
        cachedCancelledAt = data.cancelled_at
        lastFetchedMs = System.currentTimeMillis()
    }

    fun clear() {
        cachedIsActive = null
        cachedEverActive = null
        cachedStatus = null
        cachedNextBillingDate = null
        cachedCancelledAt = null
        lastFetchedMs = 0L
    }

    /** Cache-then-PREFS read of the active flag. */
    fun isActive(context: Context): Boolean {
        cachedIsActive?.let { return it }
        return context
            .getSharedPreferences(DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE)
            .getBoolean(DummySubscriptionActivity.KEY_ACTIVE, false)
    }

    /** Cache-then-PREFS read of the ever-active flag (lapsed-vs-never-subscribed). */
    fun everActive(context: Context): Boolean {
        cachedEverActive?.let { return it }
        return context
            .getSharedPreferences(DummySubscriptionActivity.PREFS, Context.MODE_PRIVATE)
            .getBoolean(DummySubscriptionActivity.KEY_EVER_ACTIVE, false)
    }

    fun status(): String? = cachedStatus
    fun nextBillingDate(): String? = cachedNextBillingDate
    fun cancelledAt(): String? = cachedCancelledAt
    fun lastFetchedMs(): Long = lastFetchedMs

    /** True if the cache has been populated from the API at least once. */
    fun isPopulated(): Boolean = cachedIsActive != null

    /**
     * Real-time push events from the OneSignal subscription_status handler.
     * Foreground activities observe this to react instantly (re-fetch
     * status, show ll_chat_ended_banner, swap chat lock UI).
     */
    enum class PushEvent { FAILED, CANCELLED }

    private val _pushEvent = MutableLiveData<PushEvent>()
    val pushEvent: LiveData<PushEvent> = _pushEvent

    fun postPushEvent(event: PushEvent) {
        clear()
        _pushEvent.postValue(event)
    }
}
