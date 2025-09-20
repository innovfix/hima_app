package com.gmwapp.hima.viewmodels


import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.UPIWithdrawRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UpiWithdrawResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class UPIWithdrawViewModel @Inject constructor(
    private val upiWithdrawRepository: UPIWithdrawRepositories
) : ViewModel() {

    val upiWithdrawResponseLiveData = MutableLiveData<UpiWithdrawResponse>()
    val upiWithdrawErrorLiveData = MutableLiveData<String>()

    fun addWithdrawal(
        userId: Int,
        amount: Int,
        paymentMethod: String
    ) {
        viewModelScope.launch {
            upiWithdrawRepository.addWithdrawal(
                userId,
                amount,
                paymentMethod,
                object : NetworkCallback<UpiWithdrawResponse> {

                    override fun onResponse(
                        call: Call<UpiWithdrawResponse>,
                        response: Response<UpiWithdrawResponse>
                    ) {

                        upiWithdrawResponseLiveData.postValue(response.body())
                        Log.d("upiWithdrawResponseLiveData","${response.body()}")

                    }

                    override fun onFailure(call: Call<UpiWithdrawResponse>, t: Throwable) {
                        upiWithdrawErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    }

                    override fun onNoNetwork() {
                        upiWithdrawErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                }
            )
        }
    }
}
