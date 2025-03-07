package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmwapp.hima.repositories.WalletRepositories
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.CoinsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject


@HiltViewModel
class WalletViewModel @Inject constructor(private val walletRepositories: WalletRepositories) : ViewModel() {

    val coinsLiveData = MutableLiveData<CoinsResponse>()
    fun getCoins(userId: Int) {
        viewModelScope.launch {
            walletRepositories.getCoins(userId, object:NetworkCallback<CoinsResponse> {
                override fun onResponse(
                    call: Call<CoinsResponse>,
                    response: Response<CoinsResponse>
                ) {
                    coinsLiveData.postValue(response.body());
                }

                override fun onFailure(call: Call<CoinsResponse>, t: Throwable) {
                    Log.d("Walletfaliure","${t.message}")

                }

                override fun onNoNetwork() {
                }
            })
        }
    }
}

