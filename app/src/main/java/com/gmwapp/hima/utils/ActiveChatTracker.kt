package com.gmwapp.hima.utils

import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-global tracker for "which 1:1 chat is currently open on screen".
 *
 * Used to let the OneSignal Notification Service Extension (which runs in its
 * own entry point, sometimes in a separate process) ask "is the user already
 * looking at the chat for peer X right now?" so incoming heads-ups can be
 * suppressed and replaced with an in-app refresh signal.
 *
 * Held in a single process-wide AtomicInteger so access is thread-safe and
 * cheap; zero means "no chat is open".
 */
object ActiveChatTracker {
    private val peerId = AtomicInteger(0)

    fun setActive(peer: Int) {
        peerId.set(peer.coerceAtLeast(0))
    }

    fun clear() {
        peerId.set(0)
    }

    fun isActiveFor(peer: Int): Boolean = peer > 0 && peerId.get() == peer

    fun currentPeer(): Int = peerId.get()
}
