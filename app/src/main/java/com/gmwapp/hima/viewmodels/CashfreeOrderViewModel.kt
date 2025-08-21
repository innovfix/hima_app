package com.gmwapp.hima.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.CashfreeOrderRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CreateCashfreeOrderResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CashfreeOrderViewModel @Inject constructor(
    private val repository: CashfreeOrderRepository
) : ViewModel() {

    val orderLiveData = MutableLiveData<CreateCashfreeOrderResponse>()
    val orderErrorLiveData = MutableLiveData<String>()

    fun createOrder(userId: Int, coinsId: Int) {
        viewModelScope.launch {
            repository.createOrder(userId, coinsId, object :
                NetworkCallback<CreateCashfreeOrderResponse> {
                override fun onResponse(
                    call: Call<CreateCashfreeOrderResponse>,
                    response: Response<CreateCashfreeOrderResponse>
                ) {
                    orderLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<CreateCashfreeOrderResponse>, t: Throwable) {
                    orderErrorLiveData.postValue("Something went wrong: ${t.message}")
                }

                override fun onNoNetwork() {
                    orderErrorLiveData.postValue("No internet connection")
                }
            })
        }
    }
}
