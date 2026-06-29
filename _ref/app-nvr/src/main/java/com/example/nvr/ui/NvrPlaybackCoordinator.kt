package com.example.nvr.ui

import android.util.Log
import com.example.nvr.model.CameraDevice
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

fun syncPlayerSurface(player: MediaPlayer?, layout: VLCVideoLayout?) {
    if (player == null || layout == null) return
    layout.post {
        runCatching {
            player.setVideoTrackEnabled(true)
            player.updateVideoSurfaces()
        }.onFailure {
            Log.e("NvrMainScreen", "同步播放器画面失败: ${it.message}")
        }
    }
}

fun queuePlaybackRequest(
    cameraToPlay: CameraDevice,
    onLastRequestedPlaybackCameraIdChange: (String?) -> Unit,
    onPendingPlaybackCameraChange: (CameraDevice?) -> Unit,
    onPendingPlaybackRequestIdChange: (Int) -> Unit,
    currentPendingPlaybackRequestId: Int,
) {
    onLastRequestedPlaybackCameraIdChange(cameraToPlay.id)
    onPendingPlaybackCameraChange(cameraToPlay.copy(isConnected = false))
    onPendingPlaybackRequestIdChange(currentPendingPlaybackRequestId + 1)
}

fun resolvePlaybackRenderMode(
    prefs: android.content.SharedPreferences,
    camera: CameraDevice,
): PlaybackRenderMode {
    val storedMode = loadPlaybackRenderMode(prefs, camera.id)
    if (prefs.getBoolean("camera_force_http_software_decode_${camera.id}", false)) {
        val scheme = PlaybackStreamType.fromUrl(camera.rtspUrl)
        if (scheme == PlaybackStreamType.HTTP || scheme == PlaybackStreamType.HTTPS) {
            return PlaybackRenderMode.SURFACE_SOFTWARE
        }
    }
    return storedMode ?: defaultPlaybackRenderMode()
}
