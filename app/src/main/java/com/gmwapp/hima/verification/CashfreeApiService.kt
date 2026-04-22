package com.gmwapp.hima.verification

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface CashfreeApiService {
    @Headers("Content-Type: application/json")
    @POST("verification/pan")
    suspend fun verifyPan(@Body request: PanRequest): Response<PanResponse>
}
