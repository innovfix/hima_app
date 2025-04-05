package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.TransactionsRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import kotlin.math.log


@HiltViewModel
class TransactionsViewModel @Inject constructor(private val transactionsRepositories: TransactionsRepositories) : ViewModel() {

    val transactionsResponseLiveData = MutableLiveData<TransactionsResponse>()
    val transactionsErrorLiveData = MutableLiveData<String>()

    fun getTransactions(userId: Int,  offset: Int,limit: Int,) {
        viewModelScope.launch {
            transactionsRepositories.getTransactions(userId,offset,limit, object:NetworkCallback<TransactionsResponse> {
                override fun onResponse(
                    call: Call<TransactionsResponse>,
                    response: Response<TransactionsResponse>
                ) {
                    transactionsResponseLiveData.postValue(response.body());
                    Log.d("transactionsResponseLiveData","${response.body()}")
                }

                override fun onFailure(call: Call<TransactionsResponse>, t: Throwable) {
                    transactionsErrorLiveData.postValue(DConstants.LOGIN_ERROR);
                }

                override fun onNoNetwork() {
                    transactionsErrorLiveData.postValue(DConstants.NO_NETWORK);
                }
            })
        }
    }

}

