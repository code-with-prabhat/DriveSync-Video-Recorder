package com.prakush.livecam.data

data class Video(
    val id: String,
    val name: String,
    val mimeType: String,
    val thumbnailUri: String? = null,
    val createdTime: String? = null,
    val isFolder: Boolean = false
)
