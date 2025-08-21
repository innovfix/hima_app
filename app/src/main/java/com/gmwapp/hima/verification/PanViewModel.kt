package com.gmwapp.hima.verification

// PanViewModel.kt
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import retrofit2.Response

class PanViewModel(private val repository: PanRepository) : ViewModel() {

    private val _response = MutableLiveData<Response<PanResponse>>()
    val response: LiveData<Response<PanResponse>> = _response

    fun verifyPan(pan: String, name: String) {
        viewModelScope.launch {
            try {
                val result = repository.verifyPan(pan, name)
                Log.d("panResult","$result")
                _response.value = result
            } catch (e: Exception) {
                Log.d("panResult","$e")

                e.printStackTrace()
            }
        }
    }
}

class PanViewModelFactory(private val repository: PanRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PanViewModel(repository) as T
    }
}
