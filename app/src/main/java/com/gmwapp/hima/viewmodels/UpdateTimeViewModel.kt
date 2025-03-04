package com.gmwapp.hima.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class UpdateTimeViewModel : ViewModel() {

    private val _remainingTime = MutableLiveData<String>()
    val remainingTime: LiveData<String> get() = _remainingTime

    fun updateTime(newTime: String) {
        _remainingTime.value = newTime
    }
}
