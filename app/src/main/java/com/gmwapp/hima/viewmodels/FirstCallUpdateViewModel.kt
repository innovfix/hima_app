package com.gmwapp.hima.viewmodels
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.repositories.FirstCallUpdateRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FirstCallUpdateResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class FirstCallUpdateViewModel @Inject constructor(
    private val repository: FirstCallUpdateRepository
) : ViewModel() {

    val firstCallUpdateLiveData = MutableLiveData<FirstCallUpdateResponse>()
    val firstCallUpdateErrorLiveData = MutableLiveData<String>()

    fun updateFirstCallStatus(userId: Int, callStatus: Int) {
        viewModelScope.launch {
            repository.updateFirstCallStatus(userId, callStatus, object :
                NetworkCallback<FirstCallUpdateResponse> {
                override fun onResponse(
                    call: Call<FirstCallUpdateResponse>,
                    response: Response<FirstCallUpdateResponse>
                ) {
                    firstCallUpdateLiveData.postValue(response.body())
                    Log.d("firstCallUpdateLiveData","${response.body()}")
                }

                override fun onFailure(call: Call<FirstCallUpdateResponse>, t: Throwable) {
                    firstCallUpdateErrorLiveData.postValue(DConstants.LOGIN_ERROR)
                }

                override fun onNoNetwork() {
                    firstCallUpdateErrorLiveData.postValue(DConstants.NO_NETWORK)
                }
            })
        }
    }
}
