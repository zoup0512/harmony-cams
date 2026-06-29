package com.example.nvr.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraDevice(
    var id: String = "",
    var name: String = "",
    var rtspUrl: String = "",
    var isRecording: Boolean = false,
    var isConnected: Boolean = false,
) : Parcelable
