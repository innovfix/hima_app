package com.gmwapp.hima.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny in-memory peer-id -> display-name cache.
 *
 * The socket message payload carries only the numeric sender id
 * ([com.gmwapp.hima.socket.ChatMessageSocket.from] is the stringified id, NOT a name),
 * so the in-app chat heads-up ([com.gmwapp.hima.BaseApplication] maybeShowInAppChatHeadsUp)
 * has no name to render. This cache is populated wherever a peer's name is already
 * known — the chat-list rows and every opened chat — and read back by id.
 *
 * Best-effort and non-authoritative: a miss simply falls back to a generic title, and
 * blanks / the "User" placeholder are never stored. Process-lifetime only.
 */
object PeerNameCache {

    private val names = ConcurrentHashMap<Int, String>()

    /** Store a known peer name. No-ops on invalid id or a blank/placeholder name. */
    fun put(peerId: Int, name: String?) {
        if (peerId <= 0) return
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty() || clean.equals("User", ignoreCase = true) ||
            clean.equals("Someone", ignoreCase = true)
        ) return
        names[peerId] = clean
    }

    /** Resolved name for [peerId], or null if not known yet. */
    fun get(peerId: Int): String? = if (peerId > 0) names[peerId] else null
}
