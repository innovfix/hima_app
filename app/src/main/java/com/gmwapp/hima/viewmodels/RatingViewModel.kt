package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.BankRepositories
import com.gmwapp.hima.repositories.EarningsRepositories
import com.gmwapp.hima.repositories.RatingRepositories
import com.gmwapp.hima.repositories.TransactionsRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BankUpdateResponse
import com.gmwapp.hima.retrofit.responses.EarningsResponse
import com.gmwapp.hima.retrofit.responses.RatingResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import com.gmwapp.hima.retrofit.responses.UserCallDurationResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject




@HiltViewModel

class RatingViewModel @Inject constructor(private val ratingRepositories: RatingRepositories) : ViewModel() {

    val ratingResponseLiveData = MutableLiveData<RatingResponse>()
    val ratingErrorLiveData = MutableLiveData<String>()
    val userCallDurationLiveData = MutableLiveData<UserCallDurationResponse>()
    val userCallDurationErrorLiveData = MutableLiveData<String>()

    fun updatedrating(
        userId: Int,
        call_user_id: Int,
        ratings: String,
        title: String,
        description: String
    ) {
        viewModelScope.launch {
            ratingRepositories.updaterating(userId, call_user_id, ratings, title, description, object : NetworkCallback<RatingResponse> {
                override fun onResponse(
                    call: Call<RatingResponse>,
                    response: Response<RatingResponse>
                ) {
                    ratingResponseLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<RatingResponse>, t: Throwable) {
                    ratingErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    ratingErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }

    fun getUserCallDuration(callId: Int) {
        viewModelScope.launch {
            ratingRepositories.getUserCallDuration(callId, object : NetworkCallback<UserCallDurationResponse> {
                override fun onResponse(
                    call: Call<UserCallDurationResponse>,
                    response: Response<UserCallDurationResponse>
                ) {
                    userCallDurationLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<UserCallDurationResponse>, t: Throwable) {
                    userCallDurationErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    userCallDurationErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}

