package com.gmwapp.hima.verification

import retrofit2.Response

class PanRepository {
    suspend fun verifyPan(pan: String, name: String): Response<PanResponse> {
        val request = PanRequest(pan, name)
        return CashfreeRetrofitClient.apiService.verifyPan(request)
    }
}

