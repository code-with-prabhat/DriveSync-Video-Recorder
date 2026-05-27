package com.prakush.livecam.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.prakush.livecam.data.MainRepository
import com.prakush.livecam.data.Video

class MainViewModel : ViewModel() {
    private val repository = MainRepository()

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    fun setVideos(videoList: List<Video>) {
        _videos.value = videoList
    }

    private val _text = MutableLiveData<String>().apply {
        value = repository.getData()
    }
    val text: LiveData<String> = _text
}
