package com.gmwapp.hima.viewmodels


import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.TransactionChargesRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TransactionChargesResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class TransactionChargesViewModel @Inject constructor(
    private val repository: TransactionChargesRepository
) : ViewModel() {

    val chargesResponseLiveData = MutableLiveData<TransactionChargesResponse>()
    val chargesErrorLiveData = MutableLiveData<String>()

    fun getTransactionCharges() {
        viewModelScope.launch {
            repository.getTransactionCharges(object : NetworkCallback<TransactionChargesResponse> {
                override fun onResponse(
                    call: Call<TransactionChargesResponse>,
                    response: Response<TransactionChargesResponse>
                ) {
                    chargesResponseLiveData.postValue(response.body())
                    Log.d("TransactionChargesVM", "${response.body()}")
                }

                override fun onFailure(call: Call<TransactionChargesResponse>, t: Throwable) {
                    chargesErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    chargesErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}
