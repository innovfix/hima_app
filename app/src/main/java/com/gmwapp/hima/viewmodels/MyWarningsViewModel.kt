package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.MyWarningsRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.MyWarningsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class MyWarningsViewModel @Inject constructor(
    private val repository: MyWarningsRepository
) : ViewModel() {

    val myWarningsLiveData = MutableLiveData<MyWarningsResponse>()
    val myWarningsErrorLiveData = MutableLiveData<String>()

    fun getMyWarnings(userId: Int) {
        viewModelScope.launch {
            repository.getMyWarnings(userId, object : NetworkCallback<MyWarningsResponse> {
                override fun onResponse(
                    call: Call<MyWarningsResponse>,
                    response: Response<MyWarningsResponse>
                ) {
                    if (response.isSuccessful) {
                        myWarningsLiveData.postValue(response.body())
                        Log.d("MyWarningsAPI", "Success url=${call.request().url} body=${response.body()}")
                    } else {
                        // Never surface the raw error body to the UI: a non-2xx from
                        // this endpoint often returns an HTML error page, which would
                        // otherwise be shown verbatim in a Toast (the <!DOCTYPE html>...
                        // leak). Keep the raw body in the log only.
                        val err = response.errorBody()?.string()
                        myWarningsErrorLiveData.postValue("Failed to fetch warnings")
                        Log.e("MyWarningsAPI", "Error ${response.code()} url=${call.request().url} body=$err")
                    }
                }

                override fun onFailure(call: Call<MyWarningsResponse>, t: Throwable) {
                    myWarningsErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    Log.e("MyWarningsAPI", "Failure url=${call.request().url}", t)
                }

                override fun onNoNetwork() {
                    myWarningsErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}

