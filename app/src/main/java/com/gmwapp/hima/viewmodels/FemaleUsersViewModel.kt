package com.gmwapp.hima.viewmodels

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.FemaleUsersRepositories
import com.gmwapp.hima.repositories.TransactionsRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CallFemaleUserResponse
import com.gmwapp.hima.retrofit.responses.CallMaleUserResponse
import com.gmwapp.hima.retrofit.responses.FemaleCallAttendResponse
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponse
import com.gmwapp.hima.retrofit.responses.RandomUsersResponse
import com.gmwapp.hima.retrofit.responses.ReportsResponse
import com.gmwapp.hima.retrofit.responses.GetFemaleTalkDurationResponse
import com.gmwapp.hima.retrofit.responses.TransactionsResponse
import com.gmwapp.hima.retrofit.responses.UpdateCallStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
//import im.zego.uikit.libuikitreport.CommonUtils.getApplication
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@HiltViewModel
class FemaleUsersViewModel @Inject constructor(private val femaleUsersRepositories: FemaleUsersRepositories) : ViewModel() {

    val femaleUsersResponseLiveData = MutableLiveData<FemaleUsersResponse>()
    val femaleUsersErrorLiveData = MutableLiveData<String>()

    val randomUsersResponseLiveData = MutableLiveData<RandomUsersResponse>()
    val randomUsersErrorLiveData = MutableLiveData<String>()

    val updateCallStatusResponseLiveData = MutableLiveData<UpdateCallStatusResponse>()
    val updateCallStatusErrorLiveData = MutableLiveData<String>()

    val femaleCallAttendResponseLiveData = MutableLiveData<FemaleCallAttendResponse>()
    val femaleCallAttendErrorLiveData = MutableLiveData<String>()

    val callFemaleUserResponseLiveData = MutableLiveData<CallFemaleUserResponse>()
    val callFemaleUserErrorLiveData = MutableLiveData<String>()

    val callMaleUserResponseLiveData = MutableLiveData<CallMaleUserResponse>()
    val callMaleUserErrorLiveData = MutableLiveData<String>()

    val reportResponseLiveData = MutableLiveData<ReportsResponse>()
    val reportsErrorLiveData = MutableLiveData<String>()

    val femaleTalkDurationResponseLiveData = MutableLiveData<GetFemaleTalkDurationResponse>()
    val femaleTalkDurationErrorLiveData = MutableLiveData<String>()

    fun getFemaleUsers(userId: Int, filter: String? = null) {
        viewModelScope.launch {
            femaleUsersRepositories.getFemaleUsers(userId, filter, object:NetworkCallback<FemaleUsersResponse> {
                override fun onResponse(
                    call: Call<FemaleUsersResponse>,
                    response: Response<FemaleUsersResponse>
                ) {
                    femaleUsersResponseLiveData.postValue(response.body());
                    Log.d("checkResponse","${response.body()}")
                }

                override fun onFailure(call: Call<FemaleUsersResponse>, t: Throwable) {
                    femaleUsersErrorLiveData.postValue(DConstants.LOGIN_ERROR);
                }

                override fun onNoNetwork() {
                    femaleUsersErrorLiveData.postValue(DConstants.NO_NETWORK);
                }
            })
        }
    }

    fun getRandomUser(userId: Int, callType: String) {
        viewModelScope.launch {
            femaleUsersRepositories.getRandomUser(userId,callType, object:NetworkCallback<RandomUsersResponse> {
                override fun onResponse(
                    call: Call<RandomUsersResponse>,
                    response: Response<RandomUsersResponse>
                ) {
                    randomUsersResponseLiveData.postValue(response.body());
                    Log.d("checkRandomResponse","${response.body()}")

                }

                override fun onFailure(call: Call<RandomUsersResponse>, t: Throwable) {
                    randomUsersErrorLiveData.postValue(DConstants.LOGIN_ERROR);
                }

                override fun onNoNetwork() {
                    randomUsersErrorLiveData.postValue(DConstants.NO_NETWORK);
                }
            })
        }
    }

    fun getReports(userId: Int) {
        viewModelScope.launch {
            femaleUsersRepositories.getCalldetailsUser(userId, object:NetworkCallback<ReportsResponse> {
                override fun onResponse(
                    call: Call<ReportsResponse>,
                    response: Response<ReportsResponse>
                ) {
                    reportResponseLiveData.postValue(response.body());
                }

                override fun onNoNetwork() {
                    reportsErrorLiveData.postValue(DConstants.NO_NETWORK);
                }

                override fun onFailure(call: Call<ReportsResponse>, t: Throwable) {
                    reportsErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                   // Toast.makeText(getApplication(), "Something went wrong: ${t.message}", Toast.LENGTH_SHORT).show()
                    Log.e("API_FAILURE", "Error: ", t)
                }

            })
        }
    }

    fun getFemaleTalkDuration(userId: Int) {
        Log.d("FemaleUsersViewModel", "🚀 getFemaleTalkDuration called for user $userId")
        viewModelScope.launch {
            Log.d("FemaleUsersViewModel", "📞 Calling repository.getFemaleTalkDuration for user $userId")
            femaleUsersRepositories.getFemaleTalkDuration(userId, object: NetworkCallback<GetFemaleTalkDurationResponse> {
                override fun onResponse(
                    call: Call<GetFemaleTalkDurationResponse>,
                    response: Response<GetFemaleTalkDurationResponse>
                ) {
                    Log.d("FemaleUsersViewModel", "✅ getFemaleTalkDuration response received: success=${response.body()?.success}, code=${response.code()}")
                    Log.d("FemaleUsersViewModel", "📊 Response body: ${response.body()}")
                    femaleTalkDurationResponseLiveData.postValue(response.body())
                }

                override fun onNoNetwork() {
                    Log.e("FemaleUsersViewModel", "❌ No network for getFemaleTalkDuration")
                    femaleTalkDurationErrorLiveData.postValue(DConstants.NO_NETWORK)
                }

                override fun onFailure(call: Call<GetFemaleTalkDurationResponse>, t: Throwable) {
                    Log.e("FemaleUsersViewModel", "❌ Failure getting talk duration: ${t.message}", t)
                    femaleTalkDurationErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }
            })
        }
    }

    fun updateCallStatus(userId: Int, callType:String, status:Int) {
        viewModelScope.launch {
            femaleUsersRepositories.updateCallStatus(userId,callType,status, object:NetworkCallback<UpdateCallStatusResponse> {
                override fun onResponse(
                    call: Call<UpdateCallStatusResponse>,
                    response: Response<UpdateCallStatusResponse>
                ) {
                    updateCallStatusResponseLiveData.postValue(response.body());
                }

                override fun onFailure(call: Call<UpdateCallStatusResponse>, t: Throwable) {
                    updateCallStatusErrorLiveData.postValue(DConstants.LOGIN_ERROR);
                }

                override fun onNoNetwork() {
                    updateCallStatusErrorLiveData.postValue(DConstants.NO_NETWORK);
                }
            })
        }
    }

   fun femaleCallAttend(userId: Int, callId: Int,
                        startTime: String,callback: NetworkCallback<FemaleCallAttendResponse>) {
        viewModelScope.launch {
            Log.d("femaleCallAttend","femaleCallAttend")

            femaleUsersRepositories.femaleCallAttend(userId,callId,startTime, callback)
        }
    }

   fun callFemaleUser(userId: Int, callUserId: Int,
                      callType: String,call_switch:Int) {
        viewModelScope.launch {
            femaleUsersRepositories.callFemaleUser(userId,callUserId,callType,call_switch, object:NetworkCallback<CallFemaleUserResponse> {
                override fun onResponse(
                    call: Call<CallFemaleUserResponse>,
                    response: Response<CallFemaleUserResponse>
                ) {
                    callFemaleUserResponseLiveData.postValue(response.body());
                    Log.d("callFemaleUserResponseLiveData","${response.body()}")
                }

                override fun onFailure(call: Call<CallFemaleUserResponse>, t: Throwable) {
                    callFemaleUserErrorLiveData.postValue(DConstants.LOGIN_ERROR);
                    Log.d("callFemaleUserResponseLiveData","${t.message}")

                }

                override fun onNoNetwork() {
                    callFemaleUserErrorLiveData.postValue(DConstants.NO_NETWORK);
                }
            })
        }
    }

    fun callMaleUser(userId: Int, callUserId: Int,
                     callType: String, call_switch: Int) {
        viewModelScope.launch {
            femaleUsersRepositories.callMaleUser(userId, callUserId, callType, call_switch, object:NetworkCallback<CallMaleUserResponse> {
                override fun onResponse(
                    call: Call<CallMaleUserResponse>,
                    response: Response<CallMaleUserResponse>
                ) {
                    callMaleUserResponseLiveData.postValue(response.body());
                    Log.d("callMaleUserResponseLiveData","${response.body()}")
                }

                override fun onFailure(call: Call<CallMaleUserResponse>, t: Throwable) {
                    callMaleUserErrorLiveData.postValue(DConstants.LOGIN_ERROR);
                }

                override fun onNoNetwork() {
                    callMaleUserErrorLiveData.postValue(DConstants.NO_NETWORK);
                }
            })
        }
    }

}

