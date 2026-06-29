package com.example.nvr.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.nvr.R
import com.example.nvr.model.CameraDevice
import java.net.IDN
import java.util.Locale

data class PlaybackValidation(
    val canPlay: Boolean,
    val message: String,
    val sanitizedUrl: String,
    val host: String,
    val port: String,
    val usernamePresent: Boolean,
    val passwordPresent: Boolean,
    val loginEnabled: Boolean,
)

enum class PlaybackStreamType(
    val scheme: String,
    val schemeLabel: String,
) {
    RTSP("rtsp", "RTSP"),
    RTMP("rtmp", "RTMP"),
    HTTP("http", "HTTP"),
    HTTPS("https", "HTTPS");

    companion object {
        fun fromScheme(scheme: String): PlaybackStreamType? {
            return when (scheme.lowercase(Locale.US)) {
                RTSP.scheme -> RTSP
                RTMP.scheme -> RTMP
                HTTP.scheme -> HTTP
                HTTPS.scheme -> HTTPS
                else -> null
            }
        }

        fun fromUrl(url: String): PlaybackStreamType? {
            val scheme = runCatching { Uri.parse(url).scheme.orEmpty() }.getOrDefault("")
            return fromScheme(scheme)
        }
    }
}

fun PlaybackValidation.toDebugString(): String {
    return "canPlay=$canPlay, message=$message, url=$sanitizedUrl, host=$host, port=$port, usernamePresent=$usernamePresent, passwordPresent=$passwordPresent, loginEnabled=$loginEnabled"
}

enum class PlaybackRenderMode(
    val useTextureView: Boolean,
    val enableHardwareDecoder: Boolean,
) {
    TEXTURE_HARDWARE(true, true),
    SURFACE_SOFTWARE(false, false);

    fun fallback(): PlaybackRenderMode = if (this == TEXTURE_HARDWARE) SURFACE_SOFTWARE else TEXTURE_HARDWARE
}

const val PLAYBACK_RENDER_MODE_PREFIX = "playback_render_mode_"

data class RtspParsed(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val path: String,
)

fun parsePlaybackUrl(playbackUrl: String): RtspParsed {
    return parseRtspUrl(playbackUrl)
}

fun playbackSchemeForUrl(playbackUrl: String): String {
    return when (PlaybackStreamType.fromUrl(playbackUrl)) {
        PlaybackStreamType.RTMP -> PlaybackStreamType.RTMP.scheme
        PlaybackStreamType.HTTP -> PlaybackStreamType.HTTP.scheme
        PlaybackStreamType.HTTPS -> PlaybackStreamType.HTTPS.scheme
        else -> PlaybackStreamType.RTSP.scheme
    }
}

fun defaultPlaybackPort(streamType: PlaybackStreamType?): String {
    return when (streamType) {
        PlaybackStreamType.RTSP -> "554"
        PlaybackStreamType.RTMP -> "1935"
        PlaybackStreamType.HTTP -> "80"
        PlaybackStreamType.HTTPS -> "443"
        null -> "554"
    }
}

fun enrichCameraRtspUrl(
    camera: CameraDevice,
    prefs: android.content.SharedPreferences,
    fallbackUsername: String = "",
    fallbackPassword: String = "",
    fallbackLoginEnabled: Boolean? = null,
): CameraDevice {
    val streamType = PlaybackStreamType.fromUrl(camera.rtspUrl) ?: return camera
    if (streamType != PlaybackStreamType.RTSP && streamType != PlaybackStreamType.RTMP) {
        Log.d(
            "NvrMainScreen",
            "非 RTSP 播放地址无需补全鉴权 cameraId=${camera.id}, url=${sanitizePlaybackUrl(camera.rtspUrl)}"
        )
        return camera
    }

    val parsed = parsePlaybackUrl(camera.rtspUrl)
    if (parsed.username.isNotBlank()) {
        Log.d(
            "NvrMainScreen",
            "播放地址已自带鉴权 cameraId=${camera.id}, url=${sanitizePlaybackUrl(camera.rtspUrl)}"
        )
        return camera
    }
    val loginEnabled = fallbackLoginEnabled ?: prefs.getBoolean("camera_login_enabled_${camera.id}", false)
    val username = prefs.getString("camera_username_${camera.id}", "").orEmpty().trim().ifBlank { fallbackUsername.trim() }
    val password = prefs.getString("camera_password_${camera.id}", "").orEmpty().ifBlank { fallbackPassword }
    Log.d(
        "NvrMainScreen",
        "尝试补全播放地址 cameraId=${camera.id}, loginEnabled=$loginEnabled, usernamePresent=${username.isNotBlank()}, passwordPresent=${password.isNotBlank()}, rawUrl=${sanitizePlaybackUrl(camera.rtspUrl)}"
    )
    if (!loginEnabled || username.isBlank()) {
        return camera
    }

    if (parsed.host.isBlank()) {
        return camera
    }

    val enrichedUrl = buildPlaybackUrl(
        streamType = streamType,
        host = parsed.host,
        port = parsed.port,
        path = parsed.path.ifBlank { "stream1" },
        loginEnabled = true,
        username = username,
        password = password,
    )
    Log.d(
        "NvrMainScreen",
        "补全后的播放地址 cameraId=${camera.id}, url=${sanitizePlaybackUrl(enrichedUrl)}"
    )
    return camera.copy(rtspUrl = enrichedUrl)
}

fun buildPlaybackUrl(
    streamType: PlaybackStreamType,
    host: String,
    port: String,
    path: String,
    loginEnabled: Boolean,
    username: String,
    password: String,
): String {
    val authPart = if (loginEnabled && username.isNotBlank()) {
        if (password.isNotBlank()) "${username}:${password}@" else "${username}@"
    } else {
        ""
    }
    val normalizedHost = normalizeNetworkHost(host)
    val hostPart = if (port.isNotBlank()) "$normalizedHost:$port" else normalizedHost
    return "${streamType.scheme}://$authPart$hostPart/$path"
}

fun buildRtspUrl(
    host: String,
    port: String,
    path: String,
    loginEnabled: Boolean,
    username: String,
    password: String,
): String {
    return buildPlaybackUrl(
        streamType = PlaybackStreamType.RTSP,
        host = host,
        port = port,
        path = path,
        loginEnabled = loginEnabled,
        username = username,
        password = password,
    )
}

fun validatePlaybackCamera(
    context: Context,
    camera: CameraDevice,
    prefs: android.content.SharedPreferences,
): PlaybackValidation {
    val streamType = PlaybackStreamType.fromUrl(camera.rtspUrl)
    val parsed = parsePlaybackUrl(camera.rtspUrl)
    val loginEnabled = prefs.getBoolean("camera_login_enabled_${camera.id}", false)
    val username = parsed.username.ifBlank { prefs.getString("camera_username_${camera.id}", "").orEmpty().trim() }
    val password = parsed.password.ifBlank { prefs.getString("camera_password_${camera.id}", "").orEmpty() }
    val sanitizedUrl = sanitizePlaybackUrl(camera.rtspUrl)
    val schemeLabel = streamType?.schemeLabel ?: "STREAM"
    return when {
        parsed.host.isBlank() -> PlaybackValidation(
            canPlay = false,
            message = context.getString(R.string.toast_playback_validation_host_empty, schemeLabel),
            sanitizedUrl = sanitizedUrl,
            host = parsed.host,
            port = parsed.port,
            usernamePresent = username.isNotBlank(),
            passwordPresent = password.isNotBlank(),
            loginEnabled = loginEnabled,
        )
        loginEnabled && username.isBlank() -> PlaybackValidation(
            canPlay = false,
            message = context.getString(R.string.toast_playback_validation_login_required),
            sanitizedUrl = sanitizedUrl,
            host = parsed.host,
            port = parsed.port,
            usernamePresent = false,
            passwordPresent = password.isNotBlank(),
            loginEnabled = true,
        )
        streamType != null && loginEnabled && !camera.rtspUrl.contains("@") -> PlaybackValidation(
            canPlay = false,
            message = context.getString(R.string.toast_playback_validation_auth_missing),
            sanitizedUrl = sanitizedUrl,
            host = parsed.host,
            port = parsed.port,
            usernamePresent = username.isNotBlank(),
            passwordPresent = password.isNotBlank(),
            loginEnabled = true,
        )
        streamType == null -> PlaybackValidation(
            canPlay = false,
            message = context.getString(R.string.toast_playback_validation_unsupported_scheme),
            sanitizedUrl = sanitizedUrl,
            host = parsed.host,
            port = parsed.port,
            usernamePresent = username.isNotBlank(),
            passwordPresent = password.isNotBlank(),
            loginEnabled = loginEnabled,
        )
        else -> PlaybackValidation(
            canPlay = true,
            message = when (streamType) {
                PlaybackStreamType.RTSP -> context.getString(R.string.toast_playback_validation_pass_rtsp)
                PlaybackStreamType.RTMP -> context.getString(R.string.toast_playback_validation_pass_rtmp)
                PlaybackStreamType.HTTP -> context.getString(R.string.toast_playback_validation_pass_http)
                PlaybackStreamType.HTTPS -> context.getString(R.string.toast_playback_validation_pass_https)
                null -> context.getString(R.string.toast_playback_validation_pass_generic)
            },
            sanitizedUrl = sanitizedUrl,
            host = parsed.host,
            port = parsed.port,
            usernamePresent = username.isNotBlank(),
            passwordPresent = password.isNotBlank(),
            loginEnabled = loginEnabled,
        )
    }
}

fun sanitizePlaybackUrl(playbackUrl: String): String {
    return playbackUrl.replace(Regex("(://)([^:/@]+)(?::([^@/]*))?@")) {
        val username = it.groupValues.getOrNull(2).orEmpty()
        val password = it.groupValues.getOrNull(3).orEmpty()
        val maskedUser = if (username.isNotBlank()) "***" else ""
        val maskedPassword = if (password.isNotBlank()) ":***" else ""
        "${it.groupValues[1]}$maskedUser$maskedPassword@"
    }
}

fun sanitizeRtspUrl(rtspUrl: String): String = sanitizePlaybackUrl(rtspUrl)

fun playbackRenderModeKey(cameraId: String): String {
    return PLAYBACK_RENDER_MODE_PREFIX + cameraId
}

fun defaultPlaybackRenderMode(): PlaybackRenderMode {
    return PlaybackRenderMode.SURFACE_SOFTWARE
}

fun loadPlaybackRenderMode(
    prefs: android.content.SharedPreferences,
    cameraId: String,
): PlaybackRenderMode? {
    val stored = prefs.getString(playbackRenderModeKey(cameraId), null)?.uppercase(Locale.US) ?: return null
    return runCatching { PlaybackRenderMode.valueOf(stored) }.getOrNull()
}

fun savePlaybackRenderMode(
    prefs: android.content.SharedPreferences,
    cameraId: String,
    mode: PlaybackRenderMode,
) {
    prefs.edit().putString(playbackRenderModeKey(cameraId), mode.name).apply()
}

fun parseRtspUrl(rtspUrl: String): RtspParsed {
    return runCatching {
        val uri = Uri.parse(rtspUrl)
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port.toString() else ""
        val userInfo = uri.userInfo.orEmpty()
        val username = if (userInfo.contains(":")) userInfo.substringBefore(":") else userInfo
        val password = if (userInfo.contains(":")) userInfo.substringAfter(":", "") else ""
        val path = uri.path.orEmpty().removePrefix("/")
        RtspParsed(
            host = host,
            port = port,
            username = username,
            password = password,
            path = path,
        )
    }.getOrElse {
        RtspParsed(
            host = "",
            port = "",
            username = "",
            password = "",
            path = "stream1",
        )
    }
}

fun normalizeNetworkHost(host: String): String {
    val trimmed = host.trim().trim('/')
    if (trimmed.isBlank()) return ""

    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val withoutUserInfo = authority.substringAfter('@', authority)
    val candidate = withoutUserInfo.substringBefore(':').trim().trim('.')
    if (candidate.isBlank()) return ""

    return runCatching { IDN.toASCII(candidate) }.getOrDefault(candidate)
}
