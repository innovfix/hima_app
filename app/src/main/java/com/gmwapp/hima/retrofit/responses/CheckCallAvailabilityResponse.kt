package com.gmwapp.hima.retrofit.responses

data class CheckCallAvailabilityResponse(
    val success: Boolean,
    val message: String,
    val data: CallAvailabilityData?
)

data class CallAvailabilityData(
    val male_user_id: Int,
    val female_user_id: Int,
    val is_blocked: Boolean,
    // FEMALE_3_REJECT_BLOCK — true when this female auto-blocked this male by
    // rejecting 3x in 5 min (60-min cooldown). Separate from is_blocked (mutual
    // user-block, different message). Old backend omits it → defaults false.
    val call_blocked: Boolean = false,
    // B_003 — DND state of each party. The caller's app greys its call buttons when
    // the callee (the peer being viewed) is on DND. Old backend omits these → default
    // false → no behavior change.
    val male_dnd: Boolean = false,
    val female_dnd: Boolean = false,
    val audio_status: Int,
    val video_status: Int
)
