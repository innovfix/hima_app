package com.gmwapp.hima.repositories

import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.AvatarsListResponse
import com.gmwapp.hima.retrofit.responses.CallsListResponse
import com.gmwapp.hima.retrofit.responses.DeleteUserResponse
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.retrofit.responses.RegisterResponse
import com.gmwapp.hima.retrofit.responses.SpeechTextResponse
import com.gmwapp.hima.retrofit.responses.UpdateProfileResponse
import com.gmwapp.hima.retrofit.responses.UserValidationResponse
import com.gmwapp.hima.retrofit.responses.VoiceUpdateResponse
import okhttp3.MultipartBody
import retrofit2.Response
import javax.inject.Inject

class ProfileRepositories @Inject constructor(private val apiManager: ApiManager) {
    fun getAvatarsList(gender: String, callback: NetworkCallback<AvatarsListResponse>) {
        apiManager.getAvatarsList(gender, callback)
    }

    fun getCallsList(userId: Int, gender: String,limit: Int, currentOffset: Int, callback: NetworkCallback<CallsListResponse>) {
        apiManager.getCallsList(userId, gender,limit,currentOffset, callback)
    }

    fun register(
        mobile: String,
        language: String,
        avatarId: Int,
        gender: String,
        savedReferCode:String,
        callback: NetworkCallback<RegisterResponse>
    ) {
        apiManager.register(mobile, language, avatarId, gender,savedReferCode, callback)
    }

    fun getUser(
        userId: Int,
        callback: NetworkCallback<RegisterResponse>
    ) {
        apiManager.getUser(userId, callback)
    }

    fun getUserSync(
        userId: Int
    ): Response<RegisterResponse> {
        return apiManager.getUserSync(userId)
    }

    fun registerFemale(
        mobile: String,
        language: String,
        avatarId: Int,
        gender: String,
        age: String,
        interests: String,
        describe_yourself: String,
        savedReferCode:String,
        callback: NetworkCallback<RegisterResponse>
    ) {
        apiManager.registerFemale(
            mobile, language, avatarId, gender, age, interests, describe_yourself, savedReferCode,callback
        )
    }

    fun updateProfile(
        userId: Int,
        avatarId: Int,
        name: String,
        bio: String?,
        interests: String?,
        callback: NetworkCallback<UpdateProfileResponse>
    ) {
        apiManager.updateProfile(userId, avatarId, name,bio, interests, callback)
    }

    fun deleteUsers(
        userId: Int, deleteReason: String, callback: NetworkCallback<DeleteUserResponse>
    ) {
        apiManager.deleteUsers(userId, deleteReason, callback)
    }

    fun userValidation(
        userId: Int, name: String, callback: NetworkCallback<UserValidationResponse>
    ) {
        apiManager.userValidation(userId, name, callback)
    }

    fun getSpeechText(
        userId: Int, language: String, callback: NetworkCallback<SpeechTextResponse>
    ) {
        apiManager.getSpeechText(userId, language, callback)
    }

    fun updateVoice(
        userId: Int, voice: MultipartBody.Part, callback: NetworkCallback<VoiceUpdateResponse>
    ) {
        apiManager.updateVoice(userId, voice, callback)
    }

    fun getRemainingTime(
        userId: Int, callType: String, callback: NetworkCallback<GetRemainingTimeResponse>
    ) {
        apiManager.getRemainingTime(userId, callType, callback)
    }
}