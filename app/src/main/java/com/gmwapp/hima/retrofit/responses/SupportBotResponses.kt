package com.gmwapp.hima.retrofit.responses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A tappable option under a bot message.
 *
 * SERVER-DRIVEN BY DESIGN. The app renders [label] and posts [key] back; it
 * has no idea what the categories are. Sub-categories will churn weekly off
 * the Yes/No feedback data, and a client-side taxonomy would mean a Play
 * Store release per iteration. The precedent to avoid is
 * AiOnboardingActivity.concernTranslations — 11 languages x 6 strings
 * hardcoded in Kotlin, so adding a 12th language is a release.
 */
@Parcelize
data class BotChip(
    val key: String,
    val label: String,
    val hint: String? = null,
    val ghost: Boolean = false
) : Parcelable

/**
 * How the app should let the user respond next. Server decides.
 *  chips  – tap one of [BotChip]
 *  text   – free text input
 *  yesno  – the "Did this solve it?" CTA
 *  none   – terminal (escalated); nothing to do but leave or view the ticket
 */
object BotInputMode {
    const val CHIPS = "chips"
    const val TEXT = "text"
    const val YESNO = "yesno"
    const val NONE = "none"
}

data class SupportBotStartResponse(
    val success: Boolean,
    val code: String? = null,          // bot_disabled | pending_escalated_ticket | daily_limit
    val message: String? = null,
    val ticket_id: Int? = null,
    val session_id: Int? = null,
    val step: Int = 0,
    val ai_message: String? = null,
    val input_mode: String? = null,
    val chips: List<BotChip>? = null,
    /** Spec #11 — set only during a platform-wide incident. */
    val known_issue: String? = null
)

data class SupportBotReplyResponse(
    val success: Boolean,
    /** in_progress = the previous request is still running server-side. */
    val code: String? = null,
    val message: String? = null,
    /** Server replayed an already-finished answer instead of regenerating. */
    val already_answered: Boolean = false,
    val step: Int = 0,
    val ai_message: String? = null,
    val feedback_prompt: String? = null,
    val input_mode: String? = null,
    val chips: List<BotChip>? = null,
    val is_final: Boolean = false,
    val escalated_immediately: Boolean = false,
    val ticket_id: Int? = null,
    /** Spec #8 — offered whenever a ticket has just been raised. Always optional. */
    val can_attach: Boolean = false,
    val attach_prompt: String? = null,
    /** Spec #7 */
    val out_of_hours: String? = null
)

data class SupportBotFeedbackResponse(
    val success: Boolean,
    val resolved: Boolean = false,
    val escalated: Boolean = false,
    val ticket_id: Int? = null,
    val ai_message: String? = null,
    /** Spec #6 — the bot's second, different attempt. No ticket yet. */
    val second_attempt: Boolean = false,
    val feedback_prompt: String? = null,
    val input_mode: String? = null,
    /** Spec #9 — rating is asked only AFTER the user confirms it's resolved. */
    val ask_rating: Boolean = false,
    /** Spec #8 */
    val can_attach: Boolean = false,
    val attach_prompt: String? = null,
    /** Spec #7 — set only when the team is off shift. */
    val out_of_hours: String? = null
)

/** One rendered bubble in a resumed transcript. role = "bot" | "user". */
@Parcelize
data class BotHistoryTurn(
    val role: String,
    val text: String
) : Parcelable

/**
 * Resume (P1-G). Returned by support_bot_session: the full transcript plus the
 * current step's live render, so a reopened session rebuilds exactly where it
 * was left — no taps repeated.
 */
data class SupportBotSessionResponse(
    val success: Boolean,
    val code: String? = null,
    val session_id: Int? = null,
    val step: Int = 0,
    val is_final: Boolean = false,
    val ticket_id: Int? = null,
    val history: List<BotHistoryTurn>? = null,
    val ai_message: String? = null,
    val input_mode: String? = null,
    val chips: List<BotChip>? = null,
    val feedback_prompt: String? = null
)

data class SupportBotAttachResponse(
    val success: Boolean,
    val code: String? = null,
    val count: Int = 0,
    val ai_message: String? = null
)
