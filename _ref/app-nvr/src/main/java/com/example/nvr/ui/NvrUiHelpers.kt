package com.example.nvr.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.videolan.libvlc.util.VLCVideoLayout
import java.net.Inet4Address
import java.net.NetworkInterface

const val PHONE_RTSP_PORT = 8554
const val PHONE_RTSP_PATH = "live"

fun buildPhoneRtspAddress(context: Context, port: Int): String {
    val host = getLocalIpAddress() ?: "127.0.0.1"
    return "rtsp://$host:$port/$PHONE_RTSP_PATH"
}

fun getLocalIpAddress(): String? {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val inetAddress = addresses.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
        null
    }.getOrNull()
}

fun checkAndRequestPermissions(activity: Activity) {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ 用细分媒体权限
        permissions += Manifest.permission.READ_MEDIA_VIDEO
    } else {
        permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    val permissionsToRequest = permissions.filter {
        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
    }

    if (permissionsToRequest.isNotEmpty()) {
        ActivityCompat.requestPermissions(activity, permissionsToRequest.toTypedArray(), 1001)
    }
}

fun runWhenVideoLayoutReady(
    layout: VLCVideoLayout,
    maxAttempts: Int = 30,
    intervalMs: Long = 100L,
    onReady: () -> Unit,
    onTimeout: () -> Unit,
) {
    val handler = Handler(Looper.getMainLooper())
    fun probe(remaining: Int) {
        val ready =
            layout.windowToken != null &&
                layout.isAttachedToWindow &&
                layout.isShown &&
                layout.width > 0 &&
                layout.height > 0
        if (ready) {
            onReady()
            return
        }
        if (remaining <= 0) {
            onTimeout()
            return
        }
        handler.postDelayed({ probe(remaining - 1) }, intervalMs)
    }
    probe(maxAttempts)
}

fun Context.findActivityCompat(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

fun Activity.returnToHostApp() {
    (this as? ComponentActivity)?.let { activity ->
        runCatching {
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
    runCatching {
        finish()
    }
}

fun treeUriToPath(uri: Uri): String? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    val parts = docId.split(":")
    if (parts.size < 2) return null
    val type = parts[0]
    val relativePath = parts[1]
    val basePath = when (type) {
        "primary" -> Environment.getExternalStorageDirectory().absolutePath
        else -> "/storage/$type"
    }
    return if (relativePath.isBlank()) basePath else "$basePath/$relativePath"
}
