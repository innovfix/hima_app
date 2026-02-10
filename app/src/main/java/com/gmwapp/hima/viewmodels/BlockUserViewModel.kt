package com.gmwapp.hima.viewmodels
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.BlockUserRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.BlockUserResponse
import com.gmwapp.hima.retrofit.responses.CheckBlockStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class BlockUserViewModel @Inject constructor(private val repository: BlockUserRepository) : ViewModel() {

    val blockUserLiveData = MutableLiveData<BlockUserResponse>()
    val blockUserErrorLiveData = MutableLiveData<String>()
    
    val checkBlockStatusLiveData = MutableLiveData<CheckBlockStatusResponse>()
    val checkBlockStatusErrorLiveData = MutableLiveData<String>()
    
    val unblockUserLiveData = MutableLiveData<BlockUserResponse>()
    val unblockUserErrorLiveData = MutableLiveData<String>()

    fun blockUser(userId: Int, callUserId: Int, blocked: Int) {
        viewModelScope.launch {
            repository.blockUser(userId, callUserId, blocked, object :
                NetworkCallback<BlockUserResponse> {
                override fun onResponse(
                    call: Call<BlockUserResponse>,
                    response: Response<BlockUserResponse>
                ) {
                    if (response.isSuccessful) {
                        blockUserLiveData.postValue(response.body())
                        Log.d("BlockUserAPI", "✅ Block user success: ${response.body()}")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("BlockUserAPI", "❌ Block user failed: $errorBody")
                        try {
                            val gson = com.google.gson.Gson()
                            val errorResponse = gson.fromJson(errorBody, BlockUserResponse::class.java)
                            val errorMessage = errorResponse.message ?: "Failed to block user"
                            blockUserErrorLiveData.postValue(errorMessage)
                        } catch (e: Exception) {
                            blockUserErrorLiveData.postValue("Failed to block user")
                        }
                    }
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    blockUserErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    Log.e("BlockUserAPI", "❌ Block user error: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    blockUserErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
    
    fun checkBlockStatus(userId: Int, callUserId: Int) {
        viewModelScope.launch {
            Log.d("BlockUserAPI", "🔍 Checking block status - userId: $userId, callUserId: $callUserId")
            repository.checkBlockStatus(userId, callUserId, object :
                NetworkCallback<CheckBlockStatusResponse> {
                override fun onResponse(
                    call: Call<CheckBlockStatusResponse>,
                    response: Response<CheckBlockStatusResponse>
                ) {
                    Log.d("BlockUserAPI", "===== CHECK BLOCK STATUS RESPONSE =====")
                    Log.d("BlockUserAPI", "HTTP Status: ${response.code()}")
                    Log.d("BlockUserAPI", "Is Successful: ${response.isSuccessful}")
                    Log.d("BlockUserAPI", "Response Body: ${response.body()}")
                    
                    if (response.isSuccessful) {
                        checkBlockStatusLiveData.postValue(response.body())
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("BlockUserAPI", "Error Body: $errorBody")
                        try {
                            val gson = com.google.gson.Gson()
                            val errorResponse = gson.fromJson(errorBody, CheckBlockStatusResponse::class.java)
                            val errorMessage = errorResponse.message ?: "Failed to check block status"
                            checkBlockStatusErrorLiveData.postValue(errorMessage)
                        } catch (e: Exception) {
                            checkBlockStatusErrorLiveData.postValue("Failed to check block status")
                        }
                    }
                    Log.d("BlockUserAPI", "======================================")
                }

                override fun onFailure(call: Call<CheckBlockStatusResponse>, t: Throwable) {
                    checkBlockStatusErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    Log.e("BlockUserAPI", "❌ Check block status error: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    checkBlockStatusErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
    
    fun unblockUser(userId: Int, callUserId: Int) {
        viewModelScope.launch {
            Log.d("BlockUserAPI", "🔓 Unblocking user - userId: $userId, callUserId: $callUserId")
            repository.unblockUser(userId, callUserId, object :
                NetworkCallback<BlockUserResponse> {
                override fun onResponse(
                    call: Call<BlockUserResponse>,
                    response: Response<BlockUserResponse>
                ) {
                    Log.d("BlockUserAPI", "===== UNBLOCK USER RESPONSE =====")
                    Log.d("BlockUserAPI", "HTTP Status: ${response.code()}")
                    Log.d("BlockUserAPI", "Response Body: ${response.body()}")
                    
                    if (response.isSuccessful) {
                        unblockUserLiveData.postValue(response.body())
                        Log.d("BlockUserAPI", "✅ Unblock user success")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("BlockUserAPI", "Error Body: $errorBody")
                        try {
                            val gson = com.google.gson.Gson()
                            val errorResponse = gson.fromJson(errorBody, BlockUserResponse::class.java)
                            val errorMessage = errorResponse.message ?: "Failed to unblock user"
                            unblockUserErrorLiveData.postValue(errorMessage)
                        } catch (e: Exception) {
                            unblockUserErrorLiveData.postValue("Failed to unblock user")
                        }
                    }
                    Log.d("BlockUserAPI", "======================================")
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    unblockUserErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                    Log.e("BlockUserAPI", "❌ Unblock user error: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    unblockUserErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}