package com.gmwapp.hima.utils

import android.content.Context

/**
 * Records the moment a friend request was accepted on THIS device, so the Friends list
 * can float a freshly accepted friend to the top.
 *
 * The `my_chat/friends` API returns no friendship timestamp, and a just-accepted friend has
 * no chat messages yet — so without this they fall into the alphabetical "no messages" bucket
 * mid-list instead of appearing first. [com.gmwapp.hima.fragments.FriendsTabFragment]'s sort
 * folds this timestamp in as `max(lastMessageTime, acceptedTime)`: the new friend sits on top
 * until real chat activity (a message) takes over the ordering naturally.
 *
 * Device-local only (never synced to the server). Keyed by owner id so a different account on
 * the same device never inherits the record. Entries older than [MAX_AGE_MILLIS] are pruned on
 * write so the store can't grow without bound.
 */
object RecentlyAcceptedFriendsPrefsHelper {

    private const val PREFS_NAME = "recently_accepted_friends_prefs"
    private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days

    private fun keyFor(ownerId: Int, friendId: Int): String = "accepted_at_${ownerId}_${friendId}"

    /** Stamps "friend accepted now" for this (owner, friend) pair and prunes stale entries. */
    fun recordAccepted(context: Context, ownerId: Int, friendId: Int) {
        if (ownerId <= 0 || friendId <= 0) return
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Prune anything older than the max age so this file stays small.
        prefs.all.forEach { (k, v) ->
            if (v is Long && now - v > MAX_AGE_MILLIS) editor.remove(k)
        }
        editor.putLong(keyFor(ownerId, friendId), now)
        editor.apply()
    }

    /** Epoch millis this friend was accepted on this device, or 0 if never/expired-and-pruned. */
    fun getAcceptedMillis(context: Context, ownerId: Int, friendId: Int): Long {
        if (ownerId <= 0 || friendId <= 0) return 0L
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(keyFor(ownerId, friendId), 0L)
    }

    /** Wipes only the records belonging to [ownerId] (call on logout). */
    fun clearForUser(context: Context, ownerId: Int) {
        if (ownerId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val prefix = "accepted_at_${ownerId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
