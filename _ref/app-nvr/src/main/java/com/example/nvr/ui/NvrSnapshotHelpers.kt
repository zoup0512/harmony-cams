package com.example.nvr.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.example.nvr.R
import com.example.nvr.model.CameraDevice
import com.example.nvr.utils.VideoStreamManager
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SnapshotBitmapAnalysis(
    val sampleCount: Int,
    val uniqueColorCount: Int,
    val averageLuma: Int,
    val lumaVariance: Int,
    val isLikelySolid: Boolean,
    val isLikelyBlack: Boolean,
    val isLikelyWhite: Boolean,
)

fun savePlaybackSnapshot(
    context: Context,
    targetView: View,
    storageRootPath: String,
    cameraName: String,
    onSuccess: (File) -> Unit,
    onError: (String) -> Unit,
    allowBlankFrame: Boolean = false,
) {
    Log.d(
        "NvrSnapshot",
        "snapshot requested camera=$cameraName root=$storageRootPath target=${targetView.javaClass.simpleName} size=${targetView.width}x${targetView.height} childCount=${(targetView as? ViewGroup)?.childCount ?: 0}"
    )
    val outputFile = runCatching {
        val dateFolder = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timestamp = SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(Date())
        val safeCameraName = snapshotCameraKey(cameraName)
        val snapshotDir = File(File(storageRootPath, "snapshot"), dateFolder)
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs()
        }
        File(snapshotDir, "${safeCameraName}_${timestamp}.png")
    }.getOrElse {
        onError(context.getString(R.string.toast_playback_snapshot_failed_save))
        return
    }

    val textureView = targetView.findTextureView()
    if (textureView != null) {
        Log.d(
            "NvrSnapshot",
            "texture candidate view=${textureView.javaClass.simpleName} size=${textureView.width}x${textureView.height} isAvailable=${textureView.isAvailable} alpha=${textureView.alpha}"
        )
        val bitmap = textureView.bitmap
        if (bitmap == null) {
            Log.w("NvrSnapshot", "texture bitmap is null")
        } else {
            Log.d("NvrSnapshot", "texture bitmap acquired size=${bitmap.width}x${bitmap.height}")
        }
        val textureMinSize = if (allowBlankFrame) 64L else 1024L
        if (bitmap != null && saveBitmapToFile(bitmap, outputFile, "texture", textureView.width, textureView.height, textureMinSize)) {
            Log.d("NvrSnapshot", "snapshot success via texture path file=${outputFile.absolutePath} size=${outputFile.length()}")
            onSuccess(outputFile)
            return
        }
        Log.w("NvrSnapshot", "texture path failed, continue to surface fallback")
    } else {
        Log.w("NvrSnapshot", "no TextureView found under target")
    }

    val surfaceView = targetView.findSurfaceView()
    val activity = targetView.context.findActivityCompat()
    if (surfaceView != null && surfaceView.width > 0 && surfaceView.height > 0) {
        Log.d(
            "NvrSnapshot",
            "surface candidate view=${surfaceView.javaClass.simpleName} size=${surfaceView.width}x${surfaceView.height} alpha=${surfaceView.alpha} activity=${activity?.javaClass?.simpleName ?: "null"}"
        )
        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        Log.d("NvrSnapshot", "pixelCopy request source=SurfaceView directCopy=true")
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result: Int ->
                Log.d("NvrSnapshot", "pixelCopy result=$result(${pixelCopyResultName(result)}) bitmap=${bitmap.width}x${bitmap.height}")
                if (result == PixelCopy.SUCCESS) {
                    val analysis = bitmap.analyzeSnapshotContent()
                    Log.d(
                        "NvrSnapshot",
                        "bitmap analysis source=surface sampled=${analysis.sampleCount} unique=${analysis.uniqueColorCount} avgLuma=${analysis.averageLuma} variance=${analysis.lumaVariance} solid=${analysis.isLikelySolid} black=${analysis.isLikelyBlack} white=${analysis.isLikelyWhite}"
                    )
                    val acceptFrame = allowBlankFrame || !analysis.isLikelySolid
                    val surfaceMinSize = if (allowBlankFrame) 64L else 1024L
                    if (acceptFrame && saveBitmapToFile(bitmap, outputFile, "surface", surfaceView.width, surfaceView.height, surfaceMinSize)) {
                        Log.d("NvrSnapshot", "snapshot success via surface path file=${outputFile.absolutePath} size=${outputFile.length()} allowBlank=$allowBlankFrame solid=${analysis.isLikelySolid}")
                        onSuccess(outputFile)
                    } else {
                        Log.e("NvrSnapshot", "surface path produced likely blank frame file=${outputFile.absolutePath} size=${outputFile.takeIf { it.exists() }?.length() ?: -1} allowBlank=$allowBlankFrame")
                        onError(context.getString(R.string.toast_playback_snapshot_blank_frame))
                    }
                } else {
                    Log.e("NvrSnapshot", "surface path failed file=${outputFile.absolutePath} size=${outputFile.takeIf { it.exists() }?.length() ?: -1}")
                    onError(context.getString(R.string.toast_playback_snapshot_not_started))
                }
            },
            Handler(Looper.getMainLooper()),
        )
        return
    }

    Log.e(
        "NvrSnapshot",
        "no valid snapshot path. surface=${surfaceView?.javaClass?.simpleName ?: "null"} surfaceSize=${surfaceView?.width ?: -1}x${surfaceView?.height ?: -1} activity=${activity?.javaClass?.simpleName ?: "null"}"
    )

    onError(context.getString(R.string.toast_playback_snapshot_not_started))
}

fun snapshotCameraKey(cameraName: String): String {
    return cameraName.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5_-]"), "_")
}

fun stopPlaybackSession(
    cameras: List<CameraDevice>,
    currentPlayer: MediaPlayer?,
    playingCameraId: String?,
    streamManager: VideoStreamManager,
    onCurrentPlayerChange: (MediaPlayer?) -> Unit,
    onPlayingCameraIdChange: (String?) -> Unit,
    onPendingPlaybackCameraChange: (CameraDevice?) -> Unit,
    onPendingPlaybackRequestIdChange: (Int) -> Unit,
    onVideoLayoutRefChange: (VLCVideoLayout?) -> Unit,
) {
    val playingCamera = cameras.firstOrNull { it.id == playingCameraId }
    streamManager.stopStream(currentPlayer, playingCamera)
    onCurrentPlayerChange(null)
    onPlayingCameraIdChange(null)
    onPendingPlaybackCameraChange(null)
    onPendingPlaybackRequestIdChange(0)
    // 注意：这里不要把 videoLayoutRef 置为 null。
    // VLCVideoLayout 是由 AndroidView 托管的，它在 composition 结束时会自然释放。
    // 如果在每次播放重启前将其置 null，会让 onBindPlayerLayout 回调把 videoLayoutRef
    // 重新设置并再次触发 queuePlayback，形成无限的 start/stop 循环
    // （症状：HTTP-FLV 不停缓冲、只有画面没有声音）。
    @Suppress("UNUSED_PARAMETER")
    onVideoLayoutRefChange
}

fun View.findTextureView(): TextureView? {
    if (this is TextureView) return this
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val textureView = child.findTextureView()
            if (textureView != null) {
                return textureView
            }
        }
    }
    return null
}

fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView) return this
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val surfaceView = child.findSurfaceView()
            if (surfaceView != null) {
                return surfaceView
            }
        }
    }
    return null
}

fun saveBitmapToFile(bitmap: Bitmap, outputFile: File): Boolean {
    return saveBitmapToFile(bitmap, outputFile, "unknown", bitmap.width, bitmap.height)
}

fun saveBitmapToFile(
    bitmap: Bitmap,
    outputFile: File,
    source: String,
    viewWidth: Int,
    viewHeight: Int,
    minFileSizeBytes: Long = 1024L,
): Boolean {
    return runCatching {
        FileOutputStream(outputFile).use { output ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
            val fileLength = outputFile.takeIf { it.exists() }?.length() ?: -1L
            Log.d(
                "NvrSnapshot",
                "saveBitmapToFile source=$source view=${viewWidth}x${viewHeight} bitmap=${bitmap.width}x${bitmap.height} compressed=$compressed file=${outputFile.absolutePath} size=$fileLength minSize=$minFileSizeBytes"
            )
            compressed && outputFile.exists() && outputFile.length() >= minFileSizeBytes
        }
    }.onFailure {
        Log.e("NvrSnapshot", "saveBitmapToFile exception source=$source msg=${it.message}", it)
    }.getOrDefault(false)
}

fun pixelCopyResultName(result: Int): String {
    return when (result) {
        PixelCopy.SUCCESS -> "SUCCESS"
        PixelCopy.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
        PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
        PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA"
        PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
        PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
        else -> "UNKNOWN_$result"
    }
}

fun Bitmap.analyzeSnapshotContent(): SnapshotBitmapAnalysis {
    if (width <= 0 || height <= 0) {
        return SnapshotBitmapAnalysis(0, 0, 0, 0, true, true, false)
    }
    val stepX = (width / 12).coerceAtLeast(1)
    val stepY = (height / 12).coerceAtLeast(1)
    val colors = LinkedHashSet<Int>()
    var totalLuma = 0L
    var totalLumaSquare = 0L
    var samples = 0
    for (y in 0 until height step stepY) {
        for (x in 0 until width step stepX) {
            val pixel = getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            val luma = ((r * 299) + (g * 587) + (b * 114)) / 1000
            colors += pixel
            totalLuma += luma
            totalLumaSquare += luma.toLong() * luma.toLong()
            samples++
        }
    }
    val averageLuma = if (samples == 0) 0 else (totalLuma / samples).toInt()
    val variance = if (samples == 0) {
        0
    } else {
        ((totalLumaSquare / samples) - averageLuma.toLong() * averageLuma.toLong()).coerceAtLeast(0L).toInt()
    }
    val uniqueColorCount = colors.size
    val likelyBlack = averageLuma < 16 && uniqueColorCount <= 3 && variance < 12
    val likelyWhite = averageLuma > 239 && uniqueColorCount <= 3 && variance < 12
    val likelySolid = uniqueColorCount <= 4 && variance < 18
    return SnapshotBitmapAnalysis(
        sampleCount = samples,
        uniqueColorCount = uniqueColorCount,
        averageLuma = averageLuma,
        lumaVariance = variance,
        isLikelySolid = likelySolid,
        isLikelyBlack = likelyBlack,
        isLikelyWhite = likelyWhite,
    )
}
