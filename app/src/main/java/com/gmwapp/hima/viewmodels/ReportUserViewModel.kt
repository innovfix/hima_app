package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.ReportUserRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ReportReasonsResponse
import com.gmwapp.hima.retrofit.responses.ReportUserResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class ReportUserViewModel @Inject constructor(
    private val repository: ReportUserRepository
) : ViewModel() {

    val reportReasonsLiveData = MutableLiveData<ReportReasonsResponse>()
    val reportReasonsErrorLiveData = MutableLiveData<String>()
    val reportUserLiveData = MutableLiveData<ReportUserResponse>()
    val reportUserErrorLiveData = MutableLiveData<String>()

    fun getReportReasons(userId: Int) {
        viewModelScope.launch {
            repository.getReportReasons(userId, object : NetworkCallback<ReportReasonsResponse> {
                override fun onResponse(
                    call: Call<ReportReasonsResponse>,
                    response: Response<ReportReasonsResponse>
                ) {
                    Log.d("ReportUserAPI", "===== GET REPORT REASONS RESPONSE =====")
                    Log.d("ReportUserAPI", "Request URL: ${call.request().url}")
                    Log.d("ReportUserAPI", "HTTP Status Code: ${response.code()}")
                    Log.d("ReportUserAPI", "Is Successful: ${response.isSuccessful}")
                    Log.d("ReportUserAPI", "Response Body: ${response.body()}")
                    Log.d("ReportUserAPI", "Response Message: ${response.message()}")
                    
                    if (!response.isSuccessful) {
                        val errorBodyString = response.errorBody()?.string()
                        Log.e("ReportUserAPI", "Error Body: $errorBodyString")
                        
                        // Parse error body JSON to extract the message
                        try {
                            if (!errorBodyString.isNullOrEmpty()) {
                                val gson = com.google.gson.Gson()
                                val errorResponse = gson.fromJson(errorBodyString, ReportReasonsResponse::class.java)
                                val errorMessage = errorResponse.message ?: "Failed to load report reasons"
                                Log.e("ReportUserAPI", "Parsed error message: $errorMessage")
                                reportReasonsErrorLiveData.postValue(errorMessage)
                            } else {
                                reportReasonsErrorLiveData.postValue("Failed to load report reasons")
                            }
                        } catch (e: Exception) {
                            Log.e("ReportUserAPI", "Failed to parse error body", e)
                            reportReasonsErrorLiveData.postValue("Failed to load report reasons")
                        }
                    } else {
                        reportReasonsLiveData.postValue(response.body())
                    }
                    
                    Log.d("ReportUserAPI", "Response Headers: ${response.headers()}")
                    Log.d("ReportUserAPI", "======================================")
                }

                override fun onFailure(call: Call<ReportReasonsResponse>, t: Throwable) {
                    Log.e("ReportUserAPI", "===== GET REPORT REASONS FAILURE =====")
                    Log.e("ReportUserAPI", "Request URL: ${call.request().url}")
                    Log.e("ReportUserAPI", "Error Message: ${t.message}")
                    Log.e("ReportUserAPI", "Error Type: ${t.javaClass.simpleName}")
                    Log.e("ReportUserAPI", "Stack Trace:", t)
                    Log.e("ReportUserAPI", "======================================")
                    
                    reportReasonsErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    Log.w("ReportUserAPI", "No network connection for getReportReasons")
                    reportReasonsErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }

    fun reportUser(userId: Int, reportUserId: Int, reasonId: Int, reasonText: String?) {
        viewModelScope.launch {
            Log.d("ReportUserAPI", "===== SUBMITTING REPORT USER =====")
            Log.d("ReportUserAPI", "User ID: $userId")
            Log.d("ReportUserAPI", "Report User ID: $reportUserId")
            Log.d("ReportUserAPI", "Reason ID: $reasonId")
            Log.d("ReportUserAPI", "Reason Text: $reasonText")
            
            repository.submitReport(userId, reportUserId, reasonId, reasonText, object :
                NetworkCallback<ReportUserResponse> {
                override fun onResponse(
                    call: Call<ReportUserResponse>,
                    response: Response<ReportUserResponse>
                ) {
                    Log.d("ReportUserAPI", "===== REPORT USER RESPONSE =====")
                    Log.d("ReportUserAPI", "Request URL: ${call.request().url}")
                    Log.d("ReportUserAPI", "Request Body: ${call.request().body}")
                    Log.d("ReportUserAPI", "HTTP Status Code: ${response.code()}")
                    Log.d("ReportUserAPI", "Is Successful: ${response.isSuccessful}")
                    Log.d("ReportUserAPI", "Response Body: ${response.body()}")
                    Log.d("ReportUserAPI", "Response Body toString: ${response.body()?.toString()}")
                    Log.d("ReportUserAPI", "Response Message: ${response.message()}")
                    
                    if (!response.isSuccessful) {
                        val errorBodyString = response.errorBody()?.string()
                        Log.e("ReportUserAPI", "Error Body: $errorBodyString")
                        
                        // Parse error body JSON to extract the message
                        try {
                            if (!errorBodyString.isNullOrEmpty()) {
                                val gson = com.google.gson.Gson()
                                val errorResponse = gson.fromJson(errorBodyString, ReportUserResponse::class.java)
                                val errorMessage = errorResponse.message ?: "Failed to report user"
                                Log.e("ReportUserAPI", "Parsed error message: $errorMessage")
                                reportUserErrorLiveData.postValue(errorMessage)
                            } else {
                                reportUserErrorLiveData.postValue("Failed to report user")
                            }
                        } catch (e: Exception) {
                            Log.e("ReportUserAPI", "Failed to parse error body", e)
                            reportUserErrorLiveData.postValue("Failed to report user")
                        }
                    } else {
                        // Try to log individual fields if response body is not null
                        response.body()?.let { body ->
                            Log.d("ReportUserAPI", "Response success field: ${body.success}")
                            Log.d("ReportUserAPI", "Response message field: ${body.message}")
                            Log.d("ReportUserAPI", "Response data field: ${body.data}")
                        } ?: Log.w("ReportUserAPI", "Response body is NULL!")
                        
                        reportUserLiveData.postValue(response.body())
                    }
                    
                    Log.d("ReportUserAPI", "Response Headers: ${response.headers()}")
                    Log.d("ReportUserAPI", "Raw Response: ${response.raw()}")
                    Log.d("ReportUserAPI", "======================================")
                }

                override fun onFailure(call: Call<ReportUserResponse>, t: Throwable) {
                    Log.e("ReportUserAPI", "===== REPORT USER FAILURE =====")
                    Log.e("ReportUserAPI", "Request URL: ${call.request().url}")
                    Log.e("ReportUserAPI", "Error Message: ${t.message}")
                    Log.e("ReportUserAPI", "Error Type: ${t.javaClass.simpleName}")
                    Log.e("ReportUserAPI", "Stack Trace:", t)
                    Log.e("ReportUserAPI", "======================================")
                    
                    reportUserErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    Log.w("ReportUserAPI", "No network connection for reportUser")
                    reportUserErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}
