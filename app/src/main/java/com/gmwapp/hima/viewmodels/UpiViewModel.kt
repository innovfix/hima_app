package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.BankRepositories
import com.gmwapp.hima.repositories.EarningsRepositories
import com.gmwapp.hima.repositories.TransactionsRepositories
import com.gmwapp.hima.repositories.UpiRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AddPointsResponse
import com.gmwapp.hima.retrofit.responses.BankUpdateResponse
import com.gmwapp.hima.retrofit.responses.EarningsResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import com.gmwapp.hima.retrofit.responses.UpiUpdateResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject




@HiltViewModel
class UpiViewModel @Inject constructor(private val upiRepositories: UpiRepositories) : ViewModel() {

    val upiResponseLiveData = MutableLiveData<UpiUpdateResponse>()
    val upiErrorLiveData = MutableLiveData<String>()
    val addpointResponseLiveData = MutableLiveData<AddPointsResponse>()
    val addpointErrorLiveData = MutableLiveData<String>()


    fun updatedUpi(
        userId: Int,
        upiId: String,

    ) {
        viewModelScope.launch {
            upiRepositories.updateUpi(userId,upiId, object : NetworkCallback<UpiUpdateResponse> {
                override fun onResponse(
                    call: Call<UpiUpdateResponse>,
                    response: Response<UpiUpdateResponse>
                ) {
                    upiResponseLiveData.postValue(response.body())
                    Log.d("upiResponseLiveData","${response.body()}")
                }

                override fun onFailure(call: Call<UpiUpdateResponse>, t: Throwable) {
                    upiErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    upiErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }


    fun addPoints(
        buyerName: String, amount: String,
        email: String, phone: String, purpose: String
    ) {
        viewModelScope.launch {
            upiRepositories.addPoints(buyerName, amount, email, phone, purpose, object : NetworkCallback<AddPointsResponse> {
                override fun onResponse(
                    call: Call<AddPointsResponse>,
                    response: Response<AddPointsResponse>
                ) {
                    addpointResponseLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<AddPointsResponse>, t: Throwable) {
                    addpointErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    addpointErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }


//    fun addPoint(
//        buyerName: String, amount: String,
//        email: String, phone: String, purpose: String
//    ) {
//        viewModelScope.launch {
//            isLoading.postValue(true)
//            chatRepositories.addPoints(buyerName, amount, email, phone, purpose).let {
//                if (it.body() != null) {
//                    addPointsLiveData.postValue(it.body() as AddPointsResponse)
//                    isLoading.postValue(false)
//                } else {
//                    isLoading.postValue(false)
//                }
//            }
//
//        }
//    }



}

