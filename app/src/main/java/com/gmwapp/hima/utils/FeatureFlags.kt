package com.gmwapp.hima.utils

/**
 * Central on/off switches for features we want to hide without ripping the code out.
 *
 * Flip a flag back to `true` to fully restore the prior server-driven behaviour — every
 * gated UI / FCM path checks the flag here *in addition to* whatever other conditions
 * it had before, so the flag is strictly additive.
 */
object FeatureFlags {
    /**
     * When false:
     *   - all IPL room banners on Home are hidden regardless of server `ipl_rooms_enabled`,
     *   - the IPL team badge + picker on Profile is hidden,
     *   - the IPL team chip on female user cards is hidden,
     *   - IPL transaction rows render as plain call rows,
     *   - the IPL team-badge overlay in the call UI is hidden.
     *
     * The IPL activities / endpoints / manifest entries remain compiled so flipping
     * this back to true restores everything atomically.
     */
    const val IPL_ENABLED = false

    /**
     * When false:
     *   - the in-call "Play Ludo" button is hidden in all four calling activities,
     *   - the `FcmUtils.ludoEvent` observer is not registered (so stale pushes cannot
     *     surface the receive-invite dialog),
     *   - FCM messages for ludo_invite / ludo_invite_accepted / ludo_invite_rejected /
     *     ludo_invite_expired / game_end are dropped silently.
     */
    const val LUDO_ENABLED = false
}
