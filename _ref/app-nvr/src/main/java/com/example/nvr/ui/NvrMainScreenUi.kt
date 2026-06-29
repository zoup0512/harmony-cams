package com.example.nvr.ui

import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nvr.R
import com.example.nvr.ui.viewmodel.NvrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvrMainScreenContent() {
    val context = LocalContext.current
    val vm: NvrViewModel = viewModel(
        factory = NvrViewModel.provideFactory(context.applicationContext as android.app.Application)
    )
    // uiState 由 ViewModel 持有为 mutableStateOf，Compose 自动订阅变更
    val uiState = vm.uiState

    DisposableEffect(Unit) { onDispose { vm.clearPlaybackState() } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val allowBackground = vm.prefs.getBoolean("allow_background_playback", false)
                if (!allowBackground && uiState.playback.currentPlayer != null) {
                    vm.clearPlaybackState()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.selectedTab) {
        if (uiState.selectedTab != 0) {
            val playingCamera = vm.cameras.firstOrNull { it.id == uiState.playback.playingCameraId }
            if (playingCamera != null) {
                vm.rememberLastRequestedPlaybackCamera(playingCamera.id)
            }
            vm.clearPlaybackState()
            vm.onPlaybackFullscreenChange(false)
        }
    }

    LaunchedEffect(uiState.selectedTab, vm.cameras.size, uiState.playback.lastRequestedPlaybackCameraId, uiState.playback.currentPlayer, uiState.playback.pendingPlaybackCamera) {
        if (uiState.selectedTab != 0 || uiState.playback.currentPlayer != null || uiState.playback.pendingPlaybackCamera != null) {
            return@LaunchedEffect
        }
        val resumeCameraId = uiState.playback.lastRequestedPlaybackCameraId ?: return@LaunchedEffect
        val cameraToResume = vm.cameras.firstOrNull { it.id == resumeCameraId } ?: return@LaunchedEffect
        vm.queuePendingPlaybackResume(cameraToResume)
    }

    LaunchedEffect(uiState.selectedTab, uiState.playback.videoLayoutRef, uiState.playback.pendingPlaybackRequestId) {
        val layout = uiState.playback.videoLayoutRef
        val cameraToPlay = uiState.playback.pendingPlaybackCamera
        if (uiState.selectedTab != 0 || layout == null || cameraToPlay == null) {
            return@LaunchedEffect
        }
        val requestId = uiState.playback.pendingPlaybackRequestId

        runWhenVideoLayoutReady(
            layout = layout,
            onReady = {
                vm.startPlayback(
                    cameraToPlay = cameraToPlay,
                    layout = layout,
                    renderMode = resolvePlaybackRenderMode(vm.prefs, cameraToPlay),
                    allowFallback = true,
                )
            },
            onTimeout = {
                val currentState = vm.uiState.playback
                if (requestId != currentState.pendingPlaybackRequestId || currentState.pendingPlaybackCamera?.id != cameraToPlay.id || vm.uiState.selectedTab != 0 || !currentState.isPlaybackFullscreen) {
                    return@runWhenVideoLayoutReady
                }
                vm.clearPendingPlaybackRequest()
                Toast.makeText(context, context.getString(R.string.toast_player_view_not_ready), Toast.LENGTH_SHORT).show()
            },
        )
    }

    NvrMainContentScaffold(
        context = context,
        selectedTab = uiState.selectedTab,
        cameraInAction = vm.cameras.firstOrNull { it.id == uiState.longPressedCameraId },
        isPlaybackFullscreen = uiState.playback.isPlaybackFullscreen,
        deviceFlowStep = uiState.deviceForm.deviceFlowStep,
        selectedDeviceId = uiState.deviceForm.selectedDeviceId,
        manageName = uiState.deviceForm.manageName,
        selectedCameraForGrid = vm.cameras.getOrNull(uiState.selectedCameraIndex),
        cameras = vm.cameras,
        snapshots = vm.snapshots,
        recordings = vm.recordings,
        playingCameraId = uiState.playback.playingCameraId,
        lastRequestedPlaybackCameraId = uiState.playback.lastRequestedPlaybackCameraId,
        currentPlayer = uiState.playback.currentPlayer,
        videoLayoutRef = uiState.playback.videoLayoutRef,
        isVlcAvailable = vm.isVlcAvailable,
        prefs = vm.prefs,
        storageManager = vm.storageManager,
        storagePathInput = uiState.settings.storagePathInput,
        recordingQualityInput = uiState.settings.recordingQualityInput,
        recordingDurationInput = uiState.settings.recordingDurationInput,
        rtmpUrlInput = uiState.settings.rtmpUrlInput,
        rtmpStreamKeyInput = uiState.settings.rtmpStreamKeyInput,
        rtmpUsernameInput = uiState.settings.rtmpUsernameInput,
        rtmpPasswordInput = uiState.settings.rtmpPasswordInput,
        rtmpAudioEnabled = uiState.settings.rtmpAudioEnabled,
        rtmpAutoStreamEnabled = uiState.settings.rtmpAutoStreamEnabled,
        appearanceThemeMode = uiState.settings.appearanceThemeMode,
        manageVendor = uiState.deviceForm.manageVendor,
        manageProfile = uiState.deviceForm.manageProfile,
        manageStreamScheme = uiState.deviceForm.manageStreamScheme,
        manageHost = uiState.deviceForm.manageHost,
        managePort = uiState.deviceForm.managePort,
        manageStreamPath = uiState.deviceForm.manageStreamPath,
        manageLoginEnabled = uiState.deviceForm.manageLoginEnabled,
        manageUsername = uiState.deviceForm.manageUsername,
        managePassword = uiState.deviceForm.managePassword,
        managePasswordVisible = uiState.deviceForm.managePasswordVisible,
        forceHttpSoftwareDecode = uiState.deviceForm.forceHttpSoftwareDecode,
        verificationResult = uiState.deviceForm.verificationResult,
        lastNonZeroVolume = uiState.playback.lastNonZeroVolume,
        onSelectedTabChange = vm::onSelectedTabChange,
        onLongPressedCameraIdChange = vm::onLongPressedCameraIdChange,
        onVerificationResultChange = vm::onVerificationResultChange,
        onDeviceFlowStepChange = vm::onDeviceFlowStepChange,
        onSelectedCameraIndexChange = vm::onSelectedCameraIndexChange,
        onVideoLayoutRefChange = vm::onVideoLayoutRefChange,
        onQueuePlayback = vm::queuePlayback,
        onOpenEditForm = vm::openEditForm,
        onOpenAddLanding = vm::openAddLanding,
        onSeedDemo = {},
        onDuplicateCamera = vm::duplicateCamera,
        onDeleteCamera = vm::deleteCamera,
        onCurrentRecordingDialogChange = vm::onCurrentRecordingDialogChange,
        onPendingDeleteSnapshotChange = vm::onPendingDeleteSnapshotChange,
        onCurrentPlayerPlay = vm::onCurrentPlayerPlay,
        onCurrentPlayerPause = vm::onCurrentPlayerPause,
        onCurrentPlayerSetVolume = vm::onCurrentPlayerSetVolume,
        onLastNonZeroVolumeChange = vm::onLastNonZeroVolumeChange,
        onManageNameChange = vm::onManageNameChange,
        onManageStreamSchemeChange = vm::onManageStreamSchemeChange,
        onManageHostChange = vm::onManageHostChange,
        onManagePortChange = vm::onManagePortChange,
        onManageStreamPathChange = vm::onManageStreamPathChange,
        onManageLoginEnabledChange = vm::onManageLoginEnabledChange,
        onManageUsernameChange = vm::onManageUsernameChange,
        onManagePasswordChange = vm::onManagePasswordChange,
        onManagePasswordVisibleChange = vm::onManagePasswordVisibleChange,
        onForceHttpSoftwareDecodeChange = vm::onForceHttpSoftwareDecodeChange,
        onAppearanceThemeModeChange = vm::onAppearanceThemeModeChange,
        onStoragePathInputChange = vm::onStoragePathChange,
        onRecordingQualityInputChange = vm::onRecordingQualityChange,
        onRecordingDurationInputChange = vm::onRecordingDurationChange,
        onRtmpUrlInputChange = vm::onRtmpUrlChange,
        onRtmpStreamKeyInputChange = vm::onRtmpStreamKeyChange,
        onRtmpUsernameInputChange = vm::onRtmpUsernameChange,
        onRtmpPasswordInputChange = vm::onRtmpPasswordChange,
        onRtmpAudioEnabledChange = vm::onRtmpAudioEnabledChange,
        onRtmpAutoStreamChange = vm::onRtmpAutoStreamChange,
        onClearPlaybackState = vm::clearPlaybackState,
        onPlaybackFullscreenChange = vm::onPlaybackFullscreenChange,
        onSyncPlayerSurface = ::syncPlayerSurface,
        onSavePlaybackSnapshot = ::savePlaybackSnapshot,
        adHocPlaybackCamera = uiState.playback.adHocPlaybackCamera,
        tabBeforeFullscreen = uiState.playback.tabBeforeFullscreen,
        onPlayAdHocUrl = vm::playAdHocUrl,
        isVideoReadyProvider = {
            val player = uiState.playback.currentPlayer
            player != null && (vm.streamManager.hasVideoOutput(player) || player.isPlaying)
        },
        onReloadCameras = vm::reloadCameras,
        onReloadRecordings = vm::reloadRecordings,
        onReloadSnapshots = vm::reloadSnapshots,
        onLoadSelectedDeviceToForm = vm::loadSelectedDeviceToForm,
        onSaveVerificationResult = vm::onVerificationResultChange,
        isRecording = uiState.playback.isRecording,
        onToggleRecord = vm::toggleRecording,
    )

    // 对话框
    uiState.currentRecordingDialog?.let { dialogFile ->
        RecordingPreviewDialog(
            dialogFile = dialogFile,
            onDismiss = { vm.onCurrentRecordingDialogChange(null) },
        )
    }

    uiState.pendingDeleteSnapshot?.let { deleteSnapshotFile ->
        DeleteSnapshotConfirmDialog(
            fileName = deleteSnapshotFile.fileName,
            onDismiss = { vm.onPendingDeleteSnapshotChange(null) },
            onConfirmDelete = {
                val deleted = deleteSnapshotFile.deleteFile()
                vm.onPendingDeleteSnapshotChange(null)
                if (vm.uiState.currentRecordingDialog?.filePath == deleteSnapshotFile.filePath) {
                    vm.onCurrentRecordingDialogChange(null)
                }
                if (!deleted) {
                    vm.reloadSnapshots()
                }
                Toast.makeText(context, if (deleted) context.getString(R.string.toast_snapshot_deleted) else context.getString(R.string.toast_snapshot_delete_failed), Toast.LENGTH_SHORT).show()
            },
        )
    }
}
