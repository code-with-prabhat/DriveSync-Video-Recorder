package com.prakush.livecam.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.prakush.livecam.data.MainRepository
import com.prakush.livecam.data.Video

class MainViewModel : ViewModel() {
    private val repository = MainRepository()

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    var driveService: Drive? = null
    var rootFolderId: String? = null

    fun initializeDriveService(context: Context): Drive? {
        if (driveService != null) return driveService

        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }

            driveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("DriveSync Video Recorder").build()
        }
        return driveService
    }

    fun setVideos(videoList: List<Video>) {
        _videos.value = videoList
        _isLoading.value = false
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    suspend fun fetchSessions() {
        val service = driveService ?: return
        setLoading(true)
        try {
            if (rootFolderId == null) {
                rootFolderId = repository.findRootFolderId(service)
            }
            val sessions = repository.fetchSessions(service, rootFolderId)
            setVideos(sessions.sortedByDescending { it.createdTime })
        } catch (e: Exception) {
            setLoading(false)
            throw e
        }
    }

    suspend fun fetchVideosInFolder(folderId: String) {
        val service = driveService ?: return
        setLoading(true)
        try {
            val videos = repository.fetchVideosInFolder(service, folderId)
            setVideos(videos.sortedBy { it.createdTime })
        } catch (e: Exception) {
            setLoading(false)
            throw e
        }
    }

    suspend fun getOrCreateFolder(folderName: String, parentId: String? = null): String? {
        val service = driveService ?: return null
        return repository.getOrCreateFolder(service, folderName, parentId)
    }

    suspend fun uploadVideo(uri: Uri, folderId: String?, fileName: String, context: Context): String? {
        val service = driveService ?: return null
        return repository.uploadVideo(service, uri, folderId, fileName, context)
    }
}
