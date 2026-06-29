package com.example.nvr.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Date

@Parcelize
data class RecordingFile(
    var id: String = "",
    var fileName: String = "",
    var filePath: String = "",
    var fileSize: Long = 0L,
    var startTime: Date? = null,
    var endTime: Date? = null,
    var cameraId: String = "",
    var cameraName: String = "",
) : Parcelable {

    val readableFileSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(fileSize.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format("%.2f %s", fileSize / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    val durationSeconds: Long
        get() = if (startTime == null || endTime == null) {
            0
        } else {
            (endTime!!.time - startTime!!.time) / 1000
        }

    val readableDuration: String
        get() {
            val duration = durationSeconds
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    fun deleteFile(): Boolean {
        if (filePath.isEmpty()) return false
        val file = File(filePath)
        return file.exists() && file.delete()
    }
}
