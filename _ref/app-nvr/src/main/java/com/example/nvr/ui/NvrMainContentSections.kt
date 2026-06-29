package com.example.nvr.ui

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nvr.R
import com.example.nvr.ui.theme.AppearanceThemeState
import com.example.nvr.ui.theme.applyAppearanceThemeMode
import com.example.nvr.model.CameraDevice
import com.example.nvr.model.RecordingFile
import com.example.nvr.utils.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun NvrMainContentScaffold(
    context: Context,
    selectedTab: Int,
    cameraInAction: CameraDevice?,
    isPlaybackFullscreen: Boolean,
    deviceFlowStep: DeviceFlowStep,
    selectedDeviceId: String?,
    manageName: String,
    selectedCameraForGrid: CameraDevice?,
    cameras: List<CameraDevice>,
    snapshots: List<RecordingFile>,
    recordings: List<RecordingFile>,
    playingCameraId: String?,
    lastRequestedPlaybackCameraId: String?,
    currentPlayer: MediaPlayer?,
    videoLayoutRef: VLCVideoLayout?,
    isVlcAvailable: Boolean,
    prefs: android.content.SharedPreferences,
    storageManager: StorageManager,
    storagePathInput: String,
    recordingQualityInput: String,
    recordingDurationInput: String,
    rtmpUrlInput: String,
    rtmpStreamKeyInput: String,
    rtmpUsernameInput: String,
    rtmpPasswordInput: String,
    rtmpAudioEnabled: Boolean,
    rtmpAutoStreamEnabled: Boolean,
    appearanceThemeMode: String,
    manageVendor: String,
    manageProfile: String,
    manageStreamScheme: String,
    manageHost: String,
    managePort: String,
    manageStreamPath: String,
    manageLoginEnabled: Boolean,
    manageUsername: String,
    managePassword: String,
    managePasswordVisible: Boolean,
    forceHttpSoftwareDecode: Boolean,
    verificationResult: VerificationPreview?,
    lastNonZeroVolume: Int,
    onSelectedTabChange: (Int) -> Unit,
    onLongPressedCameraIdChange: (String?) -> Unit,
    onVerificationResultChange: (VerificationPreview?) -> Unit,
    onDeviceFlowStepChange: (DeviceFlowStep) -> Unit,
    onSelectedCameraIndexChange: (Int) -> Unit,
    onVideoLayoutRefChange: (VLCVideoLayout?) -> Unit,
    onQueuePlayback: (CameraDevice) -> Unit,
    onOpenEditForm: (CameraDevice?) -> Unit,
    onOpenAddLanding: () -> Unit,
    onSeedDemo: () -> Unit,
    onDuplicateCamera: (CameraDevice) -> Unit,
    onDeleteCamera: (CameraDevice) -> Unit,
    onCurrentRecordingDialogChange: (RecordingFile?) -> Unit,
    onPendingDeleteSnapshotChange: (RecordingFile?) -> Unit,
    onCurrentPlayerPlay: () -> Unit,
    onCurrentPlayerPause: () -> Unit,
    onCurrentPlayerSetVolume: (Int) -> Unit,
    onLastNonZeroVolumeChange: (Int) -> Unit,
    onManageNameChange: (String) -> Unit,
    onManageStreamSchemeChange: (String) -> Unit,
    onManageHostChange: (String) -> Unit,
    onManagePortChange: (String) -> Unit,
    onManageStreamPathChange: (String) -> Unit,
    onManageLoginEnabledChange: (Boolean) -> Unit,
    onManageUsernameChange: (String) -> Unit,
    onManagePasswordChange: (String) -> Unit,
    onManagePasswordVisibleChange: (Boolean) -> Unit,
    onForceHttpSoftwareDecodeChange: (Boolean) -> Unit,
    onAppearanceThemeModeChange: (String) -> Unit,
    onStoragePathInputChange: (String) -> Unit,
    onRecordingQualityInputChange: (String) -> Unit,
    onRecordingDurationInputChange: (String) -> Unit,
    onRtmpUrlInputChange: (String) -> Unit,
    onRtmpStreamKeyInputChange: (String) -> Unit,
    onRtmpUsernameInputChange: (String) -> Unit,
    onRtmpPasswordInputChange: (String) -> Unit,
    onRtmpAudioEnabledChange: (Boolean) -> Unit,
    onRtmpAutoStreamChange: (Boolean) -> Unit,
    onClearPlaybackState: () -> Unit,
    onPlaybackFullscreenChange: (Boolean) -> Unit,
    onSyncPlayerSurface: (MediaPlayer?, VLCVideoLayout?) -> Unit,
    onSavePlaybackSnapshot: (Context, VLCVideoLayout, String, String, (java.io.File) -> Unit, (String) -> Unit, Boolean) -> Unit,
    adHocPlaybackCamera: CameraDevice?,
    tabBeforeFullscreen: Int,
    onPlayAdHocUrl: (String, String) -> Unit,
    isVideoReadyProvider: () -> Boolean,
    onReloadCameras: () -> Unit,
    onReloadRecordings: () -> Unit,
    onReloadSnapshots: () -> Unit,
    onLoadSelectedDeviceToForm: (CameraDevice?) -> Unit,
    onSaveVerificationResult: (VerificationPreview?) -> Unit,
    isRecording: Boolean = false,
    onToggleRecord: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val appBackgroundColor = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize().background(appBackgroundColor)) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (cameraInAction != null) Modifier.blur(18.dp) else Modifier),
            containerColor = appBackgroundColor,
            topBar = {
                if (selectedTab == 0 && isPlaybackFullscreen) {
                    Spacer(modifier = Modifier.height(0.dp))
                } else if (selectedTab == 5) {
                    DeviceManageTopBar(
                        title = when (deviceFlowStep) {
                            DeviceFlowStep.Landing -> stringResource(R.string.device_manage_title_add)
                            DeviceFlowStep.Verifying -> stringResource(R.string.device_manage_title_verifying)
                            DeviceFlowStep.Form -> if (selectedDeviceId == null) stringResource(R.string.device_manage_title_new) else manageName.ifBlank { stringResource(R.string.device_manage_title_edit) }
                        },
                        flowStep = deviceFlowStep,
                        isEditingExistingCamera = deviceFlowStep == DeviceFlowStep.Form && selectedDeviceId != null,
                        onCancel = {
                            onSelectedTabChange(0)
                            onLongPressedCameraIdChange(null)
                            onVerificationResultChange(null)
                            onDeviceFlowStepChange(DeviceFlowStep.Landing)
                        },
                        onBack = {
                            onDeviceFlowStepChange(
                                if (selectedDeviceId == null) DeviceFlowStep.Landing else DeviceFlowStep.Form
                            )
                        },
                    )
                } else {
                    NvrMainTopBar(
                        selectedTab = selectedTab,
                        onEditClick = {
                            onReloadCameras()
                            onOpenEditForm(selectedCameraForGrid ?: cameras.firstOrNull())
                        },
                        onMenuClick = {
                            onReloadCameras()
                            onOpenEditForm(selectedCameraForGrid ?: cameras.firstOrNull())
                        },
                        onAddClick = onOpenAddLanding,
                    )
                }
            },
            bottomBar = {
                if (selectedTab != 5 && !(selectedTab == 0 && isPlaybackFullscreen)) {
                    NvrBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { index ->
                            onSelectedTabChange(index)
                            onLongPressedCameraIdChange(null)
                            if (index == 2 || index == 3) {
                                onReloadRecordings()
                                onReloadSnapshots()
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            when (selectedTab) {
                0 -> {
                    val targetId = playingCameraId ?: lastRequestedPlaybackCameraId
                    val fullscreenCamera = adHocPlaybackCamera?.takeIf { it.id == targetId }
                        ?: cameras.firstOrNull { it.id == targetId }
                    var playbackControlPlaying = remember(playingCameraId, isPlaybackFullscreen) {
                        mutableStateOf(currentPlayer?.isPlaying == true)
                    }
                    LaunchedEffect(currentPlayer, isPlaybackFullscreen) {
                        if (!isPlaybackFullscreen || currentPlayer == null) {
                            playbackControlPlaying.value = false
                            return@LaunchedEffect
                        }

                        while (true) {
                            playbackControlPlaying.value = currentPlayer.isPlaying
                            delay(250)
                        }
                    }
                    if (isPlaybackFullscreen && fullscreenCamera != null) {
                        PlaybackFullscreenScreen(
                            camera = fullscreenCamera,
                            badge = cameraBadge(fullscreenCamera, prefs),
                            isPlaying = playbackControlPlaying.value,
                            isMuted = (currentPlayer?.volume ?: -1) == 0,
                            isVideoReadyProvider = isVideoReadyProvider,
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            onBindPlayerLayout = { layout ->
                                if (videoLayoutRef !== layout) {
                                    onVideoLayoutRefChange(layout)
                                    onQueuePlayback(fullscreenCamera)
                                }
                                onSyncPlayerSurface(currentPlayer, layout)
                            },
                            onBack = {
                                val targetLayout = videoLayoutRef
                                val activePlayer = currentPlayer
                                val isAdHoc = adHocPlaybackCamera != null && fullscreenCamera.id == adHocPlaybackCamera.id
                                
                                if (activePlayer != null && targetLayout != null) {
                                    onSavePlaybackSnapshot(
                                        context,
                                        targetLayout,
                                        storagePathInput.trim().ifBlank { context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath },
                                        fullscreenCamera.name.ifBlank { fullscreenCamera.id },
                                        {
                                            onReloadSnapshots()
                                            onClearPlaybackState()
                                            onPlaybackFullscreenChange(false)
                                            // 如果是 ad-hoc 播放，返回到之前的 tab
                                            if (isAdHoc && tabBeforeFullscreen != 0) {
                                                onSelectedTabChange(tabBeforeFullscreen)
                                            }
                                        },
                                        {
                                            // 退出路径上的截图是静默的：失败不打扰用户，只正常收尾。
                                            onClearPlaybackState()
                                            onPlaybackFullscreenChange(false)
                                            // 如果是 ad-hoc 播放，返回到之前的 tab
                                            if (isAdHoc && tabBeforeFullscreen != 0) {
                                                onSelectedTabChange(tabBeforeFullscreen)
                                            }
                                        },
                                        true,
                                    )
                                } else {
                                    onClearPlaybackState()
                                    onPlaybackFullscreenChange(false)
                                    // 如果是 ad-hoc 播放，返回到之前的 tab
                                    if (isAdHoc && tabBeforeFullscreen != 0) {
                                        onSelectedTabChange(tabBeforeFullscreen)
                                    }
                                }
                            },
                            onTogglePlayPause = {
                                val player = currentPlayer
                                if (player == null) {
                                    playbackControlPlaying.value = true
                                    onQueuePlayback(fullscreenCamera)
                                } else if (playbackControlPlaying.value) {
                                    playbackControlPlaying.value = false
                                    onCurrentPlayerPause()
                                } else {
                                    playbackControlPlaying.value = true
                                    onCurrentPlayerPlay()
                                    onSyncPlayerSurface(player, videoLayoutRef)
                                }
                            },
                            onMute = {
                                val player = currentPlayer
                                if (player == null) {
                                    Toast.makeText(context, context.getString(R.string.toast_no_active_playback), Toast.LENGTH_SHORT).show()
                                } else {
                                    val currentVolume = player.volume
                                    if (currentVolume > 0) {
                                        onLastNonZeroVolumeChange(currentVolume)
                                        onCurrentPlayerSetVolume(0)
                                        Toast.makeText(context, context.getString(R.string.toast_muted), Toast.LENGTH_SHORT).show()
                                    } else {
                                        onCurrentPlayerSetVolume(lastNonZeroVolume.coerceAtLeast(1))
                                        Toast.makeText(context, context.getString(R.string.toast_unmuted), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onSnapshot = {
                                val player = currentPlayer
                                val targetLayout = videoLayoutRef
                                if (player == null || targetLayout == null) {
                                    Toast.makeText(context, context.getString(R.string.toast_no_snapshot_target), Toast.LENGTH_SHORT).show()
                                } else {
                                    onSavePlaybackSnapshot(
                                        context,
                                        targetLayout,
                                        storagePathInput.trim().ifBlank { context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath },
                                        fullscreenCamera.name.ifBlank { fullscreenCamera.id },
                                        { file ->
                                            Toast.makeText(context, context.getString(R.string.toast_snapshot_saved, file.absolutePath), Toast.LENGTH_LONG).show()
                                            onReloadSnapshots()
                                        },
                                        { message ->
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        },
                                        false,
                                    )
                                }
                            },
                            onMore = { Toast.makeText(context, context.getString(R.string.toast_more_features_coming_soon), Toast.LENGTH_SHORT).show() },
                            isRecording = isRecording,
                            onToggleRecord = onToggleRecord,
                        )
                    } else {
                        CameraHomeScreen(
                            cameras = cameras,
                            snapshots = snapshots,
                            selectedCameraId = selectedCameraForGrid?.id,
                            playingCameraId = playingCameraId,
                            prefs = prefs,
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            onCameraClick = { device ->
                                onSelectedCameraIndexChange(cameras.indexOfFirst { it.id == device.id }.takeIf { it >= 0 } ?: 0)
                                if (!isVlcAvailable) {
                                    Toast.makeText(context, context.getString(R.string.toast_vlc_runtime_unavailable), Toast.LENGTH_SHORT).show()
                                    return@CameraHomeScreen
                                }
                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(context, context.getString(R.string.toast_host_missing_network_permission), Toast.LENGTH_LONG).show()
                                    return@CameraHomeScreen
                                }
                                val cameraToPlay = enrichCameraRtspUrl(
                                    camera = device,
                                    prefs = prefs,
                                    fallbackUsername = if (selectedDeviceId == device.id) manageUsername else "",
                                    fallbackPassword = if (selectedDeviceId == device.id) managePassword else "",
                                    fallbackLoginEnabled = if (selectedDeviceId == device.id) manageLoginEnabled else null,
                                )
                                val playbackValidation = validatePlaybackCamera(context, cameraToPlay, prefs)
                                if (!playbackValidation.canPlay) {
                                    Toast.makeText(context, playbackValidation.message, Toast.LENGTH_LONG).show()
                                    return@CameraHomeScreen
                                }
                                onPlaybackFullscreenChange(true)
                                onQueuePlayback(cameraToPlay)
                            },
                            onCameraLongPress = { device ->
                                onSelectedCameraIndexChange(cameras.indexOfFirst { it.id == device.id }.takeIf { it >= 0 } ?: 0)
                                onLongPressedCameraIdChange(device.id)
                            },
                            onAddCamera = onOpenAddLanding,
                            onSeedDemo = onSeedDemo,
                        )
                    }
                }
                1 -> BrowseScreen(
                    prefs = prefs,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    onPlayUrl = { title, url -> onPlayAdHocUrl(title, url) },
                )
                2 -> ActivityStreamScreen(
                    recordings = recordings,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    onOpenRecording = onCurrentRecordingDialogChange,
                    prefs = prefs,
                )
                3 -> SnapshotScreen(
                    snapshots = snapshots,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    onOpenSnapshot = onCurrentRecordingDialogChange,
                    onDeleteSnapshot = onPendingDeleteSnapshotChange,
                )
                4 -> SettingsHomeScreen(
                    storagePathInput = storagePathInput,
                    onStoragePathChange = onStoragePathInputChange,
                    recordingQualityInput = recordingQualityInput,
                    onRecordingQualityChange = onRecordingQualityInputChange,
                    recordingDurationInput = recordingDurationInput,
                    onRecordingDurationChange = onRecordingDurationInputChange,
                    appearanceThemeMode = appearanceThemeMode,
                    rtmpUrlInput = rtmpUrlInput,
                    onRtmpUrlChange = onRtmpUrlInputChange,
                    rtmpStreamKeyInput = rtmpStreamKeyInput,
                    onRtmpStreamKeyChange = onRtmpStreamKeyInputChange,
                    rtmpUsernameInput = rtmpUsernameInput,
                    onRtmpUsernameChange = onRtmpUsernameInputChange,
                    rtmpPasswordInput = rtmpPasswordInput,
                    onRtmpPasswordChange = onRtmpPasswordInputChange,
                    rtmpAudioEnabled = rtmpAudioEnabled,
                    onRtmpAudioEnabledChange = onRtmpAudioEnabledChange,
                    rtmpAutoStreamEnabled = rtmpAutoStreamEnabled,
                    onRtmpAutoStreamChange = onRtmpAutoStreamChange,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    onThemeModeChange = {
                        onAppearanceThemeModeChange(it)
                        prefs.edit().putString("appearance_theme_mode", it).apply()
                        AppearanceThemeState.update(it)
                        applyAppearanceThemeMode(it)
                        Toast.makeText(context, context.getString(R.string.toast_theme_changed), Toast.LENGTH_SHORT).show()
                    },
                    onClearRecordings = {
                        val allFiles = storageManager.getAllRecordings()
                        var deleted = 0
                        allFiles.forEach {
                            if (it.deleteFile()) {
                                deleted++
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.toast_recordings_cleared, deleted, allFiles.size), Toast.LENGTH_SHORT).show()
                        onReloadRecordings()
                    },
                    onManageRtmpSettings = {
                        Toast.makeText(context, context.getString(R.string.toast_rtmp_manager_coming_soon), Toast.LENGTH_SHORT).show()
                    },
                )
                5 -> DeviceManageTabContent(
                    cameras = cameras,
                    selectedDeviceId = selectedDeviceId,
                    deviceFlowStep = deviceFlowStep,
                    verificationResult = verificationResult,
                    manageName = manageName,
                    manageStreamScheme = manageStreamScheme,
                    manageHost = manageHost,
                    managePort = managePort,
                    manageStreamPath = manageStreamPath,
                    manageLoginEnabled = manageLoginEnabled,
                    manageUsername = manageUsername,
                    managePassword = managePassword,
                    managePasswordVisible = managePasswordVisible,
                    forceHttpSoftwareDecode = forceHttpSoftwareDecode,
                    manageVendor = manageVendor,
                    manageProfile = manageProfile,
                    prefs = prefs,
                    innerPadding = innerPadding,
                    scopeLaunch = { block -> scope.launch(block = block) },
                    onLoadSelectedDeviceToForm = onLoadSelectedDeviceToForm,
                    onDeviceFlowStepChange = onDeviceFlowStepChange,
                    onVerificationResultChange = onSaveVerificationResult,
                    onManageNameChange = onManageNameChange,
                    onManageStreamSchemeChange = onManageStreamSchemeChange,
                    onManageHostChange = onManageHostChange,
                    onManagePortChange = onManagePortChange,
                    onManageStreamPathChange = onManageStreamPathChange,
                    onManageLoginEnabledChange = onManageLoginEnabledChange,
                    onManageUsernameChange = onManageUsernameChange,
                    onManagePasswordChange = onManagePasswordChange,
                    onManagePasswordVisibleChange = onManagePasswordVisibleChange,
                    onForceHttpSoftwareDecodeChange = onForceHttpSoftwareDecodeChange,
                    onSelectedCameraIndexChange = onSelectedCameraIndexChange,
                    onSelectedTabChange = onSelectedTabChange,
                    onReloadCameras = onReloadCameras,
                    context = context,
                )
            }
        }

        if (cameraInAction != null && selectedTab != 5) {
            CameraActionOverlay(
                camera = cameraInAction,
                badge = cameraBadge(cameraInAction, prefs),
                modifier = Modifier.fillMaxSize(),
                onDismiss = { onLongPressedCameraIdChange(null) },
                onEdit = {
                    onLongPressedCameraIdChange(null)
                    onOpenEditForm(cameraInAction)
                },
                onCopy = {
                    onLongPressedCameraIdChange(null)
                    onDuplicateCamera(cameraInAction)
                },
                onDelete = {
                    onLongPressedCameraIdChange(null)
                    onDeleteCamera(cameraInAction)
                },
            )
        }
    }
}

@Composable
private fun DeviceManageTabContent(
    cameras: List<CameraDevice>,
    selectedDeviceId: String?,
    deviceFlowStep: DeviceFlowStep,
    verificationResult: VerificationPreview?,
    manageName: String,
    manageStreamScheme: String,
    manageHost: String,
    managePort: String,
    manageStreamPath: String,
    manageLoginEnabled: Boolean,
    manageUsername: String,
    managePassword: String,
    managePasswordVisible: Boolean,
    forceHttpSoftwareDecode: Boolean,
    manageVendor: String,
    manageProfile: String,
    prefs: android.content.SharedPreferences,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    scopeLaunch: (suspend CoroutineScope.() -> Unit) -> Unit,
    onLoadSelectedDeviceToForm: (CameraDevice?) -> Unit,
    onDeviceFlowStepChange: (DeviceFlowStep) -> Unit,
    onVerificationResultChange: (VerificationPreview?) -> Unit,
    onManageNameChange: (String) -> Unit,
    onManageStreamSchemeChange: (String) -> Unit,
    onManageHostChange: (String) -> Unit,
    onManagePortChange: (String) -> Unit,
    onManageStreamPathChange: (String) -> Unit,
    onManageLoginEnabledChange: (Boolean) -> Unit,
    onManageUsernameChange: (String) -> Unit,
    onManagePasswordChange: (String) -> Unit,
    onManagePasswordVisibleChange: (Boolean) -> Unit,
    onForceHttpSoftwareDecodeChange: (Boolean) -> Unit,
    onSelectedCameraIndexChange: (Int) -> Unit,
    onSelectedTabChange: (Int) -> Unit,
    onReloadCameras: () -> Unit,
    context: Context,
) {
    val selectedDevice = cameras.firstOrNull { it.id == selectedDeviceId } ?: cameras.firstOrNull()
    if (deviceFlowStep == DeviceFlowStep.Form && selectedDeviceId != null && selectedDeviceId != selectedDevice?.id) {
        LaunchedEffect(selectedDevice?.id, cameras.size) {
            onLoadSelectedDeviceToForm(selectedDevice)
        }
    }
    when (deviceFlowStep) {
        DeviceFlowStep.Landing -> AddCameraLandingScreen(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            onAddLocalCamera = {
                onLoadSelectedDeviceToForm(null)
                onDeviceFlowStepChange(DeviceFlowStep.Form)
            },
        )
        DeviceFlowStep.Verifying -> AddCameraVerificationScreen(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            result = verificationResult,
            forceHttpSoftwareDecode = forceHttpSoftwareDecode,
            onSave = {
                val result = verificationResult ?: return@AddCameraVerificationScreen
                val editingDeviceId = selectedDeviceId
                val targetDevice = CameraDevice(
                    id = editingDeviceId ?: java.util.UUID.randomUUID().toString(),
                    name = manageName.trim().ifBlank { result.name },
                    rtspUrl = result.primaryUrl,
                )
                scopeLaunch {
                    val success = DatabaseSaveBridge.saveVerifiedDevice(
                        context = context,
                        prefs = prefs,
                        targetDevice = targetDevice,
                        editingDeviceId = editingDeviceId,
                        manageVendor = manageVendor,
                        manageProfile = manageProfile,
                        manageLoginEnabled = manageLoginEnabled,
                        manageUsername = manageUsername,
                        managePassword = managePassword,
                        forceHttpSoftwareDecode = forceHttpSoftwareDecode,
                        onReloadCameras = onReloadCameras,
                        onLoadSelectedDeviceToForm = onLoadSelectedDeviceToForm,
                        onSelectCameraIndex = onSelectedCameraIndexChange,
                        currentCameras = cameras,
                        onSelectedTabChange = onSelectedTabChange,
                        onDeviceFlowStepChange = onDeviceFlowStepChange,
                        onVerificationResultChange = onVerificationResultChange,
                    )
                    Toast.makeText(
                        context,
                        if (success) context.getString(R.string.toast_save_success)
                        else if (editingDeviceId != null) context.getString(R.string.toast_update_failed)
                        else context.getString(R.string.toast_save_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        DeviceFlowStep.Form -> AddCameraFormScreen(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            manageName = manageName,
            onManageNameChange = onManageNameChange,
            manageStreamScheme = manageStreamScheme,
            onManageStreamSchemeChange = onManageStreamSchemeChange,
            manageHost = manageHost,
            onManageHostChange = onManageHostChange,
            managePort = managePort,
            onManagePortChange = { onManagePortChange(it.filter(Char::isDigit)) },
            manageStreamPath = manageStreamPath,
            onManageStreamPathChange = onManageStreamPathChange,
            manageLoginEnabled = manageLoginEnabled,
            onManageLoginEnabledChange = onManageLoginEnabledChange,
            manageUsername = manageUsername,
            onManageUsernameChange = onManageUsernameChange,
            managePassword = managePassword,
            onManagePasswordChange = onManagePasswordChange,
            managePasswordVisible = managePasswordVisible,
            onTogglePasswordVisible = { onManagePasswordVisibleChange(!managePasswordVisible) },
            forceHttpSoftwareDecode = forceHttpSoftwareDecode,
            onForceHttpSoftwareDecodeChange = onForceHttpSoftwareDecodeChange,
            onSave = {
                val name = manageName.trim().ifBlank { "IPCams" }
                val host = manageHost.trim()
                val port = managePort.trim()
                val path = manageStreamPath.trim().ifEmpty { "stream1" }
                val selectedScheme = PlaybackStreamType.fromScheme(manageStreamScheme) ?: PlaybackStreamType.RTSP
                if (host.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.toast_fill_host_ip), Toast.LENGTH_SHORT).show()
                    return@AddCameraFormScreen
                }
                if (port.isNotEmpty() && port.toIntOrNull() == null) {
                    Toast.makeText(context, context.getString(R.string.toast_port_format_invalid), Toast.LENGTH_SHORT).show()
                    return@AddCameraFormScreen
                }
                val playbackUrl = buildPlaybackUrl(
                    streamType = selectedScheme,
                    host = host,
                    port = port.ifBlank { defaultPlaybackPort(selectedScheme) },
                    path = path,
                    loginEnabled = manageLoginEnabled,
                    username = manageUsername,
                    password = managePassword,
                )
                onManageNameChange(name)
                onVerificationResultChange(
                    VerificationPreview(
                        name = name,
                        primaryUrl = playbackUrl,
                        streamScheme = selectedScheme.scheme,
                        host = host,
                        port = port.ifBlank { defaultPlaybackPort(selectedScheme) },
                        resolution = "2560x1440",
                        codec = "hevc",
                        streamPath = path,
                    )
                )
                onDeviceFlowStepChange(DeviceFlowStep.Verifying)
            },
        )
    }
}

private object DatabaseSaveBridge {
    fun saveVerifiedDevice(
        context: Context,
        prefs: android.content.SharedPreferences,
        targetDevice: CameraDevice,
        editingDeviceId: String?,
        manageVendor: String,
        manageProfile: String,
        manageLoginEnabled: Boolean,
        manageUsername: String,
        managePassword: String,
        forceHttpSoftwareDecode: Boolean,
        onReloadCameras: () -> Unit,
        onLoadSelectedDeviceToForm: (CameraDevice?) -> Unit,
        onSelectCameraIndex: (Int) -> Unit,
        currentCameras: List<CameraDevice>,
        onSelectedTabChange: (Int) -> Unit,
        onDeviceFlowStepChange: (DeviceFlowStep) -> Unit,
        onVerificationResultChange: (VerificationPreview?) -> Unit,
    ): Boolean {
        val dbHelper = com.example.nvr.utils.DatabaseHelper(context)
        val success = if (editingDeviceId != null) {
            dbHelper.updateCamera(targetDevice) > 0
        } else {
            dbHelper.addCamera(targetDevice)
        }
        if (success) {
            prefs.edit()
                .putString("camera_vendor_${targetDevice.id}", manageVendor.trim().ifBlank { "IPCams" })
                .putString("camera_profile_${targetDevice.id}", manageProfile.trim())
                .putBoolean("camera_login_enabled_${targetDevice.id}", manageLoginEnabled)
                .putString("camera_username_${targetDevice.id}", manageUsername.trim())
                .putString("camera_password_${targetDevice.id}", managePassword)
                .putBoolean("camera_force_http_software_decode_${targetDevice.id}", forceHttpSoftwareDecode)
                .apply()
            onReloadCameras()
            onLoadSelectedDeviceToForm(targetDevice)
            onSelectCameraIndex(currentCameras.indexOfFirst { it.id == targetDevice.id }.takeIf { it >= 0 } ?: 0)
            onSelectedTabChange(0)
            onDeviceFlowStepChange(DeviceFlowStep.Landing)
            onVerificationResultChange(null)
        }
        return success
    }
}
