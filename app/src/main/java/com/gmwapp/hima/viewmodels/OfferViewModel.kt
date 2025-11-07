package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.BankRepositories
import com.gmwapp.hima.repositories.EarningsRepositories
import com.gmwapp.hima.repositories.OfferRepositories
import com.gmwapp.hima.repositories.TransactionsRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BankUpdateResponse
import com.gmwapp.hima.retrofit.responses.EarningsResponse
import com.gmwapp.hima.retrofit.responses.OfferResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject




@HiltViewModel
class OfferViewModel @Inject constructor(private val offerRepositories: OfferRepositories) : ViewModel() {

    val offerResponseLiveData = MutableLiveData<OfferResponse>()
    val offerrrorLiveData = MutableLiveData<String>()

    fun getOffer(
        userId: Int,
    ) {
        viewModelScope.launch {
            offerRepositories.getoffer(userId,  object : NetworkCallback<OfferResponse> {
                override fun onResponse(
                    call: Call<OfferResponse>,
                    response: Response<OfferResponse>
                ) {
                    offerResponseLiveData.postValue(response.body())
                    Log.d("offerResponseLiveData", "${response.body()}")


                }

                override fun onFailure(call: Call<OfferResponse>, t: Throwable) {
                    offerrrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    Log.d("offerResponseLiveData", "${t.message}")

                }

                override fun onNoNetwork() {
                    offerrrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}

