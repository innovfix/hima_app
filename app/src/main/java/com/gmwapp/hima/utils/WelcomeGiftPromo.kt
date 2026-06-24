package com.gmwapp.hima.utils

import android.content.Context
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.constants.DConstants

/**
 * Single source of truth for whether the welcome-gift (₹1 trial) banner may be
 * shown. The banner is a payment upsell, so it is restricted to:
 *
 *  - MALE users only            — subscribe/autopay CTAs are male-only (females
 *                                 are recipients, not payers).
 *  - autopay-enabled languages  — admin per-language flag == "autopay".
 *  - confirmed subscription      — only act once we actually have the user's
 *    state (isPopulated)           subscription truth, never on the cold-start
 *                                 fail-closed default.
 *  - never-active users          — a user who has EVER had an autopay mandate
 *    (!everActive)                (currently active OR cancelled/lapsed) must
 *                                 not see the ₹1 welcome banner again.
 *
 * Once the user subscribes (isActive) or cancels (everActive), everActive stays
 * true and the banner never reappears — they fall through to the normal
 * app/wallet flow.
 */
object WelcomeGiftPromo {

    fun isEligible(context: Context): Boolean {
        val isMale = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender == DConstants.MALE
        return isMale &&
            LanguageFeatureCache.isAutopayEnabled(context) &&
            SubscriptionStateCache.isPopulated() &&
            !SubscriptionStateCache.everActive(context)
    }
}
