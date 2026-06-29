package com.example.nvr.ui

/**
 * "浏览" 功能用到的轻量模型。
 * 故意与 CameraDevice / RecordingFile 隔开，避免污染既有摄像机管理逻辑。
 */

/** 用户收藏的可播放项（既可以是本地文件 file://，也可以是远端 URL）。 */
data class BrowseFavorite(
    val id: String,
    val title: String,
    val url: String,
    val source: BrowseSourceKind,
    val addedAtMs: Long,
)

/** 用户保存的网络源（RTSP / HTTP / RTMP / SMB 等远端 URL）。 */
data class BrowseNetworkSource(
    val id: String,
    val title: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val addedAtMs: Long,
)

/** 通过 MediaStore 扫到的本机视频。 */
data class BrowseLocalVideo(
    val id: Long,
    val displayName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val mimeType: String,
    val folder: String,
)

enum class BrowseSourceKind {
    LOCAL_FILE,
    NETWORK_URL,
}
