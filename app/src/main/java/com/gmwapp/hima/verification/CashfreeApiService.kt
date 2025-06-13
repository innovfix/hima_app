package com.gmwapp.hima.verification

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface CashfreeApiService {
    @Headers(
        "Content-Type: application/json",
        "x-client-id: CF10666599D157Q7U76C7C73BOAF4G",
        "x-client-secret: cfsk_ma_test_40c029f442f352110cb1c3f85aebf06e_085bf549"
    )
    @POST("verification/pan")
    suspend fun verifyPan(@Body request: PanRequest): Response<PanResponse>
}