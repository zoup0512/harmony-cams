package com.example.nvr.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.content.res.Configuration
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.nvr.model.CameraDevice
import com.example.nvr.R
import com.example.nvr.model.RecordingFile
import com.example.nvr.ui.theme.LocalNvrAccent
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera1
import java.io.File

@Composable
fun ActivityStreamScreen(
    recordings: List<RecordingFile>,
    modifier: Modifier = Modifier,
    onOpenRecording: (RecordingFile) -> Unit,
    prefs: android.content.SharedPreferences,
) {
    var selectedStreamTool by remember(prefs) { 
        mutableStateOf(prefs.getString("activity_stream_tool", "rtsp") ?: "rtsp") 
    }
    
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActivityStreamOptionChip(
                    title = "RTSP 服务器",
                    selected = selectedStreamTool == "rtsp",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedStreamTool = "rtsp"
                        prefs.edit().putString("activity_stream_tool", "rtsp").apply()
                    },
                )
                ActivityStreamOptionChip(
                    title = "RTMP 推流",
                    selected = selectedStreamTool == "rtmp",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedStreamTool = "rtmp"
                        prefs.edit().putString("activity_stream_tool", "rtmp").apply()
                    },
                )
            }
        }

        item {
            if (selectedStreamTool == "rtmp") {
                PhoneRtmpPushStreamCard(prefs = prefs)
            } else {
                PhoneRtspStreamCard()
            }
        }

        item {
            Text(
                text = "活动记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
            )
        }

        if (recordings.isEmpty()) {
            item {
                EmptyDarkScreen(text = "暂无活动记录")
            }
        } else {
            items(recordings) { recording ->
                RecordingRow(recording = recording, onOpen = { onOpenRecording(recording) })
            }
        }
    }
}

@Composable
fun ActivityStreamOptionChip(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun PhoneRtspStreamCard() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val tag = "NvrMainScreen"
    var rtspServerCamera by remember { mutableStateOf<RtspServerCamera1?>(null) }
    var previewSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("未启动") }
    var previewSurfaceReady by remember { mutableStateOf(false) }
    val rtspAddress = remember {
        buildPhoneRtspAddress(context, PHONE_RTSP_PORT)
    }

    fun postStatus(message: String) {
        mainHandler.post { statusText = message }
    }

    val connectChecker = remember {
        object : ConnectChecker {
            override fun onConnectionStarted(url: String) {
                Log.d(tag, "[PhoneRtsp][connect] started url=${sanitizeRtspUrl(url)}")
                postStatus("连接中: ${sanitizeRtspUrl(url)}")
            }

            override fun onConnectionSuccess() {
                Log.d(tag, "[PhoneRtsp][connect] success")
                mainHandler.post {
                    isStreaming = true
                    statusText = "推流中"
                }
            }

            override fun onConnectionFailed(reason: String) {
                Log.e(tag, "[PhoneRtsp][connect] failed reason=$reason")
                mainHandler.post {
                    isStreaming = false
                    statusText = "连接失败: $reason"
                }
            }

            override fun onDisconnect() {
                Log.d(tag, "[PhoneRtsp][connect] disconnect")
                mainHandler.post {
                    isStreaming = false
                    statusText = "已停止"
                }
            }

            override fun onAuthError() {
                Log.e(tag, "[PhoneRtsp][connect] auth error")
                postStatus("鉴权失败")
            }

            override fun onAuthSuccess() {
                Log.d(tag, "[PhoneRtsp][connect] auth success")
                postStatus("鉴权成功")
            }

            override fun onNewBitrate(bitrate: Long) {
                Log.d(tag, "[PhoneRtsp][connect] bitrate=${bitrate / 1000} kbps")
                mainHandler.post {
                    statusText = "推流中 · ${bitrate / 1000} kbps"
                }
            }
        }
    }

    fun logRtspState(event: String, extra: String = "") {
        val camera = rtspServerCamera
        Log.d(
            tag,
            "[PhoneRtsp][$event] front=$useFrontCamera, streaming=$isStreaming, previewReady=$previewSurfaceReady, camera=${camera?.javaClass?.simpleName}@${camera?.hashCode()}, cameraPreview=${camera?.isOnPreview}, cameraStreaming=${camera?.isStreaming}, cameraFront=${camera?.isFrontCamera}${if (extra.isBlank()) "" else ", $extra"}"
        )
    }

    fun createRtspCamera(surfaceView: SurfaceView, eventLabel: String) {
        try {
            val old = rtspServerCamera
            if (old != null) {
                if (old.isOnPreview || old.isStreaming) {
                    runCatching { old.stopStream() }
                    runCatching { old.stopPreview() }
                }
            }
            rtspServerCamera = RtspServerCamera1(surfaceView, connectChecker, PHONE_RTSP_PORT)
            Log.d(
                tag,
                "[PhoneRtsp][$eventLabel] created RtspServerCamera1@${rtspServerCamera?.hashCode()} on SurfaceView@${surfaceView.hashCode()}"
            )
        } catch (e: Exception) {
            Log.e(tag, "[PhoneRtsp][$eventLabel] failed to create camera: ${e.message}", e)
            rtspServerCamera = null
            previewSurfaceReady = false
            statusText = "摄像头初始化失败: ${e.message}"
        }
    }

    fun startStream() {
        val camera = rtspServerCamera
        logRtspState("startStream_click")
        if (camera == null) {
            Log.w(tag, "[PhoneRtsp][startStream] camera is null")
            statusText = "预览尚未就绪"
            return
        }
        if (isStreaming || camera.isStreaming) {
            Log.w(tag, "[PhoneRtsp][startStream] already streaming")
            statusText = "已经在推流"
            return
        }

        val rotation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 90 else 0
        Log.d(tag, "[PhoneRtsp][startStream] preparing encoder rotation=$rotation front=$useFrontCamera")
        val prepared = runCatching {
            camera.prepareAudio(64 * 1024, 44100, true, false, false) &&
                camera.prepareVideo(640, 480, 30, 1_200 * 1024, rotation)
        }.getOrDefault(false)

        if (!prepared) {
            Log.e(tag, "[PhoneRtsp][startStream] encoder prepare failed")
            statusText = "编码器初始化失败"
            return
        }

        runCatching {
            Log.d(tag, "[PhoneRtsp][startStream] startPreview facing=${if (useFrontCamera) "FRONT" else "BACK"}")
            camera.startPreview(if (useFrontCamera) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK)
            Log.d(tag, "[PhoneRtsp][startStream] startStream invoke")
            camera.startStream()
            previewSurfaceReady = true
            logRtspState("startStream_afterCall")
            statusText = "正在启动 ${sanitizeRtspUrl(rtspAddress)}"
        }.onFailure {
            isStreaming = false
            Log.e(tag, "[PhoneRtsp][startStream] failed: ${it.message}", it)
            statusText = "启动失败: ${it.message}"
            Log.e(tag, "手机RTSP推流启动失败: ${it.message}", it)
        }
    }

    fun restartStreamWithNewCamera(targetFrontCamera: Boolean): Boolean {
        val camera = rtspServerCamera
        logRtspState("switch_click", "targetFront=$targetFrontCamera")
        if (camera == null) {
            Log.w(tag, "[PhoneRtsp][switch] camera is null")
            statusText = "预览尚未就绪"
            return false
        }

        val targetFacing = if (targetFrontCamera) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK
        Log.d(
            tag,
            "[PhoneRtsp][switch] targetFacing=$targetFacing, cameraPreview=${camera.isOnPreview}, cameraStreaming=${camera.isStreaming}, cameraFront=${camera.isFrontCamera}"
        )

        if (!camera.isOnPreview && !camera.isStreaming) {
            return runCatching {
                Log.d(tag, "[PhoneRtsp][switch] no active preview/stream, startPreview only")
                camera.startPreview(targetFacing)
                previewSurfaceReady = true
                statusText = if (targetFrontCamera) "已切到前置摄像头" else "已切到后置摄像头"
                logRtspState("switch_preview_only_success", "targetFacing=$targetFacing")
                true
            }.onFailure {
                statusText = "切换摄像头失败: ${it.message}"
                Log.e(tag, "[PhoneRtsp][switch] preview-only failed: ${it.message}", it)
            }.getOrDefault(false)
        }

        val wasStreaming = isStreaming || camera.isStreaming
        val rotation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 90 else 0
        return runCatching {
            val surfaceView = previewSurfaceView ?: throw IllegalStateException("预览 SurfaceView 未就绪")
            Log.d(tag, "[PhoneRtsp][switch] restarting session for targetFacing=$targetFacing, wasStreaming=$wasStreaming, rotation=$rotation")
            if (camera.isStreaming) {
                Log.d(tag, "[PhoneRtsp][switch] stopStream before restart")
                camera.stopStream()
            }
            if (camera.isOnPreview) {
                Log.d(tag, "[PhoneRtsp][switch] stopPreview before restart")
                camera.stopPreview()
            }

            createRtspCamera(surfaceView, "switch_recreate")
            val newCamera = rtspServerCamera ?: throw IllegalStateException("摄像头重建失败")

            val prepared = newCamera.prepareAudio(64 * 1024, 44100, true, false, false) &&
                newCamera.prepareVideo(640, 480, 30, 1_200 * 1024, rotation)
            Log.d(tag, "[PhoneRtsp][switch] encoder prepared=$prepared")
            if (!prepared) {
                throw IllegalStateException("编码器重新初始化失败")
            }

            Log.d(tag, "[PhoneRtsp][switch] startPreview facing=$targetFacing")
            newCamera.startPreview(targetFacing)
            Log.d(tag, "[PhoneRtsp][switch] after startPreview cameraFront=${newCamera.isFrontCamera}")
            if (wasStreaming) {
                Log.d(tag, "[PhoneRtsp][switch] startStream after restart")
                newCamera.startStream()
                isStreaming = true
            } else {
                isStreaming = false
            }
            previewSurfaceReady = true
            logRtspState("switch_afterRestart", "targetFacing=$targetFacing, wasStreaming=$wasStreaming, actualFront=${newCamera.isFrontCamera}")
            statusText = if (wasStreaming) {
                if (targetFrontCamera) "已切到前置并继续推流" else "已切到后置并继续推流"
            } else {
                if (targetFrontCamera) "已切到前置摄像头" else "已切到后置摄像头"
            }
            true
        }.onFailure {
            statusText = "切换摄像头失败: ${it.message}"
            Log.e(tag, "[PhoneRtsp][switch] failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    fun stopStream() {
        val camera = rtspServerCamera ?: run {
            Log.w(tag, "[PhoneRtsp][stop] camera is null")
            statusText = "未初始化"
            return
        }
        logRtspState("stop_click")
        runCatching {
            Log.d(tag, "[PhoneRtsp][stop] stopping stream=${camera.isStreaming}, preview=${camera.isOnPreview}")
            if (camera.isStreaming) {
                camera.stopStream()
            }
            if (camera.isOnPreview) {
                camera.stopPreview()
            }
        }.onFailure {
            Log.e(tag, "[PhoneRtsp][stop] failed: ${it.message}", it)
        }
        isStreaming = false
        previewSurfaceReady = true
        logRtspState("stop_afterCall")
        statusText = "已停止"
    }

    DisposableEffect(Unit) {
        onDispose {
            stopStream()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "手机摄像头 RTSP 推流", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (isStreaming) "运行中" else "待启动",
                    color = if (isStreaming) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            factory = { viewContext ->
                Log.d(tag, "[PhoneRtsp][preview] factory create SurfaceView")
                SurfaceView(viewContext).also { surfaceView ->
                    previewSurfaceView = surfaceView
                    createRtspCamera(surfaceView, "preview_created")
                    previewSurfaceReady = true
                    logRtspState("preview_created")
                    statusText = "预览已就绪"
                }
            },
            update = { surfaceView ->
                previewSurfaceView = surfaceView
                if (rtspServerCamera == null) {
                    Log.d(tag, "[PhoneRtsp][preview] update recreate camera")
                    createRtspCamera(surfaceView, "preview_recreated")
                    previewSurfaceReady = true
                    logRtspState("preview_recreated")
                    statusText = "预览已就绪"
                }
            },
        )

        Text(
            text = "推流地址：${sanitizeRtspUrl(rtspAddress)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    val newFrontCameraState = !useFrontCamera
                    Log.d(tag, "[PhoneRtsp][ui] switch button clicked newFront=$newFrontCameraState from=$useFrontCamera")
                    val switched = restartStreamWithNewCamera(newFrontCameraState)
                    Log.d(tag, "[PhoneRtsp][ui] switch result=$switched")
                    if (switched) {
                        useFrontCamera = newFrontCameraState
                        logRtspState("ui_state_updated", "useFrontCamera=$useFrontCamera")
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = previewSurfaceReady || isStreaming,
            ) {
                Text(text = if (useFrontCamera) "切到后置" else "切到前置")
            }

            Button(
                onClick = {
                    Log.d(tag, "[PhoneRtsp][ui] stream button clicked isStreaming=$isStreaming")
                    if (isStreaming) stopStream() else startStream()
                },
                modifier = Modifier.weight(1f),
                enabled = previewSurfaceReady || isStreaming,
            ) {
                Text(text = if (isStreaming) "停止推流" else "开始推流")
            }
        }
    }
}

@Composable
fun SnapshotScreen(
    snapshots: List<RecordingFile>,
    modifier: Modifier = Modifier,
    onOpenSnapshot: (RecordingFile) -> Unit,
    onDeleteSnapshot: (RecordingFile) -> Unit,
) {
    if (snapshots.isEmpty()) {
        EmptyDarkScreen(text = "暂无快照")
        return
    }
    val groupedSnapshots = snapshots.groupBy {
        File(it.filePath).parentFile?.name ?: "未分类"
    }.toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groupedSnapshots.forEach { (day, files) ->
            item {
                Text(
                    text = day,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            items(files.chunked(2)) { rowFiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowFiles.forEach { snapshot ->
                        SnapshotCard(
                            snapshot = snapshot,
                            modifier = Modifier.weight(1f),
                            onOpen = { onOpenSnapshot(snapshot) },
                            onLongPress = { onDeleteSnapshot(snapshot) },
                        )
                    }
                    if (rowFiles.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun SnapshotCard(
    snapshot: RecordingFile,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(onOpen, onLongPress) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onLongPress() },
                )
            }
            .padding(12.dp),
    ) {
        // 使用 Coil 异步加载快照缩略图，避免主线程 decodeFile
        coil.compose.AsyncImage(
            model = java.io.File(snapshot.filePath),
            contentDescription = snapshot.fileName,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = snapshot.fileName, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = snapshot.readableFileSize, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SettingsHomeScreen(
    storagePathInput: String,
    onStoragePathChange: (String) -> Unit,
    recordingQualityInput: String,
    onRecordingQualityChange: (String) -> Unit,
    recordingDurationInput: String,
    onRecordingDurationChange: (String) -> Unit,
    appearanceThemeMode: String,
    rtmpUrlInput: String,
    onRtmpUrlChange: (String) -> Unit,
    rtmpStreamKeyInput: String,
    onRtmpStreamKeyChange: (String) -> Unit,
    rtmpUsernameInput: String,
    onRtmpUsernameChange: (String) -> Unit,
    rtmpPasswordInput: String,
    onRtmpPasswordChange: (String) -> Unit,
    rtmpAudioEnabled: Boolean,
    onRtmpAudioEnabledChange: (Boolean) -> Unit,
    rtmpAutoStreamEnabled: Boolean,
    onRtmpAutoStreamChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onThemeModeChange: (String) -> Unit,
    onClearRecordings: () -> Unit,
    onManageRtmpSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showPassword by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.background(colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 外观 ──
        item {
            SettingsSectionHeader(icon = "\u2728", title = "外观")
        }
        item {
            AppearanceSettingsScreen(
                themeMode = appearanceThemeMode,
                modifier = Modifier.fillMaxWidth(),
                onThemeModeChange = onThemeModeChange,
            )
        }

        // ── 设备选项 ──
        item {
            SettingsSectionHeader(icon = "", title = "设备选项")
        }
        item {
            SettingsGroupCard {
                StoragePathPickerRow(
                    label = "存储路径",
                    value = storagePathInput,
                    onPathChange = onStoragePathChange,
                )
                SettingsDivider()
                SettingsDropdownRow(
                    label = "录制质量",
                    options = listOf("4K", "1080p", "720p", "480p"),
                    selected = recordingQualityInput,
                    onSelected = onRecordingQualityChange,
                )
                SettingsDivider()
                SettingsTextFieldRow(
                    label = "录制时长",
                    hint = "分钟数，如 30",
                    value = recordingDurationInput,
                    onValueChange = onRecordingDurationChange,
                )
            }
        }
        item {
            BackgroundPlaybackToggleCard()
        }

        // ── RTMP 推流 ──
        item {
            SettingsSectionHeader(icon = "", title = "RTMP 推流")
        }
        item {
            SettingsGroupCard {
                SettingsTextFieldRow(
                    label = "服务器地址",
                    hint = "rtmp://your-server/live",
                    value = rtmpUrlInput,
                    onValueChange = onRtmpUrlChange,
                )
                SettingsDivider()
                SettingsTextFieldRow(
                    label = "推流密钥",
                    hint = "与服务器地址拼接为完整推流路径",
                    value = rtmpStreamKeyInput,
                    onValueChange = onRtmpStreamKeyChange,
                )
                SettingsDivider()
                SettingsTextFieldRow(
                    label = "鉴权用户名",
                    hint = "可留空",
                    value = rtmpUsernameInput,
                    onValueChange = onRtmpUsernameChange,
                )
                SettingsDivider()
                SettingsTextFieldRow(
                    label = "鉴权密码",
                    hint = "可留空",
                    value = rtmpPasswordInput,
                    onValueChange = onRtmpPasswordChange,
                    password = !showPassword,
                    trailing = {
                        Text(
                            text = if (showPassword) "隐藏" else "显示",
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showPassword = !showPassword },
                        )
                    },
                )
            }
        }
        item {
            SettingsToggleRow(
                title = "采集麦克风音频",
                subtitle = "推流时同时携带本机音频轨道",
                checked = rtmpAudioEnabled,
                onCheckedChange = onRtmpAudioEnabledChange,
            )
        }
        item {
            RtmpSettingsManagerCard(onManageClick = onManageRtmpSettings)
        }
        item {
            RtmpAutoStreamToggleCard(
                enabled = rtmpAutoStreamEnabled,
                onToggle = onRtmpAutoStreamChange
            )
        }

        // ── 操作 ──
        item {
            Spacer(modifier = Modifier.height(6.dp))
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Red)
                    .clickable(onClick = onClearRecordings)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "清理录像",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isNotEmpty()) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SettingsGroupCard(
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.surface)
            .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
fun SettingsTextFieldRow(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) colorScheme.primary else colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            if (trailing != null) trailing()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .background(colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp,
    )
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.surface)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SettingsFixedThumbSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsFixedThumbSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "settings_switch_scale",
    )
    val trackColor = animateColorAsState(
        targetValue = if (checked) colorScheme.primary else colorScheme.surfaceVariant,
        label = "settings_switch_track",
    )
    val borderColor = animateColorAsState(
        targetValue = if (checked) colorScheme.primary else colorScheme.outline.copy(alpha = 0.7f),
        label = "settings_switch_border",
    )
    val thumbColor = animateColorAsState(
        targetValue = if (checked) Color.White else colorScheme.surface,
        label = "settings_switch_thumb",
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor.value)
            .border(1.dp, borderColor.value, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 20.dp else 0.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(thumbColor.value),
        )
    }
}

@Composable
fun StoragePathPickerRow(
    label: String,
    value: String,
    onPathChange: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val path = treeUriToPath(uri)
            if (path != null) {
                onPathChange(path)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "无法解析所选目录的文件路径，请选择外部存储下的目录",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable { dirPickerLauncher.launch(null) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value.ifBlank { "点击选择存储目录" },
                color = if (value.isBlank()) colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "选择",
                color = colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SettingsDropdownRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Text(
                    text = option,
                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) colorScheme.primary
                            else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        .clickable { onSelected(option) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(text = label, color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
    ) {
        Text(text = title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RecordingRow(
    recording: RecordingFile,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = recording.fileName, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = recording.filePath, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "查看", color = LocalNvrAccent.current, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyDarkScreen(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun RtmpSettingsManagerCard(
    onManageClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.surface)
            .clickable(onClick = onManageClick)
            .padding(16.dp),
    ) {
        Text(text = "RTMP设置管理器", color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "管理或重新使用以前的RTMP推流地址和视频密钥", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RtmpAutoStreamToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.surface)
            .clickable { onToggle(!enabled) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "询问RTMP设置", color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "从保存的列表中选择RTMP设置而不是默认设置，并且\"打开后自动推流\"选项将使用最后选择的设置",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SettingsFixedThumbSwitch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
fun BackgroundPlaybackToggleCard() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("com.example.nvr_preferences", 0)
    }
    val colorScheme = MaterialTheme.colorScheme
    // 默认关闭：应用退到后台后自动停止播放，避免占用带宽/电量。
    var enabled by remember { mutableStateOf(prefs.getBoolean("allow_background_playback", false)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.surface)
            .clickable {
                val next = !enabled
                enabled = next
                prefs.edit().putBoolean("allow_background_playback", next).apply()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "允许后台播放",
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "关闭时，应用切到后台会自动停止画面拉流；开启后将在后台继续播放",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SettingsFixedThumbSwitch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean("allow_background_playback", it).apply()
            },
        )
    }
}

/**
 * 构建 RTMP 推流目标 URL
 */
fun buildRtmpPushTargetUrl(serverUrl: String, streamKey: String, username: String = "", password: String = ""): String {
    val normalizedServer = serverUrl.trim()
    val normalizedStreamKey = streamKey.trim().trimStart('/')
    if (normalizedServer.isBlank()) return ""

    val protocol = if (normalizedServer.startsWith("rtmps://", ignoreCase = true)) "rtmps://" else "rtmp://"
    val serverWithoutProtocol = normalizedServer
        .substringAfter("://", normalizedServer)
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')

    val existingPath = serverWithoutProtocol.substringAfter('/', "")
    val authority = serverWithoutProtocol.substringBefore('/')
    val hostAndPort = authority.substringAfter('@', authority)
    val host = hostAndPort.substringBefore(':')
    val portSuffix = hostAndPort.substringAfter(':', "")
    val normalizedHost = normalizeNetworkHost(host)
    val normalizedAuthority = buildString {
        if (username.isNotBlank() && password.isNotBlank()) {
            append(username)
            append(':')
            append(password)
            append('@')
        }
        append(normalizedHost.ifBlank { host })
        if (portSuffix.isNotBlank()) {
            append(':')
            append(portSuffix)
        }
    }
    val baseUrl = buildString {
        append(protocol)
        append(normalizedAuthority)
        if (existingPath.isNotBlank()) {
            append('/')
            append(existingPath.trim('/'))
        }
    }

    return if (normalizedStreamKey.isNotBlank()) {
        "$baseUrl/$normalizedStreamKey"
    } else {
        baseUrl
    }
}

@Composable
fun PhoneRtmpPushStreamCard(
    prefs: android.content.SharedPreferences,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val tag = "NvrMainScreen"
    var rtmpCamera by remember { mutableStateOf<com.pedro.library.rtmp.RtmpCamera1?>(null) }
    var previewSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var useFrontCamera by remember(prefs) { mutableStateOf(prefs.getBoolean("rtmp_push_use_front_camera", false)) }
    var statusText by remember { mutableStateOf("未启动") }
    var previewSurfaceReady by remember { mutableStateOf(false) }
    val serverUrl = prefs.getString("rtmp_push_url", "").orEmpty().trim()
    val streamKey = prefs.getString("rtmp_push_stream_key", "").orEmpty().trim()
    val authUsername = prefs.getString("rtmp_push_username", "").orEmpty().trim()
    val authPassword = prefs.getString("rtmp_push_password", "").orEmpty()
    val audioEnabled = prefs.getBoolean("rtmp_push_audio_enabled", true)
    val pushTargetUrl = remember(serverUrl, streamKey, authUsername, authPassword) {
        buildRtmpPushTargetUrl(serverUrl, streamKey, authUsername, authPassword)
    }

    fun postStatus(message: String) {
        Log.d(tag, "[PhoneRtmp][status] $message")
        mainHandler.post { statusText = message }
    }

    val connectChecker = remember {
        object : com.pedro.common.ConnectChecker {
            override fun onConnectionStarted(url: String) {
                Log.d(tag, "[PhoneRtmp][connect] started url=${sanitizeRtspUrl(url)}")
                postStatus("连接中: ${sanitizeRtspUrl(url)}")
            }

            override fun onConnectionSuccess() {
                Log.d(tag, "[PhoneRtmp][connect] success")
                mainHandler.post {
                    isStreaming = true
                    statusText = "推流中"
                }
            }

            override fun onConnectionFailed(reason: String) {
                Log.e(tag, "[PhoneRtmp][connect] failed reason=$reason")
                mainHandler.post {
                    isStreaming = false
                    statusText = "连接失败: $reason"
                }
            }

            override fun onDisconnect() {
                Log.d(tag, "[PhoneRtmp][connect] disconnect")
                mainHandler.post {
                    isStreaming = false
                    statusText = "已停止"
                }
            }

            override fun onAuthError() {
                Log.e(tag, "[PhoneRtmp][connect] auth error")
                postStatus("鉴权失败")
            }

            override fun onAuthSuccess() {
                Log.d(tag, "[PhoneRtmp][connect] auth success")
                postStatus("鉴权成功")
            }

            override fun onNewBitrate(bitrate: Long) {
                Log.d(tag, "[PhoneRtmp][connect] bitrate=${bitrate / 1000} kbps")
                mainHandler.post {
                    statusText = "推流中 · ${bitrate / 1000} kbps"
                }
            }
        }
    }

    fun createRtmpCamera(surfaceView: SurfaceView) {
        rtmpCamera = com.pedro.library.rtmp.RtmpCamera1(surfaceView, connectChecker)
    }

    fun prepareEncoders(camera: com.pedro.library.rtmp.RtmpCamera1, rotation: Int): Boolean {
        val videoPrepared = runCatching {
            camera.prepareVideo(640, 480, 30, 1_200 * 1024, rotation)
        }.getOrDefault(false)
        
        if (!videoPrepared) return false
        
        if (audioEnabled) {
            val audioPrepared = runCatching {
                camera.prepareAudio(64 * 1024, 44100, true, false, false)
            }.getOrDefault(false)
            return audioPrepared
        }
        
        return true
    }

    fun startStream() {
        val camera = rtmpCamera
        if (pushTargetUrl.isBlank()) {
            statusText = "请先在设置中配置 RTMP 服务器地址"
            Toast.makeText(context, context.getString(R.string.toast_rtmp_address_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (camera == null) {
            Log.w(tag, "[PhoneRtmp][startStream] camera is null")
            statusText = "预览尚未就绪"
            return
        }
        if (isStreaming || camera.isStreaming) {
            Log.w(tag, "[PhoneRtmp][startStream] already streaming")
            statusText = "已经在推流"
            return
        }

        val rotation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 90 else 0
        Log.d(tag, "[PhoneRtmp][startStream] preparing encoder rotation=$rotation front=$useFrontCamera")
        
        if (!prepareEncoders(camera, rotation)) {
            Log.e(tag, "[PhoneRtmp][startStream] encoder prepare failed")
            statusText = "编码器初始化失败"
            return
        }

        runCatching {
            Log.d(tag, "[PhoneRtmp][startStream] startPreview facing=${if (useFrontCamera) "FRONT" else "BACK"}")
            camera.startPreview(if (useFrontCamera) com.pedro.encoder.input.video.CameraHelper.Facing.FRONT else com.pedro.encoder.input.video.CameraHelper.Facing.BACK)
            Log.d(tag, "[PhoneRtmp][startStream] startStream to $pushTargetUrl")
            camera.startStream(pushTargetUrl)
            previewSurfaceReady = true
            statusText = "正在启动推流..."
        }.onFailure {
            isStreaming = false
            Log.e(tag, "[PhoneRtmp][startStream] failed: ${it.message}", it)
            statusText = "启动失败: ${it.message}"
        }
    }

    fun restartStreamWithNewCamera(targetFrontCamera: Boolean): Boolean {
        val camera = rtmpCamera ?: return false
        val targetFacing = if (targetFrontCamera) com.pedro.encoder.input.video.CameraHelper.Facing.FRONT else com.pedro.encoder.input.video.CameraHelper.Facing.BACK
        
        if (!camera.isOnPreview && !camera.isStreaming) {
            return runCatching {
                Log.d(tag, "[PhoneRtmp][switch] no active preview/stream, startPreview only")
                camera.startPreview(targetFacing)
                previewSurfaceReady = true
                statusText = if (targetFrontCamera) "已切到前置摄像头" else "已切到后置摄像头"
                true
            }.onFailure {
                statusText = "切换摄像头失败: ${it.message}"
                Log.e(tag, "[PhoneRtmp][switch] preview-only failed: ${it.message}", it)
            }.getOrDefault(false)
        }

        val wasStreaming = isStreaming || camera.isStreaming
        val rotation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 90 else 0
        return runCatching {
            val surfaceView = previewSurfaceView ?: throw IllegalStateException("预览 SurfaceView 未创建")
            Log.d(tag, "[PhoneRtmp][switch] restarting session for targetFacing=$targetFacing, wasStreaming=$wasStreaming, rotation=$rotation")
            if (camera.isStreaming) {
                Log.d(tag, "[PhoneRtmp][switch] stopStream before restart")
                camera.stopStream()
            }
            if (camera.isOnPreview) {
                Log.d(tag, "[PhoneRtmp][switch] stopPreview before restart")
                camera.stopPreview()
            }

            createRtmpCamera(surfaceView)
            val newCamera = rtmpCamera ?: throw IllegalStateException("摄像头重建失败")

            if (!prepareEncoders(newCamera, rotation)) {
                throw IllegalStateException("编码器重新初始化失败")
            }

            Log.d(tag, "[PhoneRtmp][switch] startPreview facing=$targetFacing")
            newCamera.startPreview(targetFacing)
            if (wasStreaming) {
                Log.d(tag, "[PhoneRtmp][switch] startStream after restart")
                newCamera.startStream(pushTargetUrl)
                isStreaming = true
            } else {
                isStreaming = false
            }
            previewSurfaceReady = true
            statusText = if (wasStreaming) {
                if (targetFrontCamera) "已切到前置并继续推流" else "已切到后置并继续推流"
            } else {
                if (targetFrontCamera) "已切到前置摄像头" else "已切到后置摄像头"
            }
            true
        }.onFailure {
            statusText = "切换摄像头失败: ${it.message}"
            Log.e(tag, "[PhoneRtmp][switch] failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    fun stopStream() {
        val camera = rtmpCamera ?: return
        runCatching {
            if (camera.isStreaming) camera.stopStream()
            if (camera.isOnPreview) camera.stopPreview()
        }.onFailure {
            Log.e(tag, "[PhoneRtmp][stop] failed: ${it.message}", it)
        }
        isStreaming = false
        previewSurfaceReady = true
        statusText = "已停止"
    }

    DisposableEffect(Unit) {
        onDispose {
            stopStream()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RTMP 推流到服务器",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (isStreaming) "运行中" else "待启动",
                    color = if (isStreaming) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            factory = { viewContext ->
                SurfaceView(viewContext).also { surfaceView ->
                    previewSurfaceView = surfaceView
                    createRtmpCamera(surfaceView)
                    previewSurfaceReady = true
                    statusText = if (pushTargetUrl.isBlank()) "请先配置 RTMP 地址" else "预览已就绪"
                }
            },
            update = { surfaceView ->
                previewSurfaceView = surfaceView
                if (rtmpCamera == null) {
                    createRtmpCamera(surfaceView)
                    previewSurfaceReady = true
                    statusText = if (pushTargetUrl.isBlank()) "请先配置 RTMP 地址" else "预览已就绪"
                }
            },
        )

        Text(
            text = if (pushTargetUrl.isBlank()) "目标地址：请先在设置中配置 RTMP 服务器" else "目标地址：${sanitizeRtspUrl(pushTargetUrl)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    val newFrontCameraState = !useFrontCamera
                    val switched = restartStreamWithNewCamera(newFrontCameraState)
                    if (switched) {
                        useFrontCamera = newFrontCameraState
                        prefs.edit().putBoolean("rtmp_push_use_front_camera", newFrontCameraState).apply()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = previewSurfaceReady || isStreaming,
            ) {
                Text(text = if (useFrontCamera) "切到后置" else "切到前置")
            }

            Button(
                onClick = {
                    if (isStreaming) stopStream() else startStream()
                },
                modifier = Modifier.weight(1f),
                enabled = previewSurfaceReady || isStreaming,
            ) {
                Text(text = if (isStreaming) "停止推流" else "开始推流")
            }
        }
    }
}
