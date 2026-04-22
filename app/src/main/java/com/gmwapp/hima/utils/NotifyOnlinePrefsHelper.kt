package com.gmwapp.hima.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Reads/writes the same `notify_online_prefs` bucket as [com.gmwapp.hima.adapters.ChatListAdapter].
 */
object NotifyOnlinePrefsHelper {

    const val PREFS_NAME = "notify_online_prefs"
    private val NOTIFY_ID_PATTERN = Regex("^notify_(\\d+)$")

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SubscribedCreator(
        val id: Int,
        val displayName: String,
        val imageUrl: String?,
        /** "bell" | "repeat_caller" | "both" | null when loaded from local cache only */
        val triggerReason: String? = null,
        val isOnline: Boolean = false,
        /** True when row came from list_creator_online_notifications */
        val subscribedFromApi: Boolean = false
    )

    /**
     * All creator IDs where `notify_<id>` is true, sorted by display name.
     */
    fun loadSubscribedCreators(context: Context): List<SubscribedCreator> {
        val sp = prefs(context)
        val out = mutableListOf<SubscribedCreator>()
        for ((key, value) in sp.all) {
            val m = NOTIFY_ID_PATTERN.matchEntire(key) ?: continue
            if (value !is Boolean || !value) continue
            val id = m.groupValues[1].toIntOrNull() ?: continue
            val name = sp.getString("notify_name_$id", null)?.takeIf { it.isNotBlank() } ?: "User"
            val image = sp.getString("notify_image_$id", null)?.takeIf { it.isNotBlank() }
            out.add(SubscribedCreator(id, name, image))
        }
        return out.sortedBy { it.displayName.lowercase() }
    }

    fun setSubscribedWithMeta(
        context: Context,
        femaleUserId: Int,
        enabled: Boolean,
        displayName: String?,
        imageUrl: String?
    ) {
        if (femaleUserId <= 0) return
        val sp = prefs(context).edit()
        val idStr = femaleUserId.toString()
        sp.putBoolean("notify_$idStr", enabled)
        if (enabled) {
            if (!displayName.isNullOrBlank()) sp.putString("notify_name_$idStr", displayName)
            if (!imageUrl.isNullOrBlank()) sp.putString("notify_image_$idStr", imageUrl)
        } else {
            sp.remove("notify_name_$idStr")
            sp.remove("notify_image_$idStr")
        }
        sp.apply()
    }

    fun clearCreatorMeta(context: Context, femaleUserId: Int) {
        if (femaleUserId <= 0) return
        val idStr = femaleUserId.toString()
        prefs(context).edit()
            .putBoolean("notify_$idStr", false)
            .remove("notify_name_$idStr")
            .remove("notify_image_$idStr")
            .apply()
    }
}
