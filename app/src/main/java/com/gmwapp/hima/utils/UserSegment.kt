package com.gmwapp.hima.utils

import android.content.Context

/**
 * Single source of truth for "is this a new user?" — used to decide
 * which trial-offer surface to show (₹1 trial sheet vs the ₹299/10d
 * old-user subscribe sheet).
 *
 * Authoritative answer comes from the backend
 * (/api/auth/subscription_status → is_new_user), which compares the
 * account's created_at against AUTOPAY_LAUNCH_DATE and folds in
 * coins == 0 && !ever_active. Server-side check is the only place
 * with a reliable account-age signal — client created_at can be
 * stale, and PackageManager.firstInstallTime resets on reinstall.
 *
 * Until the cache is populated by the first subscription_status
 * fetch, this returns false (fail-closed → user treated as old).
 * Same convention as isActive / everActive on SubscriptionStateCache.
 */
object UserSegment {

    fun isNewUser(context: Context): Boolean =
        SubscriptionStateCache.isNewUser(context)

    fun isOldUser(context: Context): Boolean = !isNewUser(context)
}
