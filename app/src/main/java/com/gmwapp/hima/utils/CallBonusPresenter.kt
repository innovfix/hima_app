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

    /**
     * @param callType "audio" or "video"
     * @return true if bonuses are enabled for this call (master + type on and tiers exist)
     */
    fun configure(config: CallBonusConfig?, callType: String): Boolean {
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
        // Keep only sane, positive tiers, sorted by minute.
        tiers = raw.filter { it.min > 0 && it.amt > 0 }.sortedBy { it.min }
        if (tiers.isEmpty()) return false

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

        if (!introShown && elapsedSeconds >= INTRO_AT_SECONDS) {
            introShown = true
            onIntro()
        }

        for (tier in tiers) {
            val milestoneSec = tier.min.toLong() * 60L
            val teaserAtSec = milestoneSec - leadSeconds

            // Teaser: only within its window [teaserAt, milestone). If we joined past the
            // milestone already, skip the teaser entirely (no misleading "coming up").
            if (!teaserShown.contains(tier.min) &&
                elapsedSeconds >= teaserAtSec && elapsedSeconds < milestoneSec
            ) {
                teaserShown.add(tier.min)
                onTeaser(tier)
            }

            // Payout: at/after the milestone, once.
            if (!payoutShown.contains(tier.min) && elapsedSeconds >= milestoneSec) {
                payoutShown.add(tier.min)
                // Ensure a teaser for this tier never fires afterwards.
                teaserShown.add(tier.min)
                onPayout(tier)
            }
        }
    }

    companion object {
        private const val INTRO_AT_SECONDS = 10L
    }
}
