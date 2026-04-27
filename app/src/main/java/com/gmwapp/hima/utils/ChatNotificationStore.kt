package com.gmwapp.hima.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-peer rolling store of the last few chat-message texts received while the app was
 * in the background. Used by the OneSignal NSE to fold multiple "yakini sent you a
 * message" pushes from one sender into a single WhatsApp-style MessagingStyle
 * notification that lists the last N texts instead of stacking N separate heads-ups.
 *
 * Intentionally backed by SharedPreferences (not an in-memory cache) because the NSE
 * can fire while the app process is killed, and the stack must survive across those
 * cold-start boundaries so a second push lands on top of the first.
 */
object ChatNotificationStore {

    data class Entry(val text: String, val ts: Long)

    private const val TAG = "ChatNotifStore"
    private const val PREFS = "chat_notif_store"
    private const val MAX_ENTRIES = 8

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun historyKey(peerId: Int) = "peer_$peerId"
    private fun metaNameKey(peerId: Int) = "meta_name_$peerId"
    private fun metaImageKey(peerId: Int) = "meta_image_$peerId"

    fun append(ctx: Context, peerId: Int, text: String, ts: Long): List<Entry> {
        val p = prefs(ctx)
        val current = readEntries(p, peerId).toMutableList()
        current.add(Entry(text, ts))
        while (current.size > MAX_ENTRIES) current.removeAt(0)

        val arr = JSONArray()
        current.forEach { entry ->
            arr.put(JSONObject().apply {
                put("t", entry.text)
                put("ts", entry.ts)
            })
        }
        p.edit().putString(historyKey(peerId), arr.toString()).apply()
        return current
    }

    fun get(ctx: Context, peerId: Int): List<Entry> = readEntries(prefs(ctx), peerId)

    fun clear(ctx: Context, peerId: Int) {
        prefs(ctx).edit().remove(historyKey(peerId)).apply()
    }

    /**
     * Drop every cached message stack and per-peer name/avatar entry. Call from
     * logout, FCM `clear_data`, and 401 unauthorized so the new account does not
     * see the previous user's stacked notification content for shared peer ids.
     */
    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun saveMeta(ctx: Context, peerId: Int, name: String, image: String?) {
        val editor = prefs(ctx).edit()
        if (name.isNotBlank()) editor.putString(metaNameKey(peerId), name)
        if (!image.isNullOrBlank()) editor.putString(metaImageKey(peerId), image)
        editor.apply()
    }

    /** Returns the last-known display name + image url for [peerId]. Defaults to ("User", null). */
    fun getMeta(ctx: Context, peerId: Int): Pair<String, String?> {
        val p = prefs(ctx)
        val name = p.getString(metaNameKey(peerId), null)?.takeIf { it.isNotBlank() } ?: "User"
        val image = p.getString(metaImageKey(peerId), null)?.takeIf { it.isNotBlank() }
        return name to image
    }

    private fun readEntries(p: android.content.SharedPreferences, peerId: Int): List<Entry> {
        val raw = p.getString(historyKey(peerId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val text = o.optString("t", "")
                    val ts = o.optLong("ts", 0L)
                    if (text.isNotEmpty() && ts > 0L) add(Entry(text, ts))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "readEntries peer=$peerId failed: ${e.message}")
            emptyList()
        }
    }
}
