package com.gmwapp.hima.utils

import android.os.Handler
import android.os.Looper
import android.view.View
import com.gmwapp.hima.databinding.OverlayCallBonusBinding
import com.gmwapp.hima.retrofit.responses.BonusTier
import kotlin.math.ceil

/**
 * F1 Call Duration Bonus — view layer for the intro / teaser / payout popups.
 * Shared by both calling activities so the show/auto-hide behaviour is identical.
 * DISPLAY ONLY — shows the amounts from admin config; the server credits the money.
 */
object CallBonusViews {

    private const val INTRO_MS = 4000L
    private const val TEASER_MS = 5000L
    private const val PAYOUT_MS = 3000L

    private val handler = Handler(Looper.getMainLooper())

    /** Format a rupee amount without a trailing .0 (₹18, ₹18.5). */
    private fun rupee(amt: Double): String {
        return if (amt == amt.toLong().toDouble()) "₹${amt.toLong()}" else "₹%.2f".format(amt)
    }

    fun showIntro(overlay: OverlayCallBonusBinding) {
        handler.removeCallbacks(hideIntro)
        pendingIntroOverlay = overlay
        overlay.bonusIntro.visibility = View.VISIBLE
        handler.postDelayed(hideIntro, INTRO_MS)
    }

    fun showTeaser(overlay: OverlayCallBonusBinding, tier: BonusTier, leadSeconds: Int) {
        val minutesLeft = ceil(leadSeconds / 60.0).toInt().coerceAtLeast(1)
        overlay.tvBonusTeaser.text = overlay.root.context.getString(
            com.gmwapp.hima.R.string.bonus_teaser_text, minutesLeft, rupee(tier.amt)
        )
        handler.removeCallbacks(hideTeaser)
        pendingTeaserOverlay = overlay
        overlay.bonusTeaser.visibility = View.VISIBLE
        handler.postDelayed(hideTeaser, TEASER_MS)
    }

    fun showPayout(overlay: OverlayCallBonusBinding, tier: BonusTier) {
        overlay.tvBonusPayoutAmt.text = overlay.root.context.getString(
            com.gmwapp.hima.R.string.bonus_payout_amount, rupee(tier.amt)
        )
        overlay.bonusScrim.visibility = View.VISIBLE
        overlay.bonusPayout.visibility = View.VISIBLE
        overlay.bonusPayout.setOnClickListener { hidePayoutNow(overlay) } // tap to dismiss early
        handler.removeCallbacks(hidePayout)
        // Bind the runnable to this overlay instance for hide.
        pendingPayoutOverlay = overlay
        handler.postDelayed(hidePayout, PAYOUT_MS)
    }

    /** Hide everything immediately (call on teardown to avoid leaking popups). */
    fun clear(overlay: OverlayCallBonusBinding) {
        handler.removeCallbacksAndMessages(null)
        overlay.bonusIntro.visibility = View.GONE
        overlay.bonusTeaser.visibility = View.GONE
        overlay.bonusPayout.visibility = View.GONE
        overlay.bonusScrim.visibility = View.GONE
        pendingIntroOverlay = null
        pendingTeaserOverlay = null
        pendingPayoutOverlay = null
    }

    private fun hidePayoutNow(overlay: OverlayCallBonusBinding) {
        handler.removeCallbacks(hidePayout)
        overlay.bonusPayout.visibility = View.GONE
        overlay.bonusScrim.visibility = View.GONE
    }

    // Overlay references for the delayed hides (one call screen at a time).
    private var pendingIntroOverlay: OverlayCallBonusBinding? = null
    private var pendingTeaserOverlay: OverlayCallBonusBinding? = null
    private var pendingPayoutOverlay: OverlayCallBonusBinding? = null

    private val hideIntro = Runnable { pendingIntroOverlay?.bonusIntro?.visibility = View.GONE }
    private val hideTeaser = Runnable { pendingTeaserOverlay?.bonusTeaser?.visibility = View.GONE }
    private val hidePayout = Runnable {
        pendingPayoutOverlay?.let {
            it.bonusPayout.visibility = View.GONE
            it.bonusScrim.visibility = View.GONE
        }
    }
}
