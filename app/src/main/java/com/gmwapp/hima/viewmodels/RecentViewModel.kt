package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.ProfileRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallsListResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@HiltViewModel
class RecentViewModel @Inject constructor(private val profileRepositories: ProfileRepositories) :
    ViewModel() {

    val callsListErrorLiveData = MutableLiveData<String>()
    val callsListLiveData = MutableLiveData<CallsListResponse>()
    fun getCallsList(userId: Int, gender: String, limit: Int, currentOffset: Int) {
        viewModelScope.launch {
            profileRepositories.getCallsList(
                userId,
                gender,
                limit,
                currentOffset,
                object : NetworkCallback<CallsListResponse> {
                    override fun onResponse(
                        call: Call<CallsListResponse>, response: Response<CallsListResponse>
                    ) {
                        callsListLiveData.postValue(response.body())
                        Log.d("callsListLiveData","${response.body()}")
                        Log.d("callsListLiveData","${call.request().url}")
                    }

                    override fun onFailure(call: Call<CallsListResponse>, t: Throwable) {
                        callsListErrorLiveData.postValue(t.message)
                        Log.d("callsListFailed","${t.message}")

                    }

                    override fun onNoNetwork() {
                        callsListErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }


}

