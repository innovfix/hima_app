package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.FemaleTransactionsRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FemaleTransactionsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class FemaleTransactionsViewModel @Inject constructor(
    private val femaleTransactionsRepositories: FemaleTransactionsRepositories
) : ViewModel() {

    companion object {
        private const val TAG = "femaleTrasactionLog"
    }

    val transactionsResponseLiveData = MutableLiveData<FemaleTransactionsResponse>()
    val transactionsErrorLiveData = MutableLiveData<String>()

    fun getFemaleTransactions(userId: Int, offset: Int, limit: Int, type: String? = null) {
        Log.d(TAG, "getFemaleTransactions: Called with userId=$userId, offset=$offset, limit=$limit, type=$type")
        Log.d(TAG, "getFemaleTransactions: API endpoint = female_transaction")

        viewModelScope.launch {
            try {
                Log.d(TAG, "getFemaleTransactions: Calling repository...")
                femaleTransactionsRepositories.getFemaleTransactions(
                    userId,
                    offset,
                    limit,
                    type,
                    object : NetworkCallback<FemaleTransactionsResponse> {
                        override fun onResponse(
                            call: Call<FemaleTransactionsResponse>,
                            response: Response<FemaleTransactionsResponse>
                        ) {
                            Log.d(TAG, "onResponse: HTTP response code = ${response.code()}")
                            Log.d(TAG, "onResponse: Response isSuccessful = ${response.isSuccessful}")
                            
                            if (response.isSuccessful) {
                                val responseBody = response.body()
                                Log.d(TAG, "onResponse: Response body = $responseBody")
                                
                                if (responseBody != null) {
                                    Log.d(TAG, "onResponse: success = ${responseBody.success}")
                                    Log.d(TAG, "onResponse: message = ${responseBody.message}")
                                    Log.d(TAG, "onResponse: data count = ${responseBody.data?.size ?: 0}")
                                    
                                    transactionsResponseLiveData.postValue(responseBody)
                                } else {
                                    Log.e(TAG, "onResponse: ERROR - Response body is null!")
                                    transactionsErrorLiveData.postValue("Response body is null")
                                }
                            } else {
                                Log.e(TAG, "onResponse: ERROR - HTTP error code = ${response.code()}")
                                Log.e(TAG, "onResponse: Error body = ${response.errorBody()?.string()}")
                                transactionsErrorLiveData.postValue("HTTP Error: ${response.code()}")
                            }
                        }

                        override fun onFailure(call: Call<FemaleTransactionsResponse>, t: Throwable) {
                            Log.e(TAG, "onFailure: API call failed", t)
                            Log.e(TAG, "onFailure: Error message = ${t.message}")
                            Log.e(TAG, "onFailure: Error type = ${t.javaClass.simpleName}")
                            t.printStackTrace()
                            transactionsErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                        }

                        override fun onNoNetwork() {
                            Log.e(TAG, "onNoNetwork: No network connection available")
                            transactionsErrorLiveData.postValue(DConstants.NO_NETWORK)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "getFemaleTransactions: Exception occurred", e)
                Log.e(TAG, "getFemaleTransactions: Exception message = ${e.message}")
                e.printStackTrace()
                transactionsErrorLiveData.postValue("Exception: ${e.message}")
            }
        }
    }
}

