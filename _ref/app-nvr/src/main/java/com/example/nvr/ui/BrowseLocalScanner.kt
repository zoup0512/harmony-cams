package com.example.nvr.ui

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 通过 MediaStore.Video 扫描机内（含外置 SD 卡）所有视频文件。
 * 不递归使用 File API；让 MediaStore 充当索引。
 */
private const val TAG = "BrowseLocalScanner"

fun scanLocalVideos(context: Context): List<BrowseLocalVideo> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.MIME_TYPE,
    )

    val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
    val results = mutableListOf<BrowseLocalVideo>()

    runCatching {
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol).orEmpty()
                val data = cursor.getString(dataCol).orEmpty()
                val size = cursor.getLong(sizeCol)
                val dur = cursor.getLong(durCol)
                val mime = cursor.getString(mimeCol).orEmpty()
                if (data.isBlank()) continue
                val parent = runCatching { File(data).parentFile?.name }.getOrNull().orEmpty()
                results += BrowseLocalVideo(
                    id = id,
                    displayName = name.ifBlank { File(data).name },
                    absolutePath = data,
                    sizeBytes = size,
                    durationMs = dur,
                    mimeType = mime,
                    folder = parent.ifBlank { "未分组" },
                )
            }
        }
    }.onFailure { Log.e(TAG, "scanLocalVideos failed: ${it.message}") }

    Log.d(TAG, "scanLocalVideos found ${results.size} items")
    return results
}

fun groupLocalVideosByFolder(videos: List<BrowseLocalVideo>): Map<String, List<BrowseLocalVideo>> {
    return videos.groupBy { it.folder }
}

fun fileUrlForLocalVideo(video: BrowseLocalVideo): String {
    return "file://" + video.absolutePath
}
