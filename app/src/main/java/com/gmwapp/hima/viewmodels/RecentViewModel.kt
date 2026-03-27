package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.ProfileRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallsListResponse
import com.gmwapp.hima.retrofit.responses.MissedCallCountResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject


@HiltViewModel
class RecentViewModel @Inject constructor(private val profileRepositories: ProfileRepositories) :
    ViewModel() {

    val callsListErrorLiveData = MutableLiveData<String>()
    val callsListLiveData = MutableLiveData<CallsListResponse>()
    val missedCallCountLiveData = MutableLiveData<Int>()
    val missedCallCountErrorLiveData = MutableLiveData<String>()

    /**
     * Bumps only on full reload ([currentOffset] == 0). Pagination reuses the current id so
     * in-flight pages stay valid, but a new reload invalidates older responses (tab / swipe spam).
     */
    private val callsListRequestId = AtomicInteger(0)

    fun getCallsList(userId: Int, gender: String, limit: Int, currentOffset: Int, type: String, search: String? = null, fav: Int? = null) {
        val requestId = if (currentOffset == 0) {
            callsListRequestId.incrementAndGet()
        } else {
            callsListRequestId.get()
        }
        viewModelScope.launch {
            profileRepositories.getCallsList(
                userId,
                gender,
                limit,
                currentOffset,
                type,
                search,
                fav,
                object : NetworkCallback<CallsListResponse> {
                    override fun onResponse(
                        call: Call<CallsListResponse>, response: Response<CallsListResponse>
                    ) {
                        if (requestId != callsListRequestId.get()) {
                            Log.d("RecentViewModel", "Ignoring stale calls list response (id=$requestId, latest=${callsListRequestId.get()}, offset=$currentOffset)")
                            return
                        }
                        callsListLiveData.postValue(response.body())
                        Log.d("callsListLiveData", "${response.body()}")
                    }

                    override fun onFailure(call: Call<CallsListResponse>, t: Throwable) {
                        if (requestId != callsListRequestId.get()) {
                            Log.d("RecentViewModel", "Ignoring stale calls list failure (id=$requestId)")
                            return
                        }
                        callsListErrorLiveData.postValue(t.message)
                        Log.d("callsListFailed", "${t.message}")
                    }

                    override fun onNoNetwork() {
                        if (requestId != callsListRequestId.get()) {
                            Log.d("RecentViewModel", "Ignoring stale calls list no-network (id=$requestId)")
                            return
                        }
                        callsListErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun getMissedCallCount(userId: Int, seen: Int = 0) {
        viewModelScope.launch {
            Log.d("missed_call_data", "Calling missed_call_count API for userId=$userId, seen=$seen")
            profileRepositories.getMissedCallCount(userId, seen, object : NetworkCallback<MissedCallCountResponse> {
                override fun onResponse(
                    call: Call<MissedCallCountResponse>,
                    response: Response<MissedCallCountResponse>
                ) {
                    Log.d(
                        "missed_call_data",
                        "response code=${response.code()}, seen=$seen, body=${response.body()}"
                    )
                    val count = if (response.isSuccessful && response.body()?.success == true) {
                        response.body()?.data?.missed_call_count ?: 0
                    } else {
                        0
                    }
                    Log.d("missed_call_data", "parsed_count=$count")
                    missedCallCountLiveData.postValue(count)
                }

                override fun onFailure(call: Call<MissedCallCountResponse>, t: Throwable) {
                    Log.e("missed_call_data", "api_failed=${t.message}", t)
                    missedCallCountErrorLiveData.postValue(t.message ?: "Unknown error")
                    missedCallCountLiveData.postValue(0)
                }

                override fun onNoNetwork() {
                    Log.e("missed_call_data", "no_network")
                    missedCallCountErrorLiveData.postValue(DConstants.NO_NETWORK)
                    missedCallCountLiveData.postValue(0)
                }
            })
        }
    }


}

