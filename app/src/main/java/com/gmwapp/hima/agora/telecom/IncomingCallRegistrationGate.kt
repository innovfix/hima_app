package com.gmwapp.hima.agora.telecom

/**
 * Process-local, bounded idempotency gate for incoming Telecom registration.
 *
 * FCM and OneSignal can deliver the same incoming call concurrently. Their
 * provider-level ownership check is useful for the normal ordered case, but it
 * cannot make the final Telecom registration atomic. This gate lets exactly
 * one delivery call addNewIncomingCall for a stable call key during the ring
 * window. Entries expire automatically so a missed lifecycle callback cannot
 * block a later call indefinitely.
 */
internal class IncomingCallRegistrationGate(
    private val claimTtlMs: Long
) {
    private val claimedAtMs = mutableMapOf<String, Long>()

    @Synchronized
    fun tryClaim(key: String, nowElapsedMs: Long): Boolean {
        val iterator = claimedAtMs.entries.iterator()
        while (iterator.hasNext()) {
            val claimedAt = iterator.next().value
            if (nowElapsedMs - claimedAt >= claimTtlMs) {
                iterator.remove()
            }
        }

        if (claimedAtMs.containsKey(key)) return false
        claimedAtMs[key] = nowElapsedMs
        return true
    }

    @Synchronized
    fun release(key: String) {
        claimedAtMs.remove(key)
    }
}
