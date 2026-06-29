package com.example.nvr.utils

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.nvr.model.CameraDevice
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class VideoStreamManager private constructor(private val context: Context) {

    @Volatile
    private var libVLC: LibVLC? = null
    private val mediaPlayers = mutableListOf<MediaPlayer>()
    // 跟踪哪些 MediaPlayer 已经产生过首帧 Vout，UI 侧可以据此即时隐藏 loading。
    private val firstVoutPlayers: MutableSet<MediaPlayer> =
        Collections.newSetFromMap(IdentityHashMap<MediaPlayer, Boolean>())

    // 录制中的 MediaPlayer -> 录像输出目录，用于停止时清理状态。
    // VLC record(directory) 内部会把流落到该目录，文件名由 VLC 自动生成。
    private val recordingPlayers: MutableMap<MediaPlayer, String> =
        Collections.synchronizedMap(IdentityHashMap())

    private val isInitialized = AtomicBoolean(false)
    private val isShuttingDown = AtomicBoolean(false)
    private val initializationThread = AtomicReference<Thread?>(null)
    private val initLatch = CountDownLatch(1)
    @Volatile
    private var lastDiagnosticMessage: String = "未开始播放"

    private data class StreamBufferingPolicy(
        val enabled: Boolean,
        val startupResumePercent: Float = 0f,
        val lowWatermarkPercent: Float = 0f,
        val resumePercent: Float = 0f,
        val startupTimeoutMs: Long = 0L,
        val streamCachingMs: Int = 0,
        val minPauseIntervalMs: Long = 0L,
        val minResumeIntervalMs: Long = 0L,
    )

    private data class StreamBufferingState(
        var startupBufferingCompleted: Boolean,
        var autoPausedForBuffering: Boolean = false,
        var bufferingPauseActive: Boolean = false,
        var lastPauseAtMs: Long = 0L,
        var lastResumeAtMs: Long = 0L,
        val startAtMs: Long = SystemClock.elapsedRealtime(),
    )

    private data class PlaybackSessionMetrics(
        val sessionStartAtMs: Long = SystemClock.elapsedRealtime(),
        var openingAtMs: Long = 0L,
        var firstPlayingAtMs: Long = 0L,
        var firstVoutAtMs: Long = 0L,
        var startupCompletedAtMs: Long = 0L,
        var autoPauseCount: Int = 0,
        var autoResumeCount: Int = 0,
        var lastBufferingPercent: Float = 0f,
        var lastBufferingEventAtMs: Long = 0L,
        var rebufferStartAtMs: Long = 0L,
        var totalRebufferDurationMs: Long = 0L,
    )

    init {
        initializeLibVLC()
    }

    private fun initializeLibVLC() {
        if (initializationThread.get() != null || isShuttingDown.get()) {
            Log.d(TAG, "LibVLC initialization already in progress or shutting down")
            return
        }

        val thread = Thread {
            try {
                initializationThread.set(Thread.currentThread())

                // 对齐 _ref/vlc-android VLCOptions.libOptions：保持选项最小化，
                // 让 VLC 自己处理网络抖动与 rebuffer，不在外层做 pause/play 干预。
                val options = arrayListOf(
                    "--audio-resampler",
                    "soxr",
                    "--network-caching=$DEFAULT_NETWORK_CACHING_MS",
                    "--stats",
                    "-vv",
                )
                libVLC = LibVLC(context, options)
                isInitialized.set(true)
                Log.d(TAG, "LibVLC instance created successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create LibVLC instance: ${e.message}")
                isInitialized.set(false)
            } finally {
                initializationThread.set(null)
                initLatch.countDown()
            }
        }

        thread.priority = Thread.MAX_PRIORITY
        thread.start()
    }

    fun isInitialized(): Boolean = isInitialized.get() && libVLC != null

    fun reinitialize(): Boolean {
        if (!isInitialized() && !isShuttingDown.get()) {
            Log.d(TAG, "Attempting to reinitialize LibVLC")

            initializationThread.get()?.let { initThread ->
                if (initThread.isAlive) {
                    try {
                        Log.d(TAG, "Waiting for existing initialization to complete")
                        initThread.join(2000)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Interrupted while waiting for initialization: ${e.message}")
                        Thread.currentThread().interrupt()
                    }
                }
            }

            if (!isInitialized()) {
                initializeLibVLC()
                try {
                    initLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        return isInitialized()
    }

    fun startStream(
        camera: CameraDevice,
        videoLayout: VLCVideoLayout?,
        useTextureView: Boolean = true,
        enableHardwareDecoder: Boolean = true,
    ): MediaPlayer? {
        lastDiagnosticMessage = "开始连接: ${camera.rtspUrl}"
        if (camera.rtspUrl.isEmpty()) {
            Log.e(TAG, "Camera or RTSP URL is null or empty")
            lastDiagnosticMessage = "RTSP URL 为空"
            return null
        }
        val urlValidation = validatePlaybackUrl(camera.rtspUrl)
        val isHttpPlayback = urlValidation.streamType == PlaybackUrlType.HTTP || urlValidation.streamType == PlaybackUrlType.HTTPS
        val bufferingPolicy = resolveBufferingPolicy(urlValidation.streamType)
        val bufferingState = StreamBufferingState(
            startupBufferingCompleted = !bufferingPolicy.enabled,
        )
        val playbackMetrics = PlaybackSessionMetrics()
        Log.d(
            TAG,
            "startStream url校验 cameraId=${camera.id}, ${urlValidation.toDebugString()}"
        )
        if (!urlValidation.canOpen) {
            Log.e(TAG, "RTSP URL 校验失败: ${urlValidation.message}")
            lastDiagnosticMessage = urlValidation.message
            return null
        }
        if (isShuttingDown.get()) {
            Log.e(TAG, "Cannot start stream - VideoStreamManager is shutting down")
            lastDiagnosticMessage = "播放器正在关闭"
            return null
        }

        Log.d(
            TAG,
            "准备开始流播放 - 摄像头ID: ${camera.id}, URL: ${sanitizeRtspUrl(camera.rtspUrl)}, texture=$useTextureView, hw=$enableHardwareDecoder"
        )

        val lib = libVLC ?: run {
            if (!reinitialize()) {
                Log.e(TAG, "LibVLC is not initialized and cannot be reinitialized, cannot start stream")
                lastDiagnosticMessage = "LibVLC 初始化失败"
                return null
            }
            libVLC ?: return null
        }

        if (videoLayout == null) {
            Log.e(TAG, "VLCVideoLayout is null, cannot attach views")
            lastDiagnosticMessage = "视频视图为空"
            return null
        }
        if (videoLayout.windowToken == null) {
            Log.e(TAG, "VLCVideoLayout is not attached to a window, cannot start stream")
            lastDiagnosticMessage = "视频视图未附着窗口"
            return null
        }

        val mediaPlayer = MediaPlayer(lib)
        try {
            try {
                mediaPlayer.attachViews(videoLayout, null, false, useTextureView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach views: ${e.message}")
                lastDiagnosticMessage = "绑定视频视图失败: ${e.message}"
                mediaPlayer.release()
                return null
            }

            // 不再强制 setAudioOutputDevice("pcm")。vlc-android 通过 setAudioOutput("opensles"/"audiotrack")
            // 选择音频输出 *模块*；setAudioOutputDevice 是选择具体 PCM 设备，
            // 在 HTTP 流上强制传 "pcm" 会导致部分设备静音。
            if (isHttpPlayback) {
                Log.d(TAG, "HTTP 流，使用 VLC 默认音频输出")
            }

            val media = createPlaybackMedia(
                lib,
                camera.rtspUrl,
                enableHardwareDecoder = enableHardwareDecoder,
            )
            mediaPlayer.media = media
            media.release()

            mediaPlayer.setEventListener(object : MediaPlayer.EventListener {
                private var retryCount = 0
                private val maxRetries = 3

                override fun onEvent(event: MediaPlayer.Event) {
                    try {
                        when (event.type) {
                            MediaPlayer.Event.EncounteredError -> {
                                val errorVolume = runCatching { mediaPlayer.volume }.getOrNull()
                                Log.e(
                                    TAG,
                                    "Error encountered while playing stream cameraId=${camera.id}, url=${sanitizeRtspUrl(camera.rtspUrl)}, " +
                                        "retry=$retryCount/$maxRetries, state=${getMediaPlayerState(mediaPlayer)}, " +
                                        "isPlaying=${mediaPlayer.isPlaying}, volume=${errorVolume ?: -1}, " +
                                        "lastDiagnostic=$lastDiagnosticMessage"
                                )
                                lastDiagnosticMessage = "播放报错(可能URL/账号密码/编码不兼容)"
                                camera.isConnected = false

                                if (retryCount < maxRetries && !isShuttingDown.get()) {
                                    retryCount++
                                    Log.d(TAG, "尝试重新连接 ($retryCount/$maxRetries)...")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        try {
                                            if (libVLC != null && !isShuttingDown.get()) {
                                                val newMedia = createPlaybackMedia(
                                                    libVLC!!,
                                                    camera.rtspUrl,
                                                    enableHardwareDecoder = enableHardwareDecoder,
                                                )
                                                mediaPlayer.media = newMedia
                                                newMedia.release()
                                                mediaPlayer.setVideoTrackEnabled(true)
                                                mediaPlayer.play()
                                            }
                                        } catch (e: Throwable) {
                                            Log.e(TAG, "重试连接失败: ${e.message}")
                                        }
                                    }, 2000)
                                } else {
                                    Log.e(TAG, "所有重试均已失败，建议检查RTSP URL、网络连接和摄像头配置")
                                }
                            }

                            MediaPlayer.Event.Buffering -> {
                                // 对齐 _ref/vlc-android：Buffering 事件只做日志/诊断，
                                // 绝不在此处调用 mediaPlayer.pause()/play()，避免覆盖用户暂停意图，
                                // 也避免 HTTP 流在 rebuffer 时陷入 pause/play 循环。
                                val bufferingPercent = event.buffering.coerceIn(0f, 100f)
                                val bufferingVolume = runCatching { mediaPlayer.volume }.getOrNull()
                                lastDiagnosticMessage = "缓冲中 ${bufferingPercent.toInt()}%"
                                playbackMetrics.lastBufferingPercent = bufferingPercent
                                playbackMetrics.lastBufferingEventAtMs = SystemClock.elapsedRealtime()
                                Log.d(
                                    TAG,
                                    "Buffering cameraId=${camera.id}, url=${sanitizeRtspUrl(camera.rtspUrl)}, " +
                                        "buffering=${bufferingPercent}%, state=${getMediaPlayerState(mediaPlayer)}, " +
                                        "isPlaying=${mediaPlayer.isPlaying}, time=${mediaPlayer.time}, position=${mediaPlayer.position}, " +
                                        "rate=${mediaPlayer.rate}, volume=${bufferingVolume ?: -1}, " +
                                        "bufferingPause=${bufferingState.bufferingPauseActive}, autoPaused=${bufferingState.autoPausedForBuffering}, " +
                                        "autoPauseCount=${playbackMetrics.autoPauseCount}, autoResumeCount=${playbackMetrics.autoResumeCount}, " +
                                        "rebufferMs=${playbackMetrics.totalRebufferDurationMs}"
                                )
                            }
                            MediaPlayer.Event.TimeChanged -> {
                                Log.d(TAG, "Time changed: ${mediaPlayer.time}")
                            }
                            MediaPlayer.Event.PositionChanged -> {
                                Log.d(TAG, "Position changed: ${mediaPlayer.position}")
                            }
                            MediaPlayer.Event.Vout -> {
                                Log.d(TAG, "Video output event count=${event.voutCount}")
                                if (event.voutCount > 0) {
                                    if (playbackMetrics.firstVoutAtMs == 0L) {
                                        playbackMetrics.firstVoutAtMs = SystemClock.elapsedRealtime()
                                        logPlaybackMilestone(
                                            camera = camera,
                                            stage = "FIRST_VOUT",
                                            mediaPlayer = mediaPlayer,
                                            bufferingState = bufferingState,
                                            playbackMetrics = playbackMetrics,
                                        )
                                    }
                                    synchronized(firstVoutPlayers) { firstVoutPlayers.add(mediaPlayer) }
                                    camera.isConnected = true
                                    lastDiagnosticMessage = "视频输出已建立"
                                }
                            }
                            MediaPlayer.Event.ESAdded -> {
                                Log.d(TAG, "Elementary stream added")
                            }
                            MediaPlayer.Event.ESDeleted -> {
                                Log.d(TAG, "Elementary stream deleted")
                            }
                            MediaPlayer.Event.SeekableChanged -> {
                                Log.d(TAG, "Seekable changed: ${mediaPlayer.isSeekable}")
                            }
                            MediaPlayer.Event.PausableChanged -> {
                                Log.d(TAG, "Pausable changed event")
                            }
                            MediaPlayer.Event.Opening ->
                                run {
                                    if (playbackMetrics.openingAtMs == 0L) {
                                        playbackMetrics.openingAtMs = SystemClock.elapsedRealtime()
                                    }
                                    Log.d(TAG, "Stream is opening")
                                    lastDiagnosticMessage = "正在打开流..."
                                }
                            MediaPlayer.Event.Playing -> {
                                if (playbackMetrics.firstPlayingAtMs == 0L) {
                                    playbackMetrics.firstPlayingAtMs = SystemClock.elapsedRealtime()
                                    logPlaybackMilestone(
                                        camera = camera,
                                        stage = "FIRST_PLAYING",
                                        mediaPlayer = mediaPlayer,
                                        bufferingState = bufferingState,
                                        playbackMetrics = playbackMetrics,
                                    )
                                }
                                Log.d(TAG, "Stream is now playing")
                                if (bufferingPolicy.enabled && !bufferingState.startupBufferingCompleted) {
                                    logPlaybackMilestone(
                                        camera = camera,
                                        stage = "PLAYING_BEFORE_STARTUP_READY",
                                        mediaPlayer = mediaPlayer,
                                        bufferingState = bufferingState,
                                        playbackMetrics = playbackMetrics,
                                    )
                                    lastDiagnosticMessage = "收到播放事件，继续预缓冲"
                                } else if (bufferingPolicy.enabled && bufferingState.autoPausedForBuffering) {
                                    bufferingState.autoPausedForBuffering = false
                                    bufferingState.bufferingPauseActive = false
                                    bufferingState.lastResumeAtMs = SystemClock.elapsedRealtime()
                                    lastDiagnosticMessage = "缓冲恢复，继续播放"
                                } else {
                                    lastDiagnosticMessage = "流已开始播放，等待视频输出"
                                }
                            }
                            MediaPlayer.Event.Paused ->
                                Log.d(TAG, "Stream is paused")
                            MediaPlayer.Event.Stopped -> {
                                Log.d(TAG, "Stream is stopped")
                                logPlaybackMilestone(
                                    camera = camera,
                                    stage = "STOPPED",
                                    mediaPlayer = mediaPlayer,
                                    bufferingState = bufferingState,
                                    playbackMetrics = playbackMetrics,
                                )
                                camera.isConnected = false
                                lastDiagnosticMessage = "已停止"
                            }
                            MediaPlayer.Event.EndReached -> {
                                Log.d(TAG, "End of stream reached")
                                logPlaybackMilestone(
                                    camera = camera,
                                    stage = "END_REACHED",
                                    mediaPlayer = mediaPlayer,
                                    bufferingState = bufferingState,
                                    playbackMetrics = playbackMetrics,
                                )
                                camera.isConnected = false
                                lastDiagnosticMessage = "流已结束"
                            }
                            else -> Unit
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error handling MediaPlayer event: ${e.message}")
                    }
                }
            })

            mediaPlayer.play()
            lastDiagnosticMessage = "已发起播放"
            synchronized(mediaPlayers) { mediaPlayers += mediaPlayer }
            Log.d(TAG, "Stream started successfully for camera: ${camera.name}")
            Handler(Looper.getMainLooper()).post {
                syncVideoOutput(mediaPlayer, videoLayout)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    syncVideoOutput(mediaPlayer, videoLayout)
                    Log.d(
                        TAG,
                        "播放阶段诊断 isPlaying=${mediaPlayer.isPlaying}, time=${mediaPlayer.time}, position=${mediaPlayer.position}, rate=${mediaPlayer.rate}, state=${getMediaPlayerState(mediaPlayer)}"
                    )
                }.onFailure {
                    Log.e(TAG, "读取播放阶段诊断失败: ${it.message}")
                }
            }, 3000)
            return mediaPlayer
        } catch (e: Throwable) {
            Log.e(TAG, "Exception while starting stream: ${e.message}")
            lastDiagnosticMessage = "启动异常: ${e.message}"
            try {
                mediaPlayer.release()
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun getMediaPlayerState(player: MediaPlayer): String =
        when {
            player.isPlaying -> "PLAYING"
            player.isSeekable -> "SEEKABLE"
            else -> "IDLE/UNKNOWN"
        }

    private fun resolveBufferingPolicy(streamType: PlaybackUrlType?): StreamBufferingPolicy {
        // 全部禁用外层 pause/resume 缓冲策略：参考 _ref/vlc-android，
        // VLC 自己处理 buffering，外层只读取参数（streamCachingMs）来设置 :network-caching。
        return when (streamType) {
            PlaybackUrlType.HTTP, PlaybackUrlType.HTTPS -> StreamBufferingPolicy(
                enabled = false,
                streamCachingMs = HTTP_STREAM_CACHING_MS,
                startupTimeoutMs = HTTP_STARTUP_TIMEOUT_MS,
            )
            else -> StreamBufferingPolicy(enabled = false)
        }
    }

    private fun syncVideoOutput(
        mediaPlayer: MediaPlayer,
        videoLayout: VLCVideoLayout,
    ) {
        runCatching {
            if (videoLayout.width > 0 && videoLayout.height > 0) {
                mediaPlayer.getVLCVout().setWindowSize(videoLayout.width, videoLayout.height)
            }
            mediaPlayer.setVideoTrackEnabled(true)
            mediaPlayer.updateVideoSurfaces()
        }.onFailure {
            Log.e(TAG, "同步视频输出失败: ${it.message}")
        }
    }

    private fun logPlaybackMilestone(
        camera: CameraDevice,
        stage: String,
        mediaPlayer: MediaPlayer,
        bufferingState: StreamBufferingState,
        playbackMetrics: PlaybackSessionMetrics,
    ) {
        val now = SystemClock.elapsedRealtime()
        val sessionElapsed = now - playbackMetrics.sessionStartAtMs
        val firstPlayingCost = playbackMetrics.firstPlayingAtMs.takeIf { it > 0L }?.minus(playbackMetrics.sessionStartAtMs) ?: -1L
        val firstVoutCost = playbackMetrics.firstVoutAtMs.takeIf { it > 0L }?.minus(playbackMetrics.sessionStartAtMs) ?: -1L
        val startupCost = playbackMetrics.startupCompletedAtMs.takeIf { it > 0L }?.minus(playbackMetrics.sessionStartAtMs) ?: -1L
        val activeRebufferMs = if (playbackMetrics.rebufferStartAtMs > 0L) now - playbackMetrics.rebufferStartAtMs else 0L
        Log.d(
            TAG,
            "PlaybackMilestone stage=$stage, cameraId=${camera.id}, url=${sanitizeRtspUrl(camera.rtspUrl)}, " +
                "elapsedMs=$sessionElapsed, firstPlayingMs=$firstPlayingCost, firstVoutMs=$firstVoutCost, startupMs=$startupCost, " +
                "autoPauseCount=${playbackMetrics.autoPauseCount}, autoResumeCount=${playbackMetrics.autoResumeCount}, " +
                "buffering=${playbackMetrics.lastBufferingPercent}, totalRebufferMs=${playbackMetrics.totalRebufferDurationMs}, " +
                "activeRebufferMs=$activeRebufferMs, bufferingPause=${bufferingState.bufferingPauseActive}, " +
                "autoPaused=${bufferingState.autoPausedForBuffering}, playerState=${getMediaPlayerState(mediaPlayer)}, " +
                "isPlaying=${mediaPlayer.isPlaying}, time=${mediaPlayer.time}, position=${mediaPlayer.position}, rate=${mediaPlayer.rate}"
        )
    }

    fun hasVideoOutput(mediaPlayer: MediaPlayer?): Boolean {
        if (mediaPlayer == null) return false
        return synchronized(firstVoutPlayers) { firstVoutPlayers.contains(mediaPlayer) }
    }

    fun stopStream(mediaPlayer: MediaPlayer?, camera: CameraDevice?) {
        if (mediaPlayer == null) return
        synchronized(mediaPlayers) { mediaPlayers.remove(mediaPlayer) }
        synchronized(firstVoutPlayers) { firstVoutPlayers.remove(mediaPlayer) }
        camera?.isConnected = false
        lastDiagnosticMessage = "正在停止"
        Thread {
            try {
                runCatching {
                    mediaPlayer.stop()
                }.onFailure {
                    Log.e(TAG, "Error stopping media player: ${it.message}")
                }
                // 先释放 media 资源，避免 VLCObject finalize 错误
                runCatching {
                    val media = mediaPlayer.media
                    if (media != null) {
                        mediaPlayer.media = null
                        media.release()
                    }
                }.onFailure {
                    Log.e(TAG, "Error releasing media: ${it.message}")
                }
                runCatching {
                    Handler(Looper.getMainLooper()).post {
                        runCatching {
                            mediaPlayer.detachViews()
                        }.onFailure {
                            Log.e(TAG, "Error detaching views: ${it.message}")
                        }
                        runCatching {
                            mediaPlayer.release()
                        }.onFailure {
                            Log.e(TAG, "Error releasing media player: ${it.message}")
                        }
                    }
                }.onFailure {
                    Log.e(TAG, "Error posting detach/release to main thread: ${it.message}")
                }
                lastDiagnosticMessage = "已手动停止"
                Log.d(TAG, "Stream stopped and resources released")
            } catch (e: Throwable) {
                Log.e(TAG, "Error while stopping stream: ${e.message}")
                lastDiagnosticMessage = "停止异常: ${e.message}"
            }
        }.start()
    }

    fun getLastDiagnosticMessage(): String = lastDiagnosticMessage

    fun testRtspTcpReachability(rtspUrl: String, callback: (Boolean, String) -> Unit) {
        if (rtspUrl.isBlank()) {
            callback(false, "播放 URL 为空")
            return
        }
        Thread {
            try {
                val validation = validatePlaybackUrl(rtspUrl)
                if (!validation.canOpen) {
                    callback(false, validation.message)
                    return@Thread
                }
                when (validation.streamType) {
                    PlaybackUrlType.RTSP -> {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(validation.host, validation.port), 5000)
                        }
                        callback(true, "RTSP 网络连通")
                    }
                    PlaybackUrlType.HTTP, PlaybackUrlType.HTTPS -> {
                        val connection = (URL(rtspUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "AI-Project-NVR/1.0")
                            setRequestProperty("Accept", "*/*")
                        }
                        try {
                            val code = connection.responseCode
                            val summary = "HTTP $code ${connection.responseMessage.orEmpty()}".trim()
                            callback(code in 200..399, if (code in 200..399) "HTTP 网络连通: $summary" else "HTTP 连接失败: $summary")
                        } finally {
                            connection.disconnect()
                        }
                    }
                    PlaybackUrlType.FILE -> {
                        // 本地文件不走网络可达性测试。
                        val path = validation.path
                        val exists = path.isNotBlank() && java.io.File(path).exists()
                        callback(exists, if (exists) "本地文件存在" else "本地文件不存在: $path")
                    }
                    null -> {
                        callback(false, validation.message)
                    }
                }
            } catch (e: Throwable) {
                callback(false, "网络不可达: ${e.message}")
            }
        }.start()
    }

    fun testRtspOptionsHandshake(rtspUrl: String, callback: (Boolean, String) -> Unit) {
        if (rtspUrl.isBlank()) {
            callback(false, "播放 URL 为空")
            return
        }
        Thread {
            try {
                val validation = validatePlaybackUrl(rtspUrl)
                if (!validation.canOpen) {
                    callback(false, validation.message)
                    return@Thread
                }
                if (validation.streamType == PlaybackUrlType.RTSP) {
                    Socket().use { socket ->
                        socket.soTimeout = 5000
                        socket.connect(InetSocketAddress(validation.host, validation.port), 5000)
                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                        val request = buildString {
                            append("OPTIONS $rtspUrl RTSP/1.0\r\n")
                            append("CSeq: 1\r\n")
                            append("User-Agent: AI-Project-NVR/1.0\r\n")
                            append("\r\n")
                        }
                        writer.write(request)
                        writer.flush()

                        val responseLines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) {
                                break
                            }
                            responseLines += line
                        }
                        if (responseLines.isEmpty()) {
                            callback(false, "RTSP OPTIONS 未收到响应")
                            return@Thread
                        }
                        val statusLine = responseLines.first()
                        val authHeader = responseLines.firstOrNull { it.startsWith("WWW-Authenticate", ignoreCase = true) }.orEmpty()
                        val publicHeader = responseLines.firstOrNull { it.startsWith("Public", ignoreCase = true) }.orEmpty()
                        val summary = listOf(statusLine, authHeader, publicHeader)
                            .filter { it.isNotBlank() }
                            .joinToString(" | ")
                        Log.d(TAG, "RTSP OPTIONS 响应: $summary")
                        callback(statusLine.contains("200") || statusLine.contains("401"), summary)
                    }
                } else {
                    val connection = (URL(rtspUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "AI-Project-NVR/1.0")
                        setRequestProperty("Accept", "*/*")
                    }
                    try {
                        val code = connection.responseCode
                        val summary = listOf(
                            "HTTP $code ${connection.responseMessage.orEmpty()}".trim(),
                            connection.headerFields.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull().orEmpty(),
                        ).filter { it.isNotBlank() }.joinToString(" | ")
                        Log.d(TAG, "HTTP 流响应: $summary")
                        callback(code in 200..399, summary)
                    } finally {
                        connection.disconnect()
                    }
                }
            } catch (e: Throwable) {
                callback(false, "播放探测失败: ${e.message}")
            }
        }.start()
    }

    private fun createPlaybackMedia(
        lib: LibVLC,
        playbackUrl: String,
        enableHardwareDecoder: Boolean,
    ): Media {
        val validation = validatePlaybackUrl(playbackUrl)
        val bufferingPolicy = resolveBufferingPolicy(validation.streamType)
        Log.d(TAG, "播放 URL: ${sanitizePlaybackUrl(playbackUrl)}")
        Log.d(
            TAG,
            "createPlaybackMedia url校验: ${validation.toDebugString()}, hwDecoder=$enableHardwareDecoder"
        )
        val isHttpPlayback = validation.streamType == PlaybackUrlType.HTTP || validation.streamType == PlaybackUrlType.HTTPS
        val streamCaching = when (validation.streamType) {
            PlaybackUrlType.HTTP, PlaybackUrlType.HTTPS -> bufferingPolicy.streamCachingMs
            PlaybackUrlType.RTSP -> 500
            PlaybackUrlType.FILE -> 300
            null -> 1000
        }
        // 对齐 _ref/vlc-android setMediaOptions：媒体选项尽量精简，
        // 让 VLC 自己处理 HTTP rebuffer/重连。冗余的 :http-reconnect / :reconnect /
        // :clock-jitter / :clock-synchro / :live-caching / :file-caching 反而
        // 在某些摄像头 HTTP-FLV 流上引发画面卡顿与音频静音。
        return Media(lib, Uri.parse(playbackUrl)).apply {
            if (enableHardwareDecoder) {
                // 与 vlc-android HW_ACCELERATION_FULL 行为一致
                setHWDecoderEnabled(true, true)
            } else {
                setHWDecoderEnabled(false, false)
            }
            addOption(":network-caching=$streamCaching")
            if (validation.streamType == PlaybackUrlType.RTSP) {
                addOption(":rtsp-tcp")
            }
            if (!isHttpPlayback) {
                Log.d(TAG, "非 HTTP 流，沿用默认 reconnect 行为")
            }
        }
    }

    /**
     * 开始录制：调用 VLC 原生 record(directory)。
     * VLC 会把当前播放流以 ps 容器落到 [outputDir] 目录，文件名自动生成。
     * 返回 true 表示已成功下达录制指令（不保证最终落盘成功）。
     */
    fun startRecording(mediaPlayer: MediaPlayer, outputDir: java.io.File): Boolean {
        return runCatching {
            if (!outputDir.exists()) outputDir.mkdirs()
            if (!outputDir.isDirectory) {
                lastDiagnosticMessage = "录像目录无效: ${outputDir.absolutePath}"
                Log.e(TAG, "startRecording failed, not a directory: ${outputDir.absolutePath}")
                return false
            }
            val ok = mediaPlayer.record(outputDir.absolutePath)
            if (ok) {
                synchronized(recordingPlayers) { recordingPlayers[mediaPlayer] = outputDir.absolutePath }
                lastDiagnosticMessage = "录制中 -> ${outputDir.absolutePath}"
                Log.d(TAG, "Recording started via VLC record() -> ${outputDir.absolutePath}")
            } else {
                lastDiagnosticMessage = "VLC 拒绝开始录制"
                Log.e(TAG, "VLC record() returned false for ${outputDir.absolutePath}")
            }
            ok
        }.getOrElse {
            lastDiagnosticMessage = "启动录制异常: ${it.message}"
            Log.e(TAG, "startRecording exception: ${it.message}")
            false
        }
    }

    /**
     * 停止录制：调用 VLC record(null)。
     */
    fun stopRecording(mediaPlayer: MediaPlayer): Boolean {
        return runCatching {
            val ok = mediaPlayer.record(null)
            synchronized(recordingPlayers) { recordingPlayers.remove(mediaPlayer) }
            lastDiagnosticMessage = if (ok) "已停止录制" else "停止录制失败"
            Log.d(TAG, "Recording stopped, result=$ok")
            ok
        }.getOrElse {
            Log.e(TAG, "stopRecording exception: ${it.message}")
            false
        }
    }

    fun isRecording(mediaPlayer: MediaPlayer?): Boolean {
        if (mediaPlayer == null) return false
        return synchronized(recordingPlayers) { recordingPlayers.containsKey(mediaPlayer) }
    }

    fun shutdown() {
        if (!isShuttingDown.compareAndSet(false, true)) return

        synchronized(firstVoutPlayers) { firstVoutPlayers.clear() }
        synchronized(recordingPlayers) { recordingPlayers.clear() }
        synchronized(mediaPlayers) {
            mediaPlayers.forEach { player ->
                try {
                    player.stop()
                    // 先释放 media 资源
                    try {
                        val media = player.media
                        if (media != null) {
                            player.media = null
                            media.release()
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error releasing media in shutdown: ${e.message}")
                    }
                    player.detachViews()
                    player.release()
                } catch (e: Throwable) {
                    Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
                }
            }
            mediaPlayers.clear()
        }

        try {
            libVLC?.release()
            libVLC = null
            isInitialized.set(false)
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing LibVLC: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VideoStreamManager"
        // 对齐 _ref/vlc-android：vlc-android 默认不显式覆盖 network-caching，
        // 沿用 VLC 自身的 1000ms 默认值；首屏延迟更接近 vlc-android。
        private const val HTTP_STREAM_CACHING_MS = 1000
        private const val HTTP_STARTUP_TIMEOUT_MS = 15000L
        private const val DEFAULT_NETWORK_CACHING_MS = 1000

        @Volatile
        private var instance: VideoStreamManager? = null

        fun playbackStartupTimeoutMsForUrl(playbackUrl: String): Long {
            return when (PlaybackUrlType.fromScheme(Uri.parse(playbackUrl).scheme.orEmpty())) {
                PlaybackUrlType.HTTP, PlaybackUrlType.HTTPS -> HTTP_STARTUP_TIMEOUT_MS
                else -> 3000L
            }
        }

        fun getInstance(context: Context): VideoStreamManager =
            instance ?: synchronized(this) {
                instance ?: VideoStreamManager(context.applicationContext).also { instance = it }
            }
    }
}

private enum class PlaybackUrlType(
    val label: String,
) {
    RTSP("RTSP"),
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    FILE("FILE");

    companion object {
        fun fromScheme(scheme: String): PlaybackUrlType? {
            return when (scheme.lowercase()) {
                "rtsp" -> RTSP
                "http" -> HTTP
                "https" -> HTTPS
                "file" -> FILE
                else -> null
            }
        }
    }
}

private data class PlaybackUrlValidation(
    val canOpen: Boolean,
    val message: String,
    val sanitizedUrl: String,
    val streamType: PlaybackUrlType?,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val usernamePresent: Boolean,
    val passwordPresent: Boolean,
    val path: String,
)

private fun PlaybackUrlValidation.toDebugString(): String {
    return "canOpen=$canOpen, message=$message, url=$sanitizedUrl, scheme=${streamType?.label ?: "unknown"}, host=$host, port=$port, usernamePresent=$usernamePresent, passwordPresent=$passwordPresent, path=$path"
}

private fun validatePlaybackUrl(rtspUrl: String): PlaybackUrlValidation {
    return runCatching {
        val uri = Uri.parse(rtspUrl)
        val streamType = PlaybackUrlType.fromScheme(uri.scheme.orEmpty())
        val host = uri.host.orEmpty()
        val port = when {
            uri.port > 0 -> uri.port
            streamType == PlaybackUrlType.HTTPS -> 443
            streamType == PlaybackUrlType.HTTP -> 80
            else -> 554
        }
        val userInfo = uri.userInfo.orEmpty()
        val username = if (userInfo.contains(":")) userInfo.substringBefore(":") else userInfo
        val password = if (userInfo.contains(":")) userInfo.substringAfter(":", "") else ""
        val path = uri.path.orEmpty().removePrefix("/")
        when {
            streamType == null -> PlaybackUrlValidation(
                canOpen = false,
                message = "播放 URL scheme 不受支持",
                sanitizedUrl = sanitizePlaybackUrl(rtspUrl),
                streamType = null,
                host = host,
                port = port,
                username = username,
                password = password,
                usernamePresent = username.isNotBlank(),
                passwordPresent = password.isNotBlank(),
                path = path,
            )
            streamType == PlaybackUrlType.FILE -> PlaybackUrlValidation(
                canOpen = path.isNotBlank() || rtspUrl.removePrefix("file://").isNotBlank(),
                message = if (path.isNotBlank() || rtspUrl.removePrefix("file://").isNotBlank()) "FILE 校验通过" else "本地文件路径为空",
                sanitizedUrl = sanitizePlaybackUrl(rtspUrl),
                streamType = streamType,
                host = host,
                port = port,
                username = username,
                password = password,
                usernamePresent = false,
                passwordPresent = false,
                path = path.ifBlank { rtspUrl.removePrefix("file://") },
            )
            host.isBlank() -> PlaybackUrlValidation(
                canOpen = false,
                message = "播放 URL 缺少主机",
                sanitizedUrl = sanitizePlaybackUrl(rtspUrl),
                streamType = streamType,
                host = host,
                port = port,
                username = username,
                password = password,
                usernamePresent = username.isNotBlank(),
                passwordPresent = password.isNotBlank(),
                path = path,
            )
            path.isBlank() -> PlaybackUrlValidation(
                canOpen = false,
                message = "播放 URL 缺少路径",
                sanitizedUrl = sanitizePlaybackUrl(rtspUrl),
                streamType = streamType,
                host = host,
                port = port,
                username = username,
                password = password,
                usernamePresent = username.isNotBlank(),
                passwordPresent = password.isNotBlank(),
                path = path,
            )
            else -> PlaybackUrlValidation(
                canOpen = true,
                message = when (streamType) {
                    PlaybackUrlType.RTSP -> "RTSP 校验通过"
                    PlaybackUrlType.HTTP -> "HTTP 校验通过"
                    PlaybackUrlType.HTTPS -> "HTTPS 校验通过"
                    PlaybackUrlType.FILE -> "FILE 校验通过"
                },
                sanitizedUrl = sanitizePlaybackUrl(rtspUrl),
                streamType = streamType,
                host = host,
                port = port,
                username = username,
                password = password,
                usernamePresent = username.isNotBlank(),
                passwordPresent = password.isNotBlank(),
                path = path,
            )
        }
    }.getOrElse {
        PlaybackUrlValidation(
            canOpen = false,
            message = "播放 URL 解析失败: ${it.message}",
            sanitizedUrl = sanitizePlaybackUrl(rtspUrl),
            streamType = null,
            host = "",
            port = 554,
            username = "",
            password = "",
            usernamePresent = false,
            passwordPresent = false,
            path = "",
        )
    }
}

private fun sanitizePlaybackUrl(rtspUrl: String): String {
    return rtspUrl.replace(Regex("(://)([^:/@]+)(?::([^@/]*))?@")) {
        val username = it.groupValues.getOrNull(2).orEmpty()
        val password = it.groupValues.getOrNull(3).orEmpty()
        val maskedUser = if (username.isNotBlank()) "***" else ""
        val maskedPassword = if (password.isNotBlank()) ":***" else ""
        "${it.groupValues[1]}$maskedUser$maskedPassword@"
    }
}

private fun sanitizeRtspUrl(rtspUrl: String): String = sanitizePlaybackUrl(rtspUrl)


