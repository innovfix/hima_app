package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallStatusRequest
import com.gmwapp.hima.retrofit.responses.CallStatusResponse
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

class CallStatusRepository @Inject constructor(
    private val apiManager: ApiManager
) {
    fun callStatus(
        request: CallStatusRequest,
        callback: NetworkCallback<CallStatusResponse>
    ) {
        // A call-status record (rejected / not-answered / ended) just landed
        // server-side. Flag the Recent/Favourite lists to reload on their next
        // onResume — this is the centralised counterpart to the completed-call
        // refresh in CallUpdateWorker, and it covers decline / reject / timeout
        // for BOTH genders in one place (previously only the female peer-reject
        // path set this flag, so a male caller's missed/rejected call didn't
        // refresh the Recent page immediately).
        apiManager.callStatus(request, object : NetworkCallback<CallStatusResponse> {
            override fun onResponse(
                call: Call<CallStatusResponse>,
                response: Response<CallStatusResponse>
            ) {
                if (response.isSuccessful) {
                    com.gmwapp.hima.agora.FcmUtils.shouldRefreshCallList = 1
                }
                callback.onResponse(call, response)
            }

            override fun onFailure(call: Call<CallStatusResponse>, t: Throwable) {
                callback.onFailure(call, t)
            }

            override fun onNoNetwork() {
                callback.onNoNetwork()
            }
        })
    }
}
