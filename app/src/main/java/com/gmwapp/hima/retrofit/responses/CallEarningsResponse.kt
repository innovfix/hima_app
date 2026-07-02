package com.gmwapp.hima.retrofit.responses

/**
 * Post-call earnings for the creator, read straight from the server's persisted,
 * already-credited values (call_income + call_duration_bonus for this call_id).
 *
 * MONEY-SAFETY: this is display-only truth. The app NEVER computes these numbers
 * from its own timer — it echoes what the server actually credited. If the call
 * hasn't settled yet, [settled] is false and the amounts are null → the B1 screen
 * shows a "finalizing" state instead of a fabricated number.
 */
data class CallEarningsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: CallEarnings? = null
)

data class CallEarnings(
    // true once the call has been settled and the values below are authoritative
    val settled: Boolean = false,
    // rupees credited for this call (base per-minute income)
    val call_income: Double? = null,
    // rupees credited as duration bonus (0 if none / feature off)
    val bonus: Double? = null,
    // total added to balance for this call = call_income + bonus
    val total: Double? = null,
    // creator wallet balance BEFORE and AFTER this call's credit (for the jump)
    val balance_before: Double? = null,
    val balance_after: Double? = null
)
