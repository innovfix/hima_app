package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.ProfileRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AvatarsListResponse
import com.gmwapp.hima.retrofit.responses.DeleteUserResponse
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.retrofit.responses.SpeechTextResponse
import com.gmwapp.hima.retrofit.responses.UpdateProfileResponse
import com.gmwapp.hima.retrofit.responses.UserValidationResponse
import com.gmwapp.hima.retrofit.responses.VoiceUpdateResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import org.greenrobot.eventbus.EventBus
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@HiltViewModel
class ProfileViewModel @Inject constructor(private val profileRepositories: ProfileRepositories) :
    ViewModel() {

    val registerLiveData = MutableLiveData<RegisterResponse>()
    val getUserLiveData = MutableLiveData<RegisterResponse>()
    val getUserLiveStatus = MutableLiveData<RegisterResponse>()
    val registerErrorLiveData = MutableLiveData<String>()
    val updateProfileLiveData = MutableLiveData<UpdateProfileResponse>()
    val updateProfileErrorLiveData = MutableLiveData<String>()
    val deleteUserLiveData = MutableLiveData<DeleteUserResponse>()
    val deleteUserErrorLiveData = MutableLiveData<String>()
    val userValidationLiveData = MutableLiveData<UserValidationResponse>()
    val userValidationErrorLiveData = MutableLiveData<String>()
    val speechTextLiveData = MutableLiveData<SpeechTextResponse>()
    val speechTextErrorLiveData = MutableLiveData<String>()
    val voiceUpdateLiveData = MutableLiveData<VoiceUpdateResponse>()
    val voiceUpdateErrorLiveData = MutableLiveData<String>()
    val remainingTimeLiveData = MutableLiveData<GetRemainingTimeResponse>()
    val remainingTimeErrorLiveData = MutableLiveData<String>()
    val avatarsListLiveData = MutableLiveData<AvatarsListResponse>()
    fun getAvatarsList(gender: String) {
        viewModelScope.launch {
            profileRepositories.getAvatarsList(
                gender,
                object : NetworkCallback<AvatarsListResponse> {
                    override fun onResponse(
                        call: Call<AvatarsListResponse>, response: Response<AvatarsListResponse>
                    ) {
                        avatarsListLiveData.postValue(response.body())
                        Log.d("avatarsListLiveData","${response.body()}")

                    }

                    override fun onFailure(call: Call<AvatarsListResponse>, t: Throwable) {
                    }

                    override fun onNoNetwork() {
                    }
                })
        }
    }

    fun register(
        mobile: String, language: String, avatarId: Int, gender: String, savedReferCode:String
    ) {
        viewModelScope.launch {
            profileRepositories.register(
                mobile,
                language,
                avatarId,
                gender,
                savedReferCode,
                object : NetworkCallback<RegisterResponse> {
                    override fun onResponse(
                        call: Call<RegisterResponse>, response: Response<RegisterResponse>
                    ) {
                        registerLiveData.postValue(response.body())
                        Log.d("registerLiveData","${response.body()}")
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        registerErrorLiveData.postValue(t.message)
                    }

                    override fun onNoNetwork() {
                        registerErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun getUsers(
        userId: Int
    ) {
        viewModelScope.launch {
            profileRepositories.getUser(
                userId,
                object : NetworkCallback<RegisterResponse> {
                    override fun onResponse(
                        call: Call<RegisterResponse>, response: Response<RegisterResponse>
                    ) {
                        getUserLiveData.postValue(response.body())
                        Log.d("getUserLiveData","${response.body()}")
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        Log.d("getUserLiveData","${t.message}")

                    }

                    override fun onNoNetwork() {
                    }
                })
        }
    }


    fun getUsersStatus(
        userId: String
    ) {
        viewModelScope.launch {
            profileRepositories.getUserStatus(
                userId,
                object : NetworkCallback<RegisterResponse> {
                    override fun onResponse(
                        call: Call<RegisterResponse>, response: Response<RegisterResponse>
                    ) {
                        getUserLiveStatus.postValue(response.body())
                        Log.d("getUserLiveStatus","${response.body()}")
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        Log.d("getUserLiveData","${t.message}")

                    }

                    override fun onNoNetwork() {
                    }
                })
        }
    }

    fun getUserSync(
        userId: Int
    ): Response<RegisterResponse> {
            return profileRepositories.getUserSync(
                userId)
    }

    fun registerFemale(
        mobile: String,
        language: String,
        avatarId: Int,
        gender: String,
        age: String,
        interests: String,
        describe_yourself: String,
        savedReferCode:String
    ) {
        viewModelScope.launch {
            profileRepositories.registerFemale(
                mobile,
                language,
                avatarId,
                gender,
                age,
                interests,
                describe_yourself,
                savedReferCode,
                object : NetworkCallback<RegisterResponse> {
                    override fun onResponse(
                        call: Call<RegisterResponse>, response: Response<RegisterResponse>
                    ) {
                        registerLiveData.postValue(response.body())
                        Log.d("registerFemale","${response.body()}")
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        registerErrorLiveData.postValue(t.message)
                        Log.d("registerFemale","${t.message}")

                    }

                    override fun onNoNetwork() {
                        registerErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun updateProfile(
        userId: Int, avatarId: Int, name: String, interests: ArrayList<String>?
    ) {
        viewModelScope.launch {
            profileRepositories.updateProfile(
                userId,
                avatarId,
                name,
                interests?.joinToString(separator = ",") { it },
                object : NetworkCallback<UpdateProfileResponse> {
                    override fun onResponse(
                        call: Call<UpdateProfileResponse>, response: Response<UpdateProfileResponse>
                    ) {
                        updateProfileLiveData.postValue(response.body())
                        Log.d("updateProfileLiveData","${response.body()}")
                    }

                    override fun onFailure(call: Call<UpdateProfileResponse>, t: Throwable) {
                        updateProfileErrorLiveData.postValue(t.message)
                    }

                    override fun onNoNetwork() {
                        updateProfileErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun deleteUsers(
        userId: Int,
        deleteReason: String,
    ) {
        viewModelScope.launch {
            profileRepositories.deleteUsers(
                userId,
                deleteReason,
                object : NetworkCallback<DeleteUserResponse> {
                    override fun onResponse(
                        call: Call<DeleteUserResponse>, response: Response<DeleteUserResponse>
                    ) {
                        deleteUserLiveData.postValue(response.body())
                    }

                    override fun onFailure(call: Call<DeleteUserResponse>, t: Throwable) {
                        deleteUserErrorLiveData.postValue(t.message)
                    }

                    override fun onNoNetwork() {
                        deleteUserErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun userValidation(
        userId: Int,
        name: String,
    ) {
        viewModelScope.launch {
            profileRepositories.userValidation(
                userId,
                name,
                object : NetworkCallback<UserValidationResponse> {
                    override fun onResponse(
                        call: Call<UserValidationResponse>,
                        response: Response<UserValidationResponse>
                    ) {
                        val responseBody = response.body()
                        if (response.isSuccessful && responseBody != null) {
                            // Post the response body (contains success status and message)
                            // Backend returns HTTP 200 even for errors, with success: false in JSON
                            userValidationLiveData.postValue(responseBody)
                            Log.d("userValidationLiveData","Response: ${responseBody}")
                        } else {
                            // Handle cases where response body is null or HTTP error
                            val errorMessage = try {
                                if (response.errorBody() != null) {
                                    response.errorBody()?.string() ?: "An error occurred"
                                } else {
                                    "An error occurred. Please try again."
                                }
                            } catch (e: Exception) {
                                "An error occurred. Please try again."
                            }
                            // Create error response object
                            val errorResponse = UserValidationResponse(
                                success = false,
                                message = errorMessage
                            )
                            userValidationLiveData.postValue(errorResponse)
                            Log.e("userValidationLiveData","Error response: $errorMessage")
                        }
                    }

                    override fun onFailure(call: Call<UserValidationResponse>, t: Throwable) {
                        val errorMessage = t.message ?: "Network error occurred"
                        userValidationErrorLiveData.postValue(errorMessage)
                        Log.e("userValidationErrorLiveData","Network failure: $errorMessage")
                    }

                    override fun onNoNetwork() {
                        userValidationErrorLiveData.postValue(DConstants.NO_NETWORK)
                        Log.e("userValidationErrorLiveData","No network connection")
                    }
                })
        }
    }

    fun getSpeechText(
        userId: Int,
        language: String,
    ) {
        viewModelScope.launch {
            profileRepositories.getSpeechText(
                userId,
                language,
                object : NetworkCallback<SpeechTextResponse> {
                    override fun onResponse(
                        call: Call<SpeechTextResponse>, response: Response<SpeechTextResponse>
                    ) {
                        speechTextLiveData.postValue(response.body())
                        Log.d("speechtest","${response.body()}")

                    }

                    override fun onFailure(call: Call<SpeechTextResponse>, t: Throwable) {
                        speechTextErrorLiveData.postValue(t.message)
                        Log.d("speechtest","${t.message}")
                    }

                    override fun onNoNetwork() {
                        speechTextErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun updateVoice(
        userId: Int,
        voice: MultipartBody.Part,
    ) {
        viewModelScope.launch {
            profileRepositories.updateVoice(
                userId,
                voice,
                object : NetworkCallback<VoiceUpdateResponse> {
                    override fun onResponse(
                        call: Call<VoiceUpdateResponse>, response: Response<VoiceUpdateResponse>
                    ) {
                        voiceUpdateLiveData.postValue(response.body())
                    }

                    override fun onFailure(call: Call<VoiceUpdateResponse>, t: Throwable) {
                        voiceUpdateErrorLiveData.postValue(t.message)
                    }

                    override fun onNoNetwork() {
                        voiceUpdateErrorLiveData.postValue(DConstants.NO_NETWORK)
                    }
                })
        }
    }

    fun getRemainingTime(
        userId: Int,
        callType: String,
        callback: NetworkCallback<GetRemainingTimeResponse>
    ) {
        viewModelScope.launch {
            profileRepositories.getRemainingTime(
                userId,
                callType,
                callback)
        }
    }

}

