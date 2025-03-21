package com.gmwapp.hima.viewmodels


import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.UserAvatarRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.UserAvatarResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class UserAvatarViewModel @Inject constructor(private val repository: UserAvatarRepository) : ViewModel() {

    val userAvatarLiveData = MutableLiveData<UserAvatarResponse>()
    val userAvatarErrorLiveData = MutableLiveData<String>()

    fun getUserAvatar(userId: Int) {
        viewModelScope.launch {
            repository.getUserAvatar(userId, object : NetworkCallback<UserAvatarResponse> {
                override fun onResponse(call: Call<UserAvatarResponse>, response: Response<UserAvatarResponse>) {
                    Log.d("userAvatarLiveDataViewModel","${response.body()}")
                    Log.d("userAvatarLiveDataViewModel","${call.request().url}")

                    Log.d("API_RESPONSE", "Code: ${response.code()}")
                    Log.d("API_RESPONSE", "Body: ${response.body()}")
                    Log.d("API_RESPONSE", "Error Body: ${response.errorBody()?.string()}")
                    userAvatarLiveData.postValue(response.body())
                }

                override fun onFailure(call: Call<UserAvatarResponse>, t: Throwable) {
                    Log.d("userAvatarLiveDataViewModel","${t.message}")

                    userAvatarErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    userAvatarErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}
