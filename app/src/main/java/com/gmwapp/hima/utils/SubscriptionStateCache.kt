package com.gmwapp.hima.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gmwapp.hima.retrofit.responses.SubscriptionStatusData

/**
 * In-memory cache of the user's subscription state, populated by
 * SubscriptionStatusResponse from /api/auth/subscription_status.
 *
 * Single source of truth across the app. Populated on resume from
 * Home / Chat / CancelSubscription / AutopayCheckout activities and
 * invalidated on OneSignal subscription_status push events. All
 * synchronous callsites in the app read through [isActive] /
 * [everActive] which return false until the first API response —
 * that's intentional: the app starts in a "not subscribed" UI state
 * and updates a moment later, same convention as the rest of the
 * backend-driven UI in this codebase.
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

    fun isActive(@Suppress("UNUSED_PARAMETER") context: android.content.Context): Boolean =
        cachedIsActive ?: false

    fun everActive(@Suppress("UNUSED_PARAMETER") context: android.content.Context): Boolean =
        cachedEverActive ?: false

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
