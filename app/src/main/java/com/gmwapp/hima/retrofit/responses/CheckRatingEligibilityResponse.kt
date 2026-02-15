package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class CheckRatingEligibilityResponse(
    val success: Boolean,
    val eligible: Boolean,
    val message: String
)
