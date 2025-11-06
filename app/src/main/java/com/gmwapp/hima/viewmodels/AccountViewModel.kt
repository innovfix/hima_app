package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.AccountRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CategoriesResponse
import com.gmwapp.hima.retrofit.responses.SettingsResponse
import com.gmwapp.hima.retrofit.responses.SubmitTicketResponse
import com.gmwapp.hima.retrofit.responses.SubqueriesResponse
import com.gmwapp.hima.retrofit.responses.TicketsListResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@HiltViewModel
class AccountViewModel @Inject constructor(private val accountRepositories: AccountRepositories) : ViewModel() {

    val settingsLiveData = MutableLiveData<SettingsResponse>()
    val categoriesLiveData = MutableLiveData<CategoriesResponse>()
    val categoriesErrorLiveData = MutableLiveData<String>()
    val subqueriesLiveData = MutableLiveData<SubqueriesResponse>()
    val subqueriesErrorLiveData = MutableLiveData<String>()
    val submitTicketLiveData = MutableLiveData<SubmitTicketResponse>()
    val submitTicketErrorLiveData = MutableLiveData<String>()
    val ticketsListLiveData = MutableLiveData<TicketsListResponse>()
    val ticketsListErrorLiveData = MutableLiveData<String>()

    fun getSettings() {
        viewModelScope.launch {
            accountRepositories.getSettings(object:NetworkCallback<SettingsResponse> {
                override fun onResponse(
                    call: Call<SettingsResponse>,
                    response: Response<SettingsResponse>
                ) {
                    settingsLiveData.postValue(response.body());
                    Log.d("settingResponse", "Request URL: ${call.request().url}")
                    Log.d("responsesetting", "Request URL: ${response.body()}")



                }

                override fun onFailure(call: Call<SettingsResponse>, t: Throwable) {
                    Log.d("responsesetting", "Request URL: ${t.message}")

                }

                override fun onNoNetwork() {
                }
            })
        }
    }

    fun getCategoriesList(userId: Int) {
        viewModelScope.launch {
            accountRepositories.getCategoriesList(userId, object: NetworkCallback<CategoriesResponse> {
                override fun onResponse(
                    call: Call<CategoriesResponse>,
                    response: Response<CategoriesResponse>
                ) {
                    categoriesLiveData.postValue(response.body())
                    Log.d("CategoriesResponse", "Request URL: ${call.request().url}")
                    Log.d("CategoriesResponse", "Response: ${response.body()}")
                }

                override fun onFailure(call: Call<CategoriesResponse>, t: Throwable) {
                    categoriesErrorLiveData.postValue(t.message ?: "Unknown error")
                    Log.e("CategoriesResponse", "Error: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    categoriesErrorLiveData.postValue("No network connection")
                }
            })
        }
    }

    fun getSubqueriesList(userId: Int, categoryId: Int) {
        viewModelScope.launch {
            accountRepositories.getSubqueriesList(userId, categoryId, object: NetworkCallback<SubqueriesResponse> {
                override fun onResponse(
                    call: Call<SubqueriesResponse>,
                    response: Response<SubqueriesResponse>
                ) {
                    subqueriesLiveData.postValue(response.body())
                    Log.d("SubqueriesResponse", "Request URL: ${call.request().url}")
                    Log.d("SubqueriesResponse", "Response: ${response.body()}")
                }

                override fun onFailure(call: Call<SubqueriesResponse>, t: Throwable) {
                    subqueriesErrorLiveData.postValue(t.message ?: "Unknown error")
                    Log.e("SubqueriesResponse", "Error: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    subqueriesErrorLiveData.postValue("No network connection")
                }
            })
        }
    }

    fun submitTicket(userId: Int, message: String, screenshots: List<okhttp3.MultipartBody.Part>) {
        viewModelScope.launch {
            accountRepositories.submitTicket(userId, message, screenshots, object: NetworkCallback<SubmitTicketResponse> {
                override fun onResponse(
                    call: Call<SubmitTicketResponse>,
                    response: Response<SubmitTicketResponse>
                ) {
                    submitTicketLiveData.postValue(response.body())
                    Log.d("SubmitTicketResponse", "Request URL: ${call.request().url}")
                    Log.d("SubmitTicketResponse", "Response: ${response.body()}")
                }

                override fun onFailure(call: Call<SubmitTicketResponse>, t: Throwable) {
                    submitTicketErrorLiveData.postValue(t.message ?: "Unknown error")
                    Log.e("SubmitTicketResponse", "Error: ${t.message}", t)
                }

                override fun onNoNetwork() {
                    submitTicketErrorLiveData.postValue("No network connection")
                }
            })
        }
    }

    fun getTicketsList(userId: Int) {
        viewModelScope.launch {
            Log.d("TicketsListAPI", "=== TICKET LIST API REQUEST ===")
            Log.d("TicketsListAPI", "User ID: $userId")
            
            accountRepositories.getTicketsList(userId, object: NetworkCallback<TicketsListResponse> {
                override fun onResponse(
                    call: Call<TicketsListResponse>,
                    response: Response<TicketsListResponse>
                ) {
                    Log.d("TicketsListAPI", "=== TICKET LIST API RESPONSE ===")
                    Log.d("TicketsListAPI", "Request URL: ${call.request().url}")
                    Log.d("TicketsListAPI", "Request Method: ${call.request().method}")
                    Log.d("TicketsListAPI", "Response Code: ${response.code()}")
                    Log.d("TicketsListAPI", "Response Message: ${response.message()}")
                    Log.d("TicketsListAPI", "Is Successful: ${response.isSuccessful}")
                    
                    val responseBody = response.body()
                    if (responseBody != null) {
                        Log.d("TicketsListAPI", "Response Body:")
                        Log.d("TicketsListAPI", "  - Success: ${responseBody.success}")
                        Log.d("TicketsListAPI", "  - Message: ${responseBody.message}")
                        Log.d("TicketsListAPI", "  - Total: ${responseBody.total}")
                        Log.d("TicketsListAPI", "  - Data Count: ${responseBody.data?.size ?: 0}")
                        
                        responseBody.data?.forEachIndexed { index, ticket ->
                            Log.d("TicketsListAPI", "  Ticket[$index]:")
                            Log.d("TicketsListAPI", "    - ID: ${ticket.id}")
                            Log.d("TicketsListAPI", "    - User ID: ${ticket.user_id}")
                            Log.d("TicketsListAPI", "    - Message: ${ticket.message}")
                            Log.d("TicketsListAPI", "    - Screenshot: ${ticket.screenshot}")
                            Log.d("TicketsListAPI", "    - Reply: ${ticket.reply}")
                            Log.d("TicketsListAPI", "    - Status: ${ticket.status} (0=active, 1=resolved)")
                            Log.d("TicketsListAPI", "    - Created At: ${ticket.created_at}")
                            Log.d("TicketsListAPI", "    - Updated At: ${ticket.updated_at}")
                        }
                        
                        Log.d("TicketsListAPI", "Full Response JSON: $responseBody")
                    } else {
                        Log.w("TicketsListAPI", "Response body is null!")
                        try {
                            val errorBody = response.errorBody()?.string()
                            Log.d("TicketsListAPI", "Error Body: $errorBody")
                        } catch (e: Exception) {
                            Log.e("TicketsListAPI", "Error reading error body: ${e.message}")
                        }
                    }
                    
                    ticketsListLiveData.postValue(responseBody)
                }

                override fun onFailure(call: Call<TicketsListResponse>, t: Throwable) {
                    Log.e("TicketsListAPI", "=== TICKET LIST API FAILURE ===")
                    Log.e("TicketsListAPI", "Request URL: ${call.request().url}")
                    Log.e("TicketsListAPI", "Error Message: ${t.message}")
                    Log.e("TicketsListAPI", "Error Type: ${t.javaClass.simpleName}")
                    Log.e("TicketsListAPI", "Stack Trace:", t)
                    
                    ticketsListErrorLiveData.postValue(t.message ?: "Unknown error")
                }

                override fun onNoNetwork() {
                    Log.w("TicketsListAPI", "=== TICKET LIST API - NO NETWORK ===")
                    Log.w("TicketsListAPI", "No network connection available")
                    
                    ticketsListErrorLiveData.postValue("No network connection")
                }
            })
        }
    }

}


