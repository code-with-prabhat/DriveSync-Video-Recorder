package com.prakush.livecam.data

import android.content.Context
import android.net.Uri
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainRepository {

    suspend fun fetchSessions(service: Drive, rootFolderId: String?): List<Video> = withContext(Dispatchers.IO) {
        if (rootFolderId == null) return@withContext emptyList()

        val sessionQuery = "'$rootFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result = service.files().list()
            .setQ(sessionQuery)
            .setSpaces("drive")
            .setFields("files(id, name, mimeType, createdTime)")
            .execute()

        result.files?.map { file ->
            Video(
                id = file.id,
                name = file.name,
                mimeType = file.mimeType ?: "application/vnd.google-apps.folder",
                createdTime = file.createdTime?.toString(),
                isFolder = true
            )
        } ?: emptyList()
    }

    suspend fun fetchVideosInFolder(service: Drive, folderId: String): List<Video> = withContext(Dispatchers.IO) {
        val fileQuery = "'$folderId' in parents and mimeType = 'video/mp4' and trashed = false"
        val result = service.files().list()
            .setQ(fileQuery)
            .setSpaces("drive")
            .setFields("files(id, name, mimeType, createdTime)")
            .execute()

        result.files?.map { file ->
            Video(
                id = file.id,
                name = file.name,
                mimeType = file.mimeType ?: "video/mp4",
                createdTime = file.createdTime?.toString(),
                isFolder = false
            )
        } ?: emptyList()
    }

    suspend fun getOrCreateFolder(service: Drive, folderName: String, parentId: String? = null): String? = withContext(Dispatchers.IO) {
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false" +
                if (parentId != null) " and '$parentId' in parents" else ""

        val result = service.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val folder = result.files?.firstOrNull()
        if (folder != null) {
            folder.id
        } else {
            val fileMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != null) {
                    parents = listOf(parentId)
                }
            }
            val newFolder = service.files().create(fileMetadata)
                .setFields("id")
                .execute()
            newFolder.id
        }
    }

    suspend fun findRootFolderId(service: Drive): String? = withContext(Dispatchers.IO) {
        val folderQuery = "name = 'LiveCam_Recordings' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val folderResult = service.files().list()
            .setQ(folderQuery)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        folderResult.files?.firstOrNull()?.id
    }

    suspend fun uploadVideo(
        service: Drive,
        uri: Uri,
        folderId: String?,
        fileName: String,
        context: Context
    ): String? = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val mediaContent = InputStreamContent("video/mp4", inputStream)

        val fileMetadata = File().apply {
            name = fileName
            folderId?.let {
                parents = listOf(it)
            }
        }

        val file = service.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
        file.id
    }

    suspend fun deleteFile(service: Drive, fileId: String) = withContext(Dispatchers.IO) {
        service.files().delete(fileId).execute()
    }
}
