package com.example.nvr.ui

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.nvr.model.CameraDevice
import com.example.nvr.model.RecordingFile
import kotlinx.coroutines.delay
import com.example.nvr.ui.theme.LocalNvrAccent
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun NvrMainTopBar(
    selectedTab: Int,
    onEditClick: () -> Unit,
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val topBarBackground = MaterialTheme.colorScheme.background
    val titleColor = MaterialTheme.colorScheme.onBackground
    val accentColor = LocalNvrAccent.current
    val addButtonTextColor = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(topBarBackground)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedTab == 0) "编辑" else "",
                color = accentColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(enabled = selectedTab == 0, onClick = onEditClick),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "☰",
                    color = accentColor,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clickable(onClick = onMenuClick),
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(onClick = onAddClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "+", color = addButtonTextColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = when (selectedTab) {
                0 -> "摄像机"
                1 -> "仪表板"
                2 -> "活动"
                3 -> "快照"
                else -> "设置"
            },
            color = titleColor,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CameraHomeScreen(
    cameras: List<CameraDevice>,
    snapshots: List<RecordingFile>,
    selectedCameraId: String?,
    playingCameraId: String?,
    prefs: android.content.SharedPreferences,
    modifier: Modifier = Modifier,
    onCameraClick: (CameraDevice) -> Unit,
    onCameraLongPress: (CameraDevice) -> Unit,
    onAddCamera: () -> Unit,
    onSeedDemo: () -> Unit = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val contentColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = LocalNvrAccent.current
    val addButtonTextColor = MaterialTheme.colorScheme.onPrimary
    if (cameras.isEmpty()) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "◉", color = accentColor, style = MaterialTheme.typography.displaySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "暂无摄像机", color = contentColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "添加 RTSP / ONVIF 设备，或先看演示", color = secondaryTextColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(accentColor)
                            .clickable(onClick = onAddCamera)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(text = "添加摄像机", color = addButtonTextColor, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onSeedDemo)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(text = "演示模式", color = contentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    var gridColumns by remember { mutableStateOf(2) }
    val pillBg = MaterialTheme.colorScheme.surfaceVariant
    val pillActiveBg = MaterialTheme.colorScheme.secondaryContainer
    val pillTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pillActiveTextColor = contentColor

    Column(
        modifier = modifier.background(backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "在线 ${cameras.size}",
                color = pillTextColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(pillBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(1 to "▢", 2 to "⊞", 3 to "⊟").forEach { (cols, icon) ->
                    val active = gridColumns == cols
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) pillActiveBg else Color.Transparent)
                            .clickable { gridColumns = cols }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = icon,
                            color = if (active) pillActiveTextColor else pillTextColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (gridColumns >= 3) 14.dp else 18.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
        ) {
            items(cameras.size) { index ->
                val device = cameras[index]
                val latestSnapshot = snapshots.firstOrNull { it.cameraName == snapshotCameraKey(device.name.ifBlank { device.id }) }
                CameraGridCard(
                    camera = device,
                    badge = cameraBadge(device, prefs),
                    isSelected = selectedCameraId == device.id,
                    isPlaying = playingCameraId == device.id,
                    snapshot = latestSnapshot,
                    compact = gridColumns >= 3,
                    onClick = { onCameraClick(device) },
                    onLongPress = { onCameraLongPress(device) },
                )
            }
        }
    }
}

@Composable
fun CameraGridCard(
    camera: CameraDevice,
    badge: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    snapshot: RecordingFile?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    compact: Boolean = false,
) {
    val titleColor = MaterialTheme.colorScheme.onBackground
    val badgeBackground = MaterialTheme.colorScheme.surfaceVariant
    val badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val statusLabel: String
    val statusDotColor: Color
    when {
        isPlaying -> {
            statusLabel = "LIVE"
            statusDotColor = MaterialTheme.colorScheme.error
        }
        snapshot != null -> {
            statusLabel = "STANDBY"
            statusDotColor = LocalNvrAccent.current
        }
        else -> {
            statusLabel = "OFFLINE"
            statusDotColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(camera.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (compact) 1.35f else 1.55f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            if (isPlaying) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        )
                    )
                ),
        ) {
            if (snapshot != null) {
                // 使用 Coil 异步加载缩略图，避免主线程 decodeFile
                coil.compose.AsyncImage(
                    model = java.io.File(snapshot.filePath),
                    contentDescription = "snapshot",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (snapshot != null) 0.06f else 0.12f)),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusDotColor),
                )
                Text(
                    text = statusLabel,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isPlaying) {
                Text(
                    text = "REC",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = camera.name.ifBlank { "未命名" },
                color = titleColor,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(badgeBackground)
                    .padding(
                        horizontal = if (compact) 10.dp else 14.dp,
                        vertical = if (compact) 4.dp else 6.dp,
                    ),
            ) {
                Text(
                    text = badge,
                    color = badgeTextColor,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun PlaybackFullscreenScreen(
    camera: CameraDevice,
    badge: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onBindPlayerLayout: (VLCVideoLayout) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onMute: () -> Unit,
    onSnapshot: () -> Unit,
    onMore: () -> Unit,
    onToggleRecord: () -> Unit = {},
    isVideoReadyProvider: () -> Boolean = { false },
    isMuted: Boolean = false,
    isRecording: Boolean = false,
) {
    val topBarAccent = LocalNvrAccent.current
    val context = LocalContext.current

    // 仿照 _ref/vlc-android VideoPlayerActivity onCreate：
    // 进入全屏播放页 → 锁定横屏（sensor landscape）+ 沉浸式；
            // 退出时还原方向与系统栏，避免状态泄漏到主页。
    DisposableEffect(Unit) {
        val activity = context.findActivityCompat()
        val originalOrientation = activity?.requestedOrientation
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (activity != null) {
                activity.requestedOrientation =
                    originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity.window
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 控件浮层可见性与自动淑出：仿照 vlc-android overlayDelegate.showOverlayTimeout。
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    var isVideoReady by remember(camera.id) { mutableStateOf(false) }
    val latestIsVideoReadyProvider by rememberUpdatedState(isVideoReadyProvider)
    LaunchedEffect(camera.id) {
        isVideoReady = false
        // 50ms 轮询：一旦 VLC 触发首帧 Vout（streamManager.hasVideoOutput），loading 几乎立刻消失。
        // 最多兜底 15 秒，避免真正失败时一直转圈。
        val deadline = System.currentTimeMillis() + 15_000L
        while (!isVideoReady && System.currentTimeMillis() < deadline) {
            delay(50)
            if (latestIsVideoReadyProvider()) {
                isVideoReady = true
            }
        }
        if (!isVideoReady) {
            // 超时后也隐藏，交给外层的"未出画面"诊断 Toast 去提示。
            isVideoReady = true
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            isVideoReady = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        // 视频层：铺满整个屏幕
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VLCVideoLayout(ctx).also {
                    it.keepScreenOn = true
                    onBindPlayerLayout(it)
                }
            },
            update = { layout -> onBindPlayerLayout(layout) },
        )

        // Loading 遮罩
        if (!isVideoReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = topBarAccent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "正在加载画面…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "高码率视频首次缓冲可能需要几秒",
                        color = Color(0xFFBEBEC2),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // 浮层控件：顺点击出现/隐藏，播放中 4s 后自动淑出
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xCC000000), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "‹",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.clickable(onClick = onBack),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = camera.name.ifBlank { badge },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = badge,
                        color = Color(0xFFBEBEC2),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // 底部控件栏
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaybackToolbarIconButton(
                                icon = Icons.Rounded.CameraAlt,
                                contentDescription = "快照",
                                onClick = onSnapshot,
                            )
                            PlaybackRecordButton(
                                isRecording = isRecording,
                                onClick = onToggleRecord,
                            )
                            PlaybackToolbarIconButton(
                                icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (isMuted) "恢复声音" else "静音",
                                onClick = onMute,
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        PlaybackToolbarIconButton(
                            icon = Icons.Rounded.MoreHoriz,
                            contentDescription = "更多",
                            onClick = onMore,
                            outlined = true,
                        )
                    }

                    PlaybackPrimaryButton(
                        isPlaying = isPlaying,
                        modifier = Modifier.align(Alignment.Center),
                        onClick = onTogglePlayPause,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackPrimaryButton(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(LocalNvrAccent.current)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(if (isPlaying) 30.dp else 34.dp),
        )
    }
}

@Composable
private fun PlaybackToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
) {
    val shape = CircleShape
    val buttonModifier = modifier
        .size(44.dp)
        .clip(shape)
        .then(if (outlined) Modifier.border(width = 2.2.dp, color = Color.White, shape = shape) else Modifier)

    IconButton(
        onClick = { onClick?.invoke() },
        modifier = buttonModifier,
        enabled = onClick != null,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (outlined) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(if (outlined) 22.dp else 24.dp),
        )
    }
}

@Composable
private fun PlaybackRecordButton(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 录制中：实心红圆 + 脉冲；未录制：空心红圆环。
    val recordColor = MaterialTheme.colorScheme.error
    val alpha by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0.85f,
        animationSpec = if (isRecording) infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ) else tween(200),
        label = "recordPulse",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(recordColor.copy(alpha = alpha)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.5.dp, recordColor, CircleShape),
            )
        }
    }
}

@Composable
fun NvrBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface
    val selectedColor = colorScheme.primary
    val unselectedColor = colorScheme.onSurfaceVariant
    val items = listOf(
        Triple("摄像机", Icons.Rounded.Videocam, 0),
        Triple("浏览", Icons.Rounded.Explore, 1),
        Triple("活动", Icons.Rounded.Timeline, 2),
        Triple("快照", Icons.Rounded.PhotoCamera, 3),
        Triple("设置", Icons.Rounded.Settings, 4),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, icon, index) ->
            val selected = selectedTab == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) selectedColor else unselectedColor,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (selected) selectedColor else unselectedColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun CameraActionOverlay(
    camera: CameraDevice,
    badge: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.42f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 110.dp)
                .fillMaxWidth(0.72f),
        ) {
            CameraGridCard(
                camera = camera,
                badge = badge,
                isSelected = true,
                isPlaying = false,
                snapshot = null,
                onClick = {},
                onLongPress = {},
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                ActionMenuItem(label = "编辑", icon = "⚙", onClick = onEdit)
                DeviceDividerDark()
                ActionMenuItem(label = "复制", icon = "⊞", onClick = onCopy)
                DeviceDividerDark()
                ActionMenuItem(label = "删除", icon = "🗑", onClick = onDelete)
            }
        }
    }
}

@Composable
fun ActionMenuItem(
    label: String,
    icon: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        Text(text = icon, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
    }
}
