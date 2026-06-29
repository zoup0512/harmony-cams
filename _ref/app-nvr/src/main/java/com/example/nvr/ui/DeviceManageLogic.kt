package com.example.nvr.ui

import android.content.SharedPreferences
import com.example.nvr.model.CameraDevice

data class DeviceManageFormValue(
    val selectedDeviceId: String?,
    val manageName: String,
    val manageVendor: String,
    val manageProfile: String,
    val manageStreamScheme: String,
    val manageStreamPath: String,
    val manageHost: String,
    val managePort: String,
    val manageLoginEnabled: Boolean,
    val manageUsername: String,
    val managePassword: String,
    val managePasswordVisible: Boolean,
    val forceHttpSoftwareDecode: Boolean,
)

fun emptyDeviceManageFormValue(): DeviceManageFormValue {
    return DeviceManageFormValue(
        selectedDeviceId = null,
        manageName = "",
        manageVendor = "",
        manageProfile = "",
        manageStreamScheme = PlaybackStreamType.RTSP.scheme,
        manageStreamPath = "stream1",
        manageHost = "",
        managePort = "",
        manageLoginEnabled = true,
        manageUsername = "",
        managePassword = "",
        managePasswordVisible = false,
        forceHttpSoftwareDecode = false,
    )
}

fun buildDeviceManageFormValue(
    device: CameraDevice,
    prefs: SharedPreferences,
): DeviceManageFormValue {
    val streamType = PlaybackStreamType.fromUrl(device.rtspUrl) ?: PlaybackStreamType.RTSP
    val parsed = parsePlaybackUrl(device.rtspUrl)
    val username = parsed.username.ifBlank { prefs.getString("camera_username_${device.id}", "").orEmpty() }
    return DeviceManageFormValue(
        selectedDeviceId = device.id,
        manageName = device.name,
        manageVendor = prefs.getString("camera_vendor_${device.id}", "TP-Link").orEmpty(),
        manageProfile = prefs.getString("camera_profile_${device.id}", "default").orEmpty(),
        manageStreamScheme = when (streamType) {
            PlaybackStreamType.RTMP -> PlaybackStreamType.HTTP.scheme
            else -> streamType.scheme
        },
        manageStreamPath = if (parsed.path.isBlank()) "stream1" else parsed.path,
        manageHost = parsed.host,
        managePort = parsed.port.ifBlank { defaultPlaybackPort(streamType) },
        manageLoginEnabled = prefs.getBoolean(
            "camera_login_enabled_${device.id}",
            parsed.username.isNotBlank() || username.isNotBlank(),
        ),
        manageUsername = username,
        managePassword = parsed.password.ifBlank { prefs.getString("camera_password_${device.id}", "").orEmpty() },
        managePasswordVisible = false,
        forceHttpSoftwareDecode = prefs.getBoolean("camera_force_http_software_decode_${device.id}", false),
    )
}

fun copyCameraPreferences(
    prefs: SharedPreferences,
    sourceCameraId: String,
    targetCameraId: String,
    fallbackVendor: String,
    fallbackProfile: String,
    fallbackLoginEnabled: Boolean,
    fallbackUsername: String,
    fallbackPassword: String,
) {
    val sourceVendor = prefs.getString("camera_vendor_${sourceCameraId}", fallbackVendor).orEmpty()
    val sourceProfile = prefs.getString("camera_profile_${sourceCameraId}", fallbackProfile).orEmpty()
    val sourceLoginEnabled = prefs.getBoolean("camera_login_enabled_${sourceCameraId}", fallbackLoginEnabled)
    val sourceUsername = prefs.getString("camera_username_${sourceCameraId}", fallbackUsername).orEmpty()
    val sourcePassword = prefs.getString("camera_password_${sourceCameraId}", fallbackPassword).orEmpty()
    val sourceForceHttpSoftwareDecode = prefs.getBoolean("camera_force_http_software_decode_${sourceCameraId}", false)
    prefs.edit()
        .putString("camera_vendor_${targetCameraId}", sourceVendor)
        .putString("camera_profile_${targetCameraId}", sourceProfile)
        .putBoolean("camera_login_enabled_${targetCameraId}", sourceLoginEnabled)
        .putString("camera_username_${targetCameraId}", sourceUsername)
        .putString("camera_password_${targetCameraId}", sourcePassword)
        .putBoolean("camera_force_http_software_decode_${targetCameraId}", sourceForceHttpSoftwareDecode)
        .apply()
}

fun clearCameraPreferences(
    prefs: SharedPreferences,
    cameraId: String,
) {
    prefs.edit()
        .remove("camera_vendor_${cameraId}")
        .remove("camera_profile_${cameraId}")
        .remove("camera_login_enabled_${cameraId}")
        .remove("camera_username_${cameraId}")
        .remove("camera_password_${cameraId}")
        .remove("camera_force_http_software_decode_${cameraId}")
        .apply()
}
