package com.gmwapp.hima.viewmodels


import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.IndividualAppUpdateRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.IndividualAppUpdateResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class IndividualAppUpdateViewModel @Inject constructor(
    private val repository: IndividualAppUpdateRepository
) : ViewModel() {

    val individualUpdateLiveData = MutableLiveData<IndividualAppUpdateResponse>()
    val individualUpdateErrorLiveData = MutableLiveData<String>()

    fun checkUserAppVersion(userId: Int, currentVersion: String) {
        viewModelScope.launch {
            repository.updateUserVersion(userId, currentVersion, object : NetworkCallback<IndividualAppUpdateResponse> {
                override fun onResponse(
                    call: Call<IndividualAppUpdateResponse>,
                    response: Response<IndividualAppUpdateResponse>
                ) {
                    individualUpdateLiveData.postValue(response.body())
                    Log.d("individualAppUpdateViewModel","${response.body()}")

                }

                override fun onFailure(call: Call<IndividualAppUpdateResponse>, t: Throwable) {
                    individualUpdateErrorLiveData.postValue("Something went wrong: ${t.message}")
                }

                override fun onNoNetwork() {
                    individualUpdateErrorLiveData.postValue("No internet connection")
                }
            })
        }
    }
}
