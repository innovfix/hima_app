package com.gmwapp.hima.retrofit.responses

data class ReportsResponse(
    val success: Boolean,
    val message: String,
    val data: List<ReportData>
)

data class ReportData(
    val user_name: String,
    val today_calls: Int,
    val today_earnings: String,
    // Split-earnings: today's gift income, separate from today_earnings (call income).
    // Old backend omits it → null → app shows ₹0.
    val today_gift_earnings: String? = null,
    val first_call: Int,
    val call_rates: String?,
    // BUG 15 — structured replacement for the `call_rates` POSTER IMAGE.
    //
    // call_rates is a hardcoded PNG URL in AuthController (served from the TEST domain
    // for every creator in production), so changing a rate means redrawing a picture and
    // dropping it on a server — and a hand-drawn table can silently disagree with what
    // the backend actually pays.
    //
    // This carries the same content as DATA so the app can draw it natively and admin
    // can edit it. Nullable with a null default on purpose, in both directions:
    //   * older backend omits it   -> null -> the app keeps rendering the image, exactly
    //                                 as before. Nothing changes for anyone.
    //   * older APP receives it    -> Gson drops unknown fields -> old builds keep using
    //                                 call_rates untouched.
    // So the backend can start sending this to everyone with no forced update. Same
    // additive trick as today_gift_earnings above.
    val earning_details: EarningDetails? = null
)

/** One "Earning details" card: a heading, N rate tables, and an optional footer note. */
data class EarningDetails(
    val title: String? = null,
    val sections: List<EarningSection>? = null,
    val note: String? = null
)

/** One rate table, e.g. "Audio call". */
data class EarningSection(
    val label: String? = null,
    val rows: List<EarningRow>? = null
)

/**
 * One row of a rate table. Both sides are PRE-FORMATTED strings so the backend owns the
 * wording, the currency symbol and any rounding — the app never does rate maths and can
 * therefore never disagree with what is actually paid.
 */
data class EarningRow(
    val time: String? = null,
    val rate: String? = null
)
