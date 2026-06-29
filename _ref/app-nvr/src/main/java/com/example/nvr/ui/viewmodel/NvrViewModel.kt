package com.example.nvr.ui.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nvr.R
import com.example.nvr.model.CameraDevice
import com.example.nvr.model.RecordingFile
import com.example.nvr.ui.DeviceFlowStep
import com.example.nvr.ui.PlaybackRenderMode
import com.example.nvr.ui.PlaybackStreamType
import com.example.nvr.ui.VerificationPreview
import com.example.nvr.ui.buildDeviceManageFormValue
import com.example.nvr.ui.emptyDeviceManageFormValue
import com.example.nvr.ui.queuePlaybackRequest
import com.example.nvr.ui.resolvePlaybackRenderMode
import com.example.nvr.ui.savePlaybackRenderMode
import com.example.nvr.ui.stopPlaybackSession
import com.example.nvr.ui.syncPlayerSurface
import com.example.nvr.ui.state.NvrSettingsState
import com.example.nvr.ui.state.NvrUiState
import com.example.nvr.utils.DatabaseHelper
import com.example.nvr.utils.StorageManager
import com.example.nvr.utils.VideoStreamManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * NVR 主屏幕 ViewModel。
 *
 * 持有原先散落在 NvrMainScreenContent() 里 40+ 个 remember 变量，
 * 将业务逻辑从 Composable 层提升到 ViewModel，使 UI 在配置变更后可恢复状态。
 *
 * ## 获取方式
 * 正常 Activity 场景使用 `viewModel<NvrViewModel>()`；
 * ComboLite 插件场景使用 `NvrViewModel.getInstance(context)` 双保险。
 */
class NvrViewModel(application: Application) : AndroidViewModel(application) {

    init { instance = this }

    /** 供 viewModel() 工厂使用。当宿主不是 ViewModelStoreOwner 时用 getInstance() 兜底。 */
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return getInstance(app) as T
        }
    }

    companion object {
        @Volatile
        private var instance: NvrViewModel? = null

        fun provideFactory(app: Application): ViewModelProvider.Factory = Factory(app)

        /** 当宿主可能不是 ViewModelStoreOwner 时（如 ComboLite 插件），用此兜底。 */
        fun getInstance(app: Application): NvrViewModel {
            return instance ?: synchronized(this) {
                instance ?: NvrViewModel(app).also { instance = it }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  依赖                                                               */
    /* ------------------------------------------------------------------ */

    private val context: Application get() = getApplication()
    val dbHelper: DatabaseHelper = DatabaseHelper(context)
    val storageManager: StorageManager = StorageManager(context)
    val streamManager: VideoStreamManager = VideoStreamManager.getInstance(context)
    val prefs = context.getSharedPreferences("com.example.nvr_preferences", 0)

    val isVlcAvailable: Boolean = runCatching {
        Class.forName("org.videolan.libvlc.util.VLCVideoLayout")
        Class.forName("org.videolan.libvlc.LibVLC")
        true
    }.getOrDefault(false)

    val defaultStorageRoot: String =
        context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath

    /* ------------------------------------------------------------------ */
    /*  UI 状态                                                            */
    /* ------------------------------------------------------------------ */

    private val _cameras = mutableStateListOf<CameraDevice>()
    private val _recordings = mutableStateListOf<RecordingFile>()
    private val _snapshots = mutableStateListOf<RecordingFile>()

    /** 可观察的列表，Compose 自动追踪变更 */
    val cameras: List<CameraDevice> get() = _cameras
    val recordings: List<RecordingFile> get() = _recordings
    val snapshots: List<RecordingFile> get() = _snapshots

    var uiState by mutableStateOf(NvrUiState())
        private set

    /* ------------------------------------------------------------------ */
    /*  便捷属性                                                           */
    /* ------------------------------------------------------------------ */

    private val p get() = uiState.playback
    private val d get() = uiState.deviceForm
    private val s get() = uiState.settings

    /* ------------------------------------------------------------------ */
    /*  初始化                                                              */
    /* ------------------------------------------------------------------ */

    init {
        val saved = prefs.getString("storage_path", null)
        val initialStorage = saved?.takeIf { it.isNotBlank() && it != "/sdcard/NVR" } ?: defaultStorageRoot
        uiState = uiState.copy(
            settings = NvrSettingsState(
                storagePathInput = initialStorage,
                recordingQualityInput = prefs.getString("recording_quality", "1080p") ?: "1080p",
                recordingDurationInput = prefs.getString("recording_duration", "30") ?: "30",
                rtmpUrlInput = prefs.getString("rtmp_push_url", "") ?: "",
                rtmpStreamKeyInput = prefs.getString("rtmp_push_stream_key", "") ?: "",
                rtmpUsernameInput = prefs.getString("rtmp_push_username", "") ?: "",
                rtmpPasswordInput = prefs.getString("rtmp_push_password", "") ?: "",
                rtmpAudioEnabled = prefs.getBoolean("rtmp_push_audio_enabled", true),
                rtmpAutoStreamEnabled = prefs.getBoolean("rtmp_auto_stream", false),
                appearanceThemeMode = prefs.getString("appearance_theme_mode", "light") ?: "light",
            ),
        )
        reloadCameras()
        reloadRecordings()
        reloadSnapshots()
    }

    /* ------------------------------------------------------------------ */
    /*  列表刷新                                                            */
    /* ------------------------------------------------------------------ */

    fun reloadCameras() {
        _cameras.clear()
        _cameras.addAll(dbHelper.getAllCameras())
        if (uiState.selectedCameraIndex >= _cameras.size) {
            uiState = uiState.copy(selectedCameraIndex = 0)
        }
    }

    fun reloadRecordings() {
        _recordings.clear()
        _recordings.addAll(storageManager.getAllRecordings())
    }

    fun reloadSnapshots() {
        _snapshots.clear()
        _snapshots.addAll(storageManager.getAllSnapshots(s.storagePathInput.trim().ifBlank { defaultStorageRoot }))
    }

    /* ------------------------------------------------------------------ */
    /*  播放生命周期                                                        */
    /* ------------------------------------------------------------------ */

    fun clearPlaybackState() {
        if (p.isRecording && p.currentPlayer != null) {
            streamManager.stopRecording(p.currentPlayer!!)
            uiState = uiState.copy(playback = p.copy(isRecording = false))
        }
        stopPlaybackSession(
            cameras = _cameras.toList(),
            currentPlayer = p.currentPlayer,
            playingCameraId = p.playingCameraId,
            streamManager = streamManager,
            onCurrentPlayerChange = { uiState = uiState.copy(playback = p.copy(currentPlayer = it)) },
            onPlayingCameraIdChange = { uiState = uiState.copy(playback = p.copy(playingCameraId = it)) },
            onPendingPlaybackCameraChange = { uiState = uiState.copy(playback = p.copy(pendingPlaybackCamera = it)) },
            onPendingPlaybackRequestIdChange = { uiState = uiState.copy(playback = p.copy(pendingPlaybackRequestId = it)) },
            onVideoLayoutRefChange = { uiState = uiState.copy(playback = p.copy(videoLayoutRef = it)) },
        )
        uiState = uiState.copy(playback = p.copy(adHocPlaybackCamera = null))
    }

    fun queuePlayback(cameraToPlay: CameraDevice) {
        queuePlaybackRequest(
            cameraToPlay = cameraToPlay,
            onLastRequestedPlaybackCameraIdChange = { uiState = uiState.copy(playback = p.copy(lastRequestedPlaybackCameraId = it)) },
            onPendingPlaybackCameraChange = { uiState = uiState.copy(playback = p.copy(pendingPlaybackCamera = it)) },
            onPendingPlaybackRequestIdChange = { uiState = uiState.copy(playback = p.copy(pendingPlaybackRequestId = it)) },
            currentPendingPlaybackRequestId = p.pendingPlaybackRequestId,
        )
    }

    fun rememberLastRequestedPlaybackCamera(cameraId: String) {
        uiState = uiState.copy(playback = p.copy(lastRequestedPlaybackCameraId = cameraId))
    }

    fun queuePendingPlaybackResume(cameraToResume: CameraDevice) {
        uiState = uiState.copy(
            playback = p.copy(
                pendingPlaybackCamera = cameraToResume.copy(isConnected = false),
                pendingPlaybackRequestId = p.pendingPlaybackRequestId + 1,
            ),
        )
    }

    fun clearPendingPlaybackRequest() {
        uiState = uiState.copy(
            playback = p.copy(
                pendingPlaybackCamera = null,
                pendingPlaybackRequestId = 0,
            ),
        )
    }

    fun playAdHocUrl(title: String, url: String) {
        val friendlyName = title.ifBlank { context.getString(R.string.ad_hoc_playback_name) }
        val safeId = "adhoc_" + java.util.UUID.randomUUID().toString()
        val adhoc = CameraDevice(id = safeId, name = friendlyName, rtspUrl = url)
        clearPlaybackState()
        uiState = uiState.copy(
            selectedTab = 0,
            playback = p.copy(
                adHocPlaybackCamera = adhoc,
                tabBeforeFullscreen = uiState.selectedTab,
                isPlaybackFullscreen = true,
            ),
        )
        queuePlayback(adhoc)
    }

    fun startPlayback(
        cameraToPlay: CameraDevice,
        layout: VLCVideoLayout,
        renderMode: PlaybackRenderMode,
        allowFallback: Boolean,
    ) {
        val streamType = PlaybackStreamType.fromUrl(cameraToPlay.rtspUrl)
        val playbackTimeoutMs = 3000L

        clearPlaybackState()
        cameraToPlay.isConnected = false

        val player = streamManager.startStream(
            camera = cameraToPlay,
            videoLayout = layout,
            useTextureView = renderMode.useTextureView,
            enableHardwareDecoder = renderMode.enableHardwareDecoder,
        )
        uiState = uiState.copy(playback = p.copy(pendingPlaybackCamera = null))

        if (player == null) {
            val diagnosis = streamManager.getLastDiagnosticMessage()
            Toast.makeText(context, context.getString(R.string.toast_playback_failed, diagnosis), Toast.LENGTH_LONG).show()
            return
        }

        uiState = uiState.copy(
            playback = p.copy(
                currentPlayer = player,
                playingCameraId = cameraToPlay.id,
                lastRequestedPlaybackCameraId = cameraToPlay.id,
            ),
        )
        player.setVolume(p.lastNonZeroVolume.coerceIn(1, 100))
        syncPlayerSurface(player, layout)
        Toast.makeText(
            context,
            if (allowFallback) context.getString(R.string.toast_playback_started) else context.getString(R.string.toast_playback_retrying_compat),
            Toast.LENGTH_SHORT,
        ).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val currentPlayerNow = uiState.playback.currentPlayer
            if (uiState.playback.playingCameraId != cameraToPlay.id || currentPlayerNow !== player) {
                return@postDelayed
            }
            syncPlayerSurface(player, layout)
            if (cameraToPlay.isConnected) {
                savePlaybackRenderMode(prefs, cameraToPlay.id, renderMode)
                return@postDelayed
            }

            val diagnosis = streamManager.getLastDiagnosticMessage()
            if (allowFallback) {
                val nextRenderMode = when (streamType) {
                    PlaybackStreamType.HTTP, PlaybackStreamType.HTTPS ->
                        if (renderMode == PlaybackRenderMode.SURFACE_SOFTWARE) null else PlaybackRenderMode.SURFACE_SOFTWARE
                    else -> renderMode.fallback()
                }
                if (nextRenderMode != null) {
                    Log.w("NvrViewModel", "首轮播放未建立视频输出，切换兼容模式重试 cameraId=${cameraToPlay.id}, streamType=${streamType?.scheme ?: "unknown"}, diagnosis=$diagnosis, nextMode=${nextRenderMode.name}")
                    startPlayback(cameraToPlay = cameraToPlay, layout = layout, renderMode = nextRenderMode, allowFallback = false)
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_playback_no_video, diagnosis), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.toast_playback_no_video, diagnosis), Toast.LENGTH_LONG).show()
            }
        }, playbackTimeoutMs)
    }

    /* ------------------------------------------------------------------ */
    /*  录制                                                               */
    /* ------------------------------------------------------------------ */

    fun toggleRecording() {
        val player = p.currentPlayer
        if (player == null) {
            Toast.makeText(context, context.getString(R.string.toast_no_active_playback), Toast.LENGTH_SHORT).show()
            return
        }
        if (p.isRecording) {
            val stopped = streamManager.stopRecording(player)
            uiState = uiState.copy(playback = p.copy(isRecording = false))
            if (stopped) {
                Toast.makeText(context, context.getString(R.string.stop_recording), Toast.LENGTH_SHORT).show()
            }
        } else {
            val targetId = p.playingCameraId ?: p.adHocPlaybackCamera?.id ?: p.lastRequestedPlaybackCameraId
            val camera = cameras.firstOrNull { it.id == targetId } ?: p.adHocPlaybackCamera
            val cameraName = camera?.name?.ifBlank { camera.id } ?: context.getString(R.string.unknown_camera_name)
            val baseDir = s.storagePathInput.trim().ifBlank { defaultStorageRoot }
            val recordDir = File(baseDir, "recordings/$cameraName")
            val started = streamManager.startRecording(player, recordDir)
            if (started) {
                uiState = uiState.copy(playback = p.copy(isRecording = true))
                Toast.makeText(context, context.getString(R.string.start_recording), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, streamManager.getLastDiagnosticMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  设备管理                                                            */
    /* ------------------------------------------------------------------ */

    fun loadSelectedDeviceToForm(device: CameraDevice?) {
        val formValue = if (device == null) emptyDeviceManageFormValue()
            else buildDeviceManageFormValue(device, prefs)
        uiState = uiState.copy(
            deviceForm = d.copy(
                selectedDeviceId = formValue.selectedDeviceId,
                manageName = formValue.manageName,
                manageVendor = formValue.manageVendor,
                manageProfile = formValue.manageProfile,
                manageStreamScheme = formValue.manageStreamScheme,
                manageStreamPath = formValue.manageStreamPath,
                manageHost = formValue.manageHost,
                managePort = formValue.managePort,
                manageLoginEnabled = formValue.manageLoginEnabled,
                manageUsername = formValue.manageUsername,
                managePassword = formValue.managePassword,
                managePasswordVisible = formValue.managePasswordVisible,
                forceHttpSoftwareDecode = formValue.forceHttpSoftwareDecode,
            ),
        )
    }

    fun openAddLanding() {
        loadSelectedDeviceToForm(null)
        uiState = uiState.copy(
            deviceForm = d.copy(
                verificationResult = null,
                deviceFlowStep = DeviceFlowStep.Landing,
            ),
            selectedTab = 5,
        )
    }

    fun openEditForm(device: CameraDevice?) {
        loadSelectedDeviceToForm(device)
        uiState = uiState.copy(
            deviceForm = d.copy(
                verificationResult = null,
                deviceFlowStep = DeviceFlowStep.Form,
            ),
            selectedTab = 5,
        )
    }

    fun duplicateCamera(device: CameraDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            val copied = device.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${device.name} 副本",
            )
            val ok = dbHelper.addCamera(copied)
            if (ok) {
                com.example.nvr.ui.copyCameraPreferences(
                    prefs = prefs,
                    sourceCameraId = device.id,
                    targetCameraId = copied.id,
                    fallbackVendor = d.manageVendor,
                    fallbackProfile = d.manageProfile,
                    fallbackLoginEnabled = d.manageLoginEnabled,
                    fallbackUsername = d.manageUsername,
                    fallbackPassword = d.managePassword,
                )
                reloadCameras()
                val idx = _cameras.indexOfFirst { it.id == copied.id }
                if (idx >= 0) uiState = uiState.copy(selectedCameraIndex = idx)
            }
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.toast_camera_copied else R.string.toast_camera_copy_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun deleteCamera(device: CameraDevice) {
        val isPlayingCurrentDevice = p.playingCameraId == device.id
        if (isPlayingCurrentDevice) {
            clearPlaybackState()
            uiState = uiState.copy(
                playback = p.copy(
                    lastRequestedPlaybackCameraId = null,
                    isPlaybackFullscreen = false,
                ),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = dbHelper.deleteCamera(device.id)
            if (ok) {
                com.example.nvr.ui.clearCameraPreferences(prefs, device.id)
                reloadCameras()
            }
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.toast_camera_deleted else R.string.toast_camera_delete_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
        loadSelectedDeviceToForm(_cameras.firstOrNull { it.id != device.id })
    }

    /* ------------------------------------------------------------------ */
    /*  简单状态写入（供 UI 回调直接调用）                                    */
    /* ------------------------------------------------------------------ */

    fun onSelectedTabChange(tab: Int) {
        uiState = uiState.copy(selectedTab = tab)
    }

    fun onLongPressedCameraIdChange(id: String?) {
        uiState = uiState.copy(longPressedCameraId = id)
    }

    fun onSelectedCameraIndexChange(index: Int) {
        uiState = uiState.copy(selectedCameraIndex = index)
    }

    fun onVideoLayoutRefChange(ref: VLCVideoLayout?) {
        uiState = uiState.copy(playback = p.copy(videoLayoutRef = ref))
    }

    fun onPlaybackFullscreenChange(fullscreen: Boolean) {
        uiState = uiState.copy(playback = p.copy(isPlaybackFullscreen = fullscreen))
    }

    fun onCurrentPlayerPlay() {
        p.currentPlayer?.setVolume(p.lastNonZeroVolume.coerceIn(1, 100))
        p.currentPlayer?.play()
    }

    fun onCurrentPlayerPause() {
        p.currentPlayer?.pause()
    }

    fun onCurrentPlayerSetVolume(volume: Int) {
        p.currentPlayer?.setVolume(volume)
    }

    fun onLastNonZeroVolumeChange(volume: Int) {
        uiState = uiState.copy(playback = p.copy(lastNonZeroVolume = volume))
    }

    /* 设备表单字段 */
    fun onManageNameChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(manageName = v)) }
    fun onManageStreamSchemeChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(manageStreamScheme = v)) }
    fun onManageHostChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(manageHost = v)) }
    fun onManagePortChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(managePort = v)) }
    fun onManageStreamPathChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(manageStreamPath = v)) }
    fun onManageLoginEnabledChange(v: Boolean) { uiState = uiState.copy(deviceForm = d.copy(manageLoginEnabled = v)) }
    fun onManageUsernameChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(manageUsername = v)) }
    fun onManagePasswordChange(v: String) { uiState = uiState.copy(deviceForm = d.copy(managePassword = v)) }
    fun onManagePasswordVisibleChange(v: Boolean) { uiState = uiState.copy(deviceForm = d.copy(managePasswordVisible = v)) }
    fun onForceHttpSoftwareDecodeChange(v: Boolean) { uiState = uiState.copy(deviceForm = d.copy(forceHttpSoftwareDecode = v)) }
    fun onVerificationResultChange(v: VerificationPreview?) { uiState = uiState.copy(deviceForm = d.copy(verificationResult = v)) }
    fun onDeviceFlowStepChange(v: DeviceFlowStep) { uiState = uiState.copy(deviceForm = d.copy(deviceFlowStep = v)) }

    /* 设置字段 — 每次改动自动持久化 */
    fun onStoragePathChange(v: String) {
        uiState = uiState.copy(settings = s.copy(storagePathInput = v))
        prefs.edit().putString("storage_path", v.trim()).apply()
    }
    fun onRecordingQualityChange(v: String) {
        uiState = uiState.copy(settings = s.copy(recordingQualityInput = v))
        prefs.edit().putString("recording_quality", v.trim()).apply()
    }
    fun onRecordingDurationChange(v: String) {
        uiState = uiState.copy(settings = s.copy(recordingDurationInput = v))
        prefs.edit().putString("recording_duration", v.trim()).apply()
    }
    fun onRtmpUrlChange(v: String) {
        uiState = uiState.copy(settings = s.copy(rtmpUrlInput = v))
        prefs.edit().putString("rtmp_push_url", v.trim()).apply()
    }
    fun onRtmpStreamKeyChange(v: String) {
        uiState = uiState.copy(settings = s.copy(rtmpStreamKeyInput = v))
        prefs.edit().putString("rtmp_push_stream_key", v.trim()).apply()
    }
    fun onRtmpUsernameChange(v: String) {
        uiState = uiState.copy(settings = s.copy(rtmpUsernameInput = v))
        prefs.edit().putString("rtmp_push_username", v.trim()).apply()
    }
    fun onRtmpPasswordChange(v: String) {
        uiState = uiState.copy(settings = s.copy(rtmpPasswordInput = v))
        prefs.edit().putString("rtmp_push_password", v).apply()
    }
    fun onRtmpAudioEnabledChange(v: Boolean) {
        uiState = uiState.copy(settings = s.copy(rtmpAudioEnabled = v))
        prefs.edit().putBoolean("rtmp_push_audio_enabled", v).apply()
    }
    fun onRtmpAutoStreamChange(v: Boolean) {
        uiState = uiState.copy(settings = s.copy(rtmpAutoStreamEnabled = v))
        prefs.edit().putBoolean("rtmp_auto_stream", v).apply()
    }
    fun onAppearanceThemeModeChange(v: String) { uiState = uiState.copy(settings = s.copy(appearanceThemeMode = v)) }

    /* 对话框 */
    fun onCurrentRecordingDialogChange(file: RecordingFile?) { uiState = uiState.copy(currentRecordingDialog = file) }
    fun onPendingDeleteSnapshotChange(file: RecordingFile?) { uiState = uiState.copy(pendingDeleteSnapshot = file) }

    /* ------------------------------------------------------------------ */
    /*  生命周期清理                                                        */
    /* ------------------------------------------------------------------ */

    override fun onCleared() {
        super.onCleared()
        clearPlaybackState()
        streamManager.shutdown()
        instance = null
    }
}
