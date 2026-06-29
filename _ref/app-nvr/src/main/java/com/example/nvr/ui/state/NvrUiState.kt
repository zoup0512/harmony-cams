package com.example.nvr.ui.state

import com.example.nvr.model.CameraDevice
import com.example.nvr.model.RecordingFile
import com.example.nvr.ui.DeviceFlowStep
import com.example.nvr.ui.VerificationPreview
import com.example.nvr.ui.PlaybackStreamType
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * 播放相关状态
 */
data class NvrPlaybackState(
    val currentPlayer: MediaPlayer? = null,
    val playingCameraId: String? = null,
    val lastRequestedPlaybackCameraId: String? = null,
    val isPlaybackFullscreen: Boolean = false,
    val videoLayoutRef: VLCVideoLayout? = null,
    val pendingPlaybackCamera: CameraDevice? = null,
    val pendingPlaybackRequestId: Int = 0,
    val lastNonZeroVolume: Int = 100,
    val isRecording: Boolean = false,
    /** 临时播放（来自浏览页），不进 cameras 列表 */
    val adHocPlaybackCamera: CameraDevice? = null,
    val tabBeforeFullscreen: Int = 0,
)

/**
 * 设备管理表单状态
 */
data class NvrDeviceFormState(
    val selectedDeviceId: String? = null,
    val manageName: String = "",
    val manageVendor: String = "",
    val manageProfile: String = "",
    val manageStreamScheme: String = PlaybackStreamType.RTSP.scheme,
    val manageStreamPath: String = "stream1",
    val manageHost: String = "",
    val managePort: String = "",
    val manageLoginEnabled: Boolean = true,
    val manageUsername: String = "",
    val managePassword: String = "",
    val managePasswordVisible: Boolean = false,
    val forceHttpSoftwareDecode: Boolean = false,
    val deviceFlowStep: DeviceFlowStep = DeviceFlowStep.Landing,
    val verificationResult: VerificationPreview? = null,
)

/**
 * 设置页状态
 */
data class NvrSettingsState(
    val storagePathInput: String = "",
    val recordingQualityInput: String = "1080p",
    val recordingDurationInput: String = "30",
    val rtmpUrlInput: String = "",
    val rtmpStreamKeyInput: String = "",
    val rtmpUsernameInput: String = "",
    val rtmpPasswordInput: String = "",
    val rtmpAudioEnabled: Boolean = true,
    val rtmpAutoStreamEnabled: Boolean = false,
    val appearanceThemeMode: String = "light",
)

/**
 * 聚合所有 NVR UI 状态（不含列表）。
 * cameras / recordings / snapshots 由 NvrViewModel 以 mutableStateListAdapter 直接暴露，
 * 这样 Compose 可以追踪列表元素级别的变更，copy() 也不会丢失可观察性。
 */
data class NvrUiState(
    val selectedTab: Int = 0,
    val selectedCameraIndex: Int = 0,
    val longPressedCameraId: String? = null,
    val currentRecordingDialog: RecordingFile? = null,
    val pendingDeleteSnapshot: RecordingFile? = null,
    val playback: NvrPlaybackState = NvrPlaybackState(),
    val deviceForm: NvrDeviceFormState = NvrDeviceFormState(),
    val settings: NvrSettingsState = NvrSettingsState(),
)
