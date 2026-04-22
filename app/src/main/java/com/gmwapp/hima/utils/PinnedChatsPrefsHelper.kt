package com.gmwapp.hima.utils

import android.content.Context

/**
 * Local-only pinned chats (max 3). Order is preserved: index 0 = top of pinned section.
 * Stored as a comma-separated list of peer user IDs (same string form as [ChatConversation.userId]).
 */
object PinnedChatsPrefsHelper {

    const val PREFS_NAME = "pinned_chats_prefs"
    private const val KEY_ORDERED = "pinned_user_ids_ordered"
    const val MAX_PINNED = 3

    sealed class PinResult {
        object Added : PinResult()
        object AlreadyPinned : PinResult()
        object LimitReached : PinResult()
    }

    private fun getOrdered(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORDERED, null)
            .orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    private fun saveOrdered(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDERED, list.joinToString(","))
            .apply()
    }

    fun getPinnedIds(context: Context): List<String> = getOrdered(context)

    fun isPinned(context: Context, userId: String): Boolean =
        getOrdered(context).contains(userId)

    fun getPinnedCount(context: Context): Int = getOrdered(context).size

    /**
     * Pins [userId] at the front of the pinned list (most recently pinned first).
     */
    fun tryPin(context: Context, userId: String): PinResult {
        val list = getOrdered(context)
        if (list.contains(userId)) return PinResult.AlreadyPinned
        if (list.size >= MAX_PINNED) return PinResult.LimitReached
        list.add(0, userId)
        saveOrdered(context, list)
        return PinResult.Added
    }

    fun unpin(context: Context, userId: String) {
        val list = getOrdered(context)
        if (list.remove(userId)) {
            saveOrdered(context, list)
        }
    }
}
