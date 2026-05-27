package com.gmwapp.hima.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * CHAT-028 / CHAT-029: a lightweight, persistent outbox for outbound TEXT
 * messages.
 *
 * Why it exists:
 *  - CHAT-028 "no queue/retry on network loss — messages dropped": a send that
 *    fails (offline / socket down / process death) is kept here and retried when
 *    connectivity returns instead of being silently lost.
 *  - CHAT-029 "sent message disappears after back/reopen": because the queue is
 *    persisted (SharedPreferences), a not-yet-confirmed message survives leaving
 *    and reopening the chat, and is re-surfaced / retried on reopen.
 *
 * Each entry is keyed by [Pending.clientMsgId] — the same id the app sends to the
 * server as `client_msg_id`. The server de-dupes on it (CHAT-023/024), so a retry
 * can never create a duplicate even if the original was actually delivered.
 *
 * This is intentionally a simple JSON store rather than a Room database: it keeps
 * the change additive and dependency-free, while still giving durable
 * at-least-once delivery for text messages. (Media is excluded — its payload is a
 * local file that needs its own upload/retry handling.)
 */
object ChatOutbox {
    private const val PREF = "chat_outbox"
    private const val KEY = "pending_v1"
    private const val MAX_ATTEMPTS = 50
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000 // give up after 24h

    data class Pending(
        val clientMsgId: String,
        val chatId: String,
        val peerUserId: Int,
        val body: String,
        val createdAt: Long,
        val attempts: Int
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    private fun readAll(ctx: Context): MutableList<Pending> {
        val raw = prefs(ctx).getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Pending>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Pending(
                        clientMsgId = o.optString("cid"),
                        chatId = o.optString("chat"),
                        peerUserId = o.optInt("peer"),
                        body = o.optString("body"),
                        createdAt = o.optLong("ts"),
                        attempts = o.optInt("att", 0)
                    )
                )
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun writeAll(ctx: Context, list: List<Pending>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("cid", p.clientMsgId)
                    put("chat", p.chatId)
                    put("peer", p.peerUserId)
                    put("body", p.body)
                    put("ts", p.createdAt)
                    put("att", p.attempts)
                }
            )
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Persist a text send. No-op if [clientMsgId] is already queued. */
    @Synchronized
    fun enqueueText(ctx: Context, chatId: String, clientMsgId: String, peerUserId: Int, body: String) {
        val list = readAll(ctx)
        if (list.any { it.clientMsgId == clientMsgId }) return
        list.add(Pending(clientMsgId, chatId, peerUserId, body, System.currentTimeMillis(), 0))
        writeAll(ctx, list)
    }

    /** Drop an entry once it is confirmed sent (or known duplicate). */
    @Synchronized
    fun remove(ctx: Context, clientMsgId: String?) {
        if (clientMsgId.isNullOrEmpty()) return
        val list = readAll(ctx)
        val filtered = list.filterNot { it.clientMsgId == clientMsgId }
        if (filtered.size != list.size) writeAll(ctx, filtered)
    }

    /**
     * Pending items for one chat. Also prunes entries that are too old or have
     * exhausted their retry budget, so the queue can't grow unbounded.
     */
    @Synchronized
    fun pendingForChat(ctx: Context, chatId: String): List<Pending> {
        val now = System.currentTimeMillis()
        val list = readAll(ctx)
        val keep = list.filter { now - it.createdAt < MAX_AGE_MS && it.attempts < MAX_ATTEMPTS }
        if (keep.size != list.size) writeAll(ctx, keep)
        return keep.filter { it.chatId == chatId }
    }

    /** Bump the attempt counter so a permanently-failing send eventually ages out. */
    @Synchronized
    fun markAttempt(ctx: Context, clientMsgId: String) {
        val list = readAll(ctx)
        var changed = false
        val updated = list.map {
            if (it.clientMsgId == clientMsgId) {
                changed = true
                it.copy(attempts = it.attempts + 1)
            } else {
                it
            }
        }
        if (changed) writeAll(ctx, updated)
    }
}
