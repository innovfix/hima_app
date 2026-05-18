package com.gmwapp.hima.retrofit.responses

data class GetRemainingTimeResponse(
    val success: Boolean,
    val message: String,
    val data: GetRemainingTimeData?,
)

data class GetRemainingTimeData(
    val remaining_time: String,
    // B141: server epoch millis and absolute call-end epoch millis. Both
    // clients (male + female) compute `display_remaining = ends_at_ms -
    // System.currentTimeMillis()` and tick a local countdown against a
    // shared anchor, eliminating the 1-2 s drift caused by each side
    // starting CountDownTimer at different local moments. Null-safe for
    // backends that haven't deployed the v2 response yet.
    val server_now_ms: Long? = null,
    val ends_at_ms: Long? = null,
)
