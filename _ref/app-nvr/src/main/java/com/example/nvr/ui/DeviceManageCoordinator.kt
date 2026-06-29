package com.example.nvr.ui

import android.content.Context
import android.widget.Toast
import com.example.nvr.R
import com.example.nvr.model.CameraDevice
import com.example.nvr.utils.DatabaseHelper

fun openAddLandingState(
    loadSelectedDeviceToForm: (CameraDevice?) -> Unit,
    onVerificationResultChange: (VerificationPreview?) -> Unit,
    onDeviceFlowStepChange: (DeviceFlowStep) -> Unit,
    onSelectedTabChange: (Int) -> Unit,
) {
    loadSelectedDeviceToForm(null)
    onVerificationResultChange(null)
    onDeviceFlowStepChange(DeviceFlowStep.Landing)
    onSelectedTabChange(5)
}

fun openEditFormState(
    device: CameraDevice?,
    loadSelectedDeviceToForm: (CameraDevice?) -> Unit,
    onVerificationResultChange: (VerificationPreview?) -> Unit,
    onDeviceFlowStepChange: (DeviceFlowStep) -> Unit,
    onSelectedTabChange: (Int) -> Unit,
) {
    loadSelectedDeviceToForm(device)
    onVerificationResultChange(null)
    onDeviceFlowStepChange(DeviceFlowStep.Form)
    onSelectedTabChange(5)
}

fun duplicateCameraState(
    context: Context,
    dbHelper: DatabaseHelper,
    device: CameraDevice,
    prefs: android.content.SharedPreferences,
    fallbackVendor: String,
    fallbackProfile: String,
    fallbackLoginEnabled: Boolean,
    fallbackUsername: String,
    fallbackPassword: String,
    reloadCameras: () -> Unit,
    findCameraIndexById: (String) -> Int,
    onSelectedCameraIndexChange: (Int) -> Unit,
    currentSelectedCameraIndex: Int,
) {
    val copied = device.copy(
        id = java.util.UUID.randomUUID().toString(),
        name = "${device.name} 副本",
    )
    val ok = dbHelper.addCamera(copied)
    if (ok) {
        copyCameraPreferences(
            prefs = prefs,
            sourceCameraId = device.id,
            targetCameraId = copied.id,
            fallbackVendor = fallbackVendor,
            fallbackProfile = fallbackProfile,
            fallbackLoginEnabled = fallbackLoginEnabled,
            fallbackUsername = fallbackUsername,
            fallbackPassword = fallbackPassword,
        )
        reloadCameras()
        onSelectedCameraIndexChange(findCameraIndexById(copied.id).takeIf { it >= 0 } ?: currentSelectedCameraIndex)
    }
    Toast.makeText(context, if (ok) context.getString(R.string.toast_camera_copied) else context.getString(R.string.toast_camera_copy_failed), Toast.LENGTH_SHORT).show()
}

fun deleteCameraState(
    context: Context,
    dbHelper: DatabaseHelper,
    device: CameraDevice,
    prefs: android.content.SharedPreferences,
    isPlayingCurrentDevice: Boolean,
    clearPlaybackState: () -> Unit,
    onLastRequestedPlaybackCameraIdChange: (String?) -> Unit,
    onIsPlaybackFullscreenChange: (Boolean) -> Unit,
    reloadCameras: () -> Unit,
    loadSelectedDeviceToForm: (CameraDevice?) -> Unit,
    nextDevice: () -> CameraDevice?,
) {
    val ok = dbHelper.deleteCamera(device.id)
    if (ok) {
        clearCameraPreferences(prefs, device.id)
        if (isPlayingCurrentDevice) {
            clearPlaybackState()
            onLastRequestedPlaybackCameraIdChange(null)
            onIsPlaybackFullscreenChange(false)
        }
        reloadCameras()
        loadSelectedDeviceToForm(nextDevice())
    }
    Toast.makeText(context, if (ok) context.getString(R.string.toast_camera_deleted) else context.getString(R.string.toast_camera_delete_failed), Toast.LENGTH_SHORT).show()
}
