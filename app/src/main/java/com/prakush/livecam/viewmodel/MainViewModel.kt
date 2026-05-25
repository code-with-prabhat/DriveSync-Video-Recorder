package com.prakush.livecam.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.prakush.livecam.data.MainRepository

class MainViewModel : ViewModel() {
    private val repository = MainRepository()

    private val _text = MutableLiveData<String>().apply {
        value = repository.getData()
    }
    val text: LiveData<String> = _text
}
