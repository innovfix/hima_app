package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.WithdrawRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.WithdrawResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject




@HiltViewModel
class WithdrawViewModel @Inject constructor(private val withdrawRepository: WithdrawRepositories) : ViewModel() {

    val withdrawResponseLiveData = MutableLiveData<WithdrawResponse>()
    val withdrawErrorLiveData = MutableLiveData<String>()

    fun addWithdrawal(
        userId: Int,
        amount: Int,
        paymentMethod: String
    ) {
        viewModelScope.launch {
            withdrawRepository.addWithdrawal(userId, amount, paymentMethod, object : NetworkCallback<WithdrawResponse> {
                override fun onResponse(
                    call: Call<WithdrawResponse>,
                    response: Response<WithdrawResponse>
                ) {
                    Log.d("withdrawResponseLiveData","${response.body()}")

                    withdrawResponseLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<WithdrawResponse>, t: Throwable) {
                    Log.d("withdrawResponseLiveData","${t}")

                    withdrawErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    withdrawErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}


