package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.CreatorWarningsRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CreatorWarningsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class CreatorWarningsViewModel @Inject constructor(
    private val repository: CreatorWarningsRepository
) : ViewModel() {

    val warningsLiveData = MutableLiveData<CreatorWarningsResponse>()
    val warningsErrorLiveData = MutableLiveData<String>()

    fun getCreatorWarnings(userId: Int) {
        viewModelScope.launch {
            Log.d("CreatorWarningsAPI", "===== FETCHING CREATOR WARNINGS =====")
            Log.d("CreatorWarningsAPI", "User ID: $userId")
            
            repository.getCreatorWarnings(userId, object : NetworkCallback<CreatorWarningsResponse> {
                override fun onResponse(
                    call: Call<CreatorWarningsResponse>,
                    response: Response<CreatorWarningsResponse>
                ) {
                    Log.d("CreatorWarningsAPI", "===== CREATOR WARNINGS RESPONSE =====")
                    Log.d("CreatorWarningsAPI", "HTTP Status: ${response.code()}")
                    Log.d("CreatorWarningsAPI", "Is Successful: ${response.isSuccessful}")
                    Log.d("CreatorWarningsAPI", "Response Body: ${response.body()}")
                    
                    if (response.isSuccessful) {
                        warningsLiveData.postValue(response.body())
                        Log.d("CreatorWarningsAPI", "✅ Warnings fetched successfully")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("CreatorWarningsAPI", "Error Body: $errorBody")
                        try {
                            val gson = com.google.gson.Gson()
                            val errorResponse = gson.fromJson(errorBody, CreatorWarningsResponse::class.java)
                            val errorMessage = errorResponse.message ?: "Failed to fetch warnings"
                            warningsErrorLiveData.postValue(errorMessage)
                        } catch (e: Exception) {
                            warningsErrorLiveData.postValue("Failed to fetch warnings")
                        }
                    }
                    Log.d("CreatorWarningsAPI", "======================================")
                }

                override fun onFailure(call: Call<CreatorWarningsResponse>, t: Throwable) {
                    Log.e("CreatorWarningsAPI", "===== CREATOR WARNINGS FAILURE =====")
                    Log.e("CreatorWarningsAPI", "Error: ${t.message}", t)
                    Log.e("CreatorWarningsAPI", "======================================")
                    warningsErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    Log.w("CreatorWarningsAPI", "No network connection")
                    warningsErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}
