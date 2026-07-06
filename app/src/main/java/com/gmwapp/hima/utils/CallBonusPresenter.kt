package com.gmwapp.hima.utils

import com.gmwapp.hima.retrofit.responses.BonusTier
import com.gmwapp.hima.retrofit.responses.CallBonusConfig

/**
 * F1 Call Duration Bonus — drives the on-screen popups on the creator call screen.
 *
 * DISPLAY ONLY. The real money is computed and credited server-side at call-end
 * settlement (CallBonusService). This class only decides WHICH popup to show and
 * WHEN, based on admin config + elapsed call time. It never credits anything.
 *
 * Option B (timed teasers): for each milestone it shows
 *   • an intro toast once at T+10s,
 *   • a teaser `leadSeconds` before the milestone ("stay 1 more min to earn +₹X"),
 *   • a payout flash at the milestone ("+₹X · added to your earnings").
 *
 * Every popup fires at most once. Because checks are `elapsed >= boundary` (not `==`),
 * a late/gappy tick still fires the popup as soon as ticking resumes — but a milestone
 * whose payout instant already passed on join is skipped (no stale teaser).
 */
class CallBonusPresenter(
    private val onIntro: () -> Unit,
    private val onTeaser: (tier: BonusTier) -> Unit,
    private val onPayout: (tier: BonusTier) -> Unit
) {
    private var enabled = false
    private var leadSeconds = 60
    private var tiers: List<BonusTier> = emptyList()

    private var introShown = false
    private val teaserShown = HashSet<Int>()
    private val payoutShown = HashSet<Int>()

    // Collision guard: when a payout fires, its celebration (scrim + card) owns the
    // screen for a few seconds. A teaser due in that same window (happens when the
    // milestone gap ≤ teaser lead, e.g. video tiers 1/2/3 min with a 60s lead) would
    // be drowned by the payout. So hold any teaser until the payout has cleared, then
    // let it fire — as long as its own window hasn't closed. 0 = no hold active.
    private var teaserHoldUntilSec = 0L

    // Cap-awareness: how much bonus the creator can still earn today (server-provided,
    // refreshed via get_remaining_time). -1 = unlimited/unknown → show everything;
    // 0 = daily cap already reached → show nothing (else the popup would promise a
    // bonus the server won't credit); >0 = only show popups that still fit the room.
    private var remainingToday: Double = -1.0
    private var cumulativeShown: Double = 0.0

    /** Server tells us the creator's remaining daily bonus room (rupees). */
    fun setRemainingToday(remaining: Int?) {
        if (remaining != null) remainingToday = remaining.toDouble()
    }

    // Unlimited/unknown, or this amount still fits under today's remaining cap.
    private fun roomFor(amt: Double): Boolean =
        remainingToday < 0.0 || cumulativeShown + amt <= remainingToday + 0.001

    /**
     * @param callType "audio" or "video"
     * @return true if bonuses are enabled for this call (master + type on and tiers exist)
     */
    fun configure(config: CallBonusConfig?, callType: String): Boolean {
        if (config == null || !isEnabledForType(config, callType)) return false

        val raw = when (callType) {
            "audio" -> config.audio_tiers
            else -> config.video_tiers
        } ?: emptyList()
        // Keep only sane, positive tiers, sorted by minute.
        tiers = raw.filter { it.min > 0 && it.amt > 0 }.sortedBy { it.min }

        leadSeconds = (config.teaser_lead_seconds ?: 60).coerceIn(5, 600)
        enabled = true
        return true
    }

    /**
     * Suppress the T+10s intro toast for this leg. Used when re-anchoring after an
     * audio↔video switch — the intro already played on the first leg, so replaying
     * it on the new leg would just be noise. Teasers/payouts still fire normally.
     */
    fun skipIntro() {
        introShown = true
    }

    /** Call once per second with elapsed seconds since the peer joined (T+0). */
    fun onTick(elapsedSeconds: Long) {
        if (!enabled) return

        // Suppress the intro when the creator has no bonus room left today — no point
        // saying "bonuses coming up" if the cap is already reached.
        if (!introShown && elapsedSeconds >= INTRO_AT_SECONDS && remainingToday != 0.0) {
            introShown = true
            onIntro()
        }

        for (tier in tiers) {
            val milestoneSec = tier.min.toLong() * 60L
            val teaserAtSec = milestoneSec - leadSeconds

            // Teaser: only within its window [teaserAt, milestone), AND not while a
            // just-fired payout still owns the screen. If we joined past the milestone
            // already, skip the teaser entirely (no misleading "coming up"). If the hold
            // pushes past the milestone, the payout below fires instead and the teaser is
            // dropped — better a missing heads-up than one buried under the celebration.
            if (!teaserShown.contains(tier.min) &&
                elapsedSeconds >= teaserAtSec && elapsedSeconds < milestoneSec &&
                elapsedSeconds >= teaserHoldUntilSec &&
                roomFor(tier.amt)   // don't tease a bonus the daily cap won't allow
            ) {
                teaserShown.add(tier.min)
                onTeaser(tier)
            }

            // Payout: at/after the milestone, once — but only if it still fits the
            // creator's remaining daily cap (else the server credits nothing and the
            // popup would be a lie). Mark it shown either way so it never re-evaluates.
            if (!payoutShown.contains(tier.min) && elapsedSeconds >= milestoneSec) {
                payoutShown.add(tier.min)
                teaserShown.add(tier.min)
                if (roomFor(tier.amt)) {
                    cumulativeShown += tier.amt
                    // Hold any not-yet-shown teaser until the payout celebration clears.
                    teaserHoldUntilSec = elapsedSeconds + POST_PAYOUT_HOLD_SECONDS
                    onPayout(tier)
                }
            }
        }
    }

    companion object {
        private const val INTRO_AT_SECONDS = 10L

        // How long a teaser waits after a payout fires. Must exceed the payout card's
        // on-screen time (CallBonusViews.PAYOUT_MS = 3s) so the teaser lands on a clear
        // screen right after "you earned ₹X", reading as "…now stay for the next one".
        private const val POST_PAYOUT_HOLD_SECONDS = 4L

        /**
         * Single source of truth for "does the Call Duration Bonus apply to THIS call
         * type": master switch on, the per-type (audio/video) switch on, and at least one
         * usable tier. Used by [configure] for the in-call popups AND by the call-end
         * routing that decides whether to show the B1 earnings/bonus sheet, so the two can
         * never disagree (previously the sheet was master-only, so it still popped on an
         * audio call even when audio bonus was switched off).
         */
        fun isEnabledForType(config: CallBonusConfig?, callType: String): Boolean {
            if (config == null || (config.master ?: 0) != 1) return false
            val typeOn = when (callType) {
                "audio" -> (config.audio_enabled ?: 0) == 1
                "video" -> (config.video_enabled ?: 0) == 1
                else -> false
            }
            if (!typeOn) return false
            val raw = when (callType) {
                "audio" -> config.audio_tiers
                else -> config.video_tiers
            } ?: emptyList()
            return raw.any { it.min > 0 && it.amt > 0 }
        }
    }
}
