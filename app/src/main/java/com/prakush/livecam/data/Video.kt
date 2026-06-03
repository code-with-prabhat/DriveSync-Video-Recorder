package com.prakush.livecam.data

import android.os.Parcel
import android.os.Parcelable

data class Video(
    val id: String,
    val name: String,
    val mimeType: String,
    val thumbnailUri: String? = null,
    val createdTime: String? = null,
    val isFolder: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(mimeType)
        parcel.writeString(thumbnailUri)
        parcel.writeString(createdTime)
        parcel.writeByte(if (isFolder) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Video> {
        override fun createFromParcel(parcel: Parcel): Video = Video(parcel)
        override fun newArray(size: Int): Array<Video?> = arrayOfNulls(size)
    }
}
