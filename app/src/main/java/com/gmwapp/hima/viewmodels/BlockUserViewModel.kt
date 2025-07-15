package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.BlockUserRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class BlockUserViewModel @Inject constructor(private val repository: BlockUserRepository) : ViewModel() {

    val blockUserLiveData = MutableLiveData<BlockUserResponse>()
    val blockUserErrorLiveData = MutableLiveData<String>()

    fun blockUser(userId: Int, callUserId: Int, blocked: Int) {
        viewModelScope.launch {
            repository.blockUser(userId, callUserId, blocked, object :
                NetworkCallback<BlockUserResponse> {
                override fun onResponse(
                    call: Call<BlockUserResponse>,
                    response: Response<BlockUserResponse>
                ) {
                    blockUserLiveData.postValue(response.body())
                    Log.d("blockUserLiveData","${response.body()}")
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    blockUserErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    Log.d("blockUserErrorLiveData","${t}")

                }

                override fun onNoNetwork() {
                    blockUserErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}
