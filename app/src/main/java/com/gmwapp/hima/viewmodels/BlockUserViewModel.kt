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
                    val body = response.body()
                    if (response.isSuccessful && body != null && body.success != false) {
                        blockUserLiveData.postValue(body)
                        Log.d("BlockUserAPI", "✅ Block user success: $body")
                    } else if (response.isSuccessful && (body == null || body.success == false)) {
                        val msg = body?.message?.takeIf { it.isNotBlank() } ?: "Failed to block user"
                        blockUserErrorLiveData.postValue(msg)
                        Log.e("BlockUserAPI", "❌ Block user rejected: $body")
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

                    val body = response.body()
                    if (response.isSuccessful && body != null && body.success != false) {
                        unblockUserLiveData.postValue(body)
                        Log.d("BlockUserAPI", "✅ Unblock user success")
                    } else if (response.isSuccessful && (body == null || body.success == false)) {
                        val msg = body?.message?.takeIf { it.isNotBlank() } ?: "Failed to unblock user"
                        Log.e("BlockUserAPI", "❌ Unblock rejected by API: $body — trying blocked=0 fallback")
                        unblockViaBlockedZeroFallback(userId, callUserId, msg)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("BlockUserAPI", "Error Body: $errorBody — trying blocked=0 fallback")
                        try {
                            val gson = com.google.gson.Gson()
                            val errorResponse = gson.fromJson(errorBody, BlockUserResponse::class.java)
                            val errorMessage = errorResponse.message ?: "Failed to unblock user"
                            unblockViaBlockedZeroFallback(userId, callUserId, errorMessage)
                        } catch (e: Exception) {
                            unblockViaBlockedZeroFallback(userId, callUserId, "Failed to unblock user")
                        }
                    }
                    Log.d("BlockUserAPI", "======================================")
                }

                override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                    Log.e("BlockUserAPI", "❌ Unblock user error: ${t.message} — trying blocked=0 fallback", t)
                    unblockViaBlockedZeroFallback(userId, callUserId, DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    unblockUserErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }

    /** Some backends clear blocks via `blocked_user` with blocked=0 instead of `unblock_user`. */
    private fun unblockViaBlockedZeroFallback(userId: Int, callUserId: Int, primaryError: String) {
        repository.blockUser(userId, callUserId, 0, object : NetworkCallback<BlockUserResponse> {
            override fun onResponse(
                call: Call<BlockUserResponse>,
                response: Response<BlockUserResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success != false) {
                    unblockUserLiveData.postValue(body)
                    Log.d("BlockUserAPI", "✅ Unblock via blocked=0 succeeded")
                } else {
                    unblockUserErrorLiveData.postValue(primaryError)
                    Log.e("BlockUserAPI", "❌ Unblock fallback blocked=0 also failed: $body")
                }
            }

            override fun onFailure(call: Call<BlockUserResponse>, t: Throwable) {
                unblockUserErrorLiveData.postValue(primaryError)
                Log.e("BlockUserAPI", "❌ Unblock fallback network error: ${t.message}", t)
            }

            override fun onNoNetwork() {
                unblockUserErrorLiveData.postValue(DConstants.NO_NETWORK)
            }
        })
    }
}