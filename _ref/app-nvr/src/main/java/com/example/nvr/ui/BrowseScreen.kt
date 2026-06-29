package com.example.nvr.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nvr.R
import com.example.nvr.network.NetworkDiscoveryService
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VLC 风格的"浏览"页面：单页滚动布局，包含三个分组（收藏、存储设备、本地网络）。
 * 使用白色卡片、橙色标题、圆角设计，像素级复刻 VLC Android UI。
 */
@Composable
fun BrowseScreen(
    prefs: SharedPreferences,
    modifier: Modifier = Modifier,
    onPlayUrl: (title: String, url: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val favorites = remember { mutableStateListOf<BrowseFavorite>() }
    val networkSources = remember { mutableStateListOf<BrowseNetworkSource>() }
    val localVideos = remember { mutableStateListOf<BrowseLocalVideo>() }
    // 自动发现的设备（SMB / DLNA / UPnP）
    val discoveredDevices = remember { mutableStateListOf<NetworkDiscoveryService.DiscoveredDevice>() }
    var isScanning by remember { mutableStateOf(false) }
    val discoveryService = remember { NetworkDiscoveryService(context) }

    LaunchedEffect(Unit) {
        favorites.clear()
        favorites.addAll(loadBrowseFavorites(prefs))
        networkSources.clear()
        networkSources.addAll(loadBrowseNetworkSources(prefs))

        // 自动扫描本地视频
        val scanned = withContext(Dispatchers.IO) { scanLocalVideos(context) }
        localVideos.addAll(scanned)
    }

    /** 执行 SMB / UPnP 网络发现扫描 */
    fun startScan() {
        if (isScanning) return
        isScanning = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { discoveryService.discoverAll() }
            discoveredDevices.clear()
            discoveredDevices.addAll(found)
            isScanning = false
            val msg = if (found.isEmpty()) context.getString(R.string.browse_no_devices_found)
                else context.getString(R.string.browse_devices_found, found.size)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background),
    ) {
        // VLC 风格顶部栏
        BrowseTopBar(isScanning = isScanning, onScan = ::startScan)
        
        // 单页滚动内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // 收藏分组
            item {
                BrowseSectionHeader(title = stringResource(R.string.browse_section_favorites))
            }
            item {
                if (favorites.isEmpty()) {
                    EmptySection(text = stringResource(R.string.browse_empty_favorites))
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(favorites) { fav ->
                            BrowseFolderCard(
                                title = fav.title,
                                subtitle = if (fav.source == BrowseSourceKind.LOCAL_FILE) stringResource(R.string.browse_subtitle_local_file) else stringResource(R.string.browse_subtitle_network_source),
                                icon = if (fav.source == BrowseSourceKind.LOCAL_FILE) "📁" else "🌐",
                                onTap = { onPlayUrl(fav.title, fav.url) },
                                onMore = {
                                    val next = removeBrowseFavorite(prefs, fav.id)
                                    favorites.clear()
                                    favorites.addAll(next)
                                },
                            )
                        }
                    }
                }
            }
            
            // 存储设备分组
            item {
                BrowseSectionHeader(title = stringResource(R.string.browse_section_storage))
            }
            item {
                if (localVideos.isEmpty()) {
                    EmptySection(text = stringResource(R.string.browse_empty_local_videos))
                } else {
                    val grouped = groupLocalVideosByFolder(localVideos)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(grouped.entries.toList()) { (folder, videos) ->
                            BrowseFolderCard(
                                title = folder,
                                subtitle = stringResource(R.string.browse_video_count, videos.size),
                                icon = "📁",
                                onTap = {
                                    // 播放第一个视频
                                    videos.firstOrNull()?.let { v ->
                                        onPlayUrl(v.displayName, fileUrlForLocalVideo(v))
                                    }
                                },
                                onMore = {
                                    // 添加到收藏
                                    videos.firstOrNull()?.let { v ->
                                        val fav = BrowseFavorite(
                                            id = UUID.randomUUID().toString(),
                                            title = v.displayName,
                                            url = fileUrlForLocalVideo(v),
                                            source = BrowseSourceKind.LOCAL_FILE,
                                            addedAtMs = System.currentTimeMillis(),
                                        )
                                        val next = addBrowseFavorite(prefs, fav)
                                        favorites.clear()
                                        favorites.addAll(next)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            
            // 本地网络分组
            item {
                BrowseSectionHeader(title = stringResource(R.string.browse_section_local_network))
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    // 自动发现的设备（SMB / DLNA / UPnP）
                    if (discoveredDevices.isNotEmpty()) {
                        BrowseSectionHeader(title = stringResource(R.string.browse_section_discovered, discoveredDevices.size))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(discoveredDevices) { dev ->
                                val protoLabel = when (dev.type) {
                                    NetworkDiscoveryService.DeviceType.SMB -> "smb"
                                    NetworkDiscoveryService.DeviceType.DLNA -> "dlna"
                                    NetworkDiscoveryService.DeviceType.UPNP_MEDIA_SERVER -> "upnp"
                                    NetworkDiscoveryService.DeviceType.RTSP_CAMERA -> "rtsp"
                                }
                                BrowseSmbCard(
                                    title = dev.name,
                                    subtitle = dev.host.ifBlank { dev.url },
                                    protocolLabel = protoLabel,
                                    onTap = { onPlayUrl(dev.name, dev.url) },
                                    onMore = {
                                        val src = BrowseNetworkSource(
                                            id = UUID.randomUUID().toString(),
                                            title = dev.name,
                                            url = dev.url,
                                            addedAtMs = System.currentTimeMillis(),
                                        )
                                        val next = addBrowseNetworkSource(prefs, src)
                                        networkSources.clear()
                                        networkSources.addAll(next)
                                    },
                                    onAddFavorite = {
                                        val fav = BrowseFavorite(
                                            id = UUID.randomUUID().toString(),
                                            title = dev.name,
                                            url = dev.url,
                                            source = BrowseSourceKind.NETWORK_URL,
                                            addedAtMs = System.currentTimeMillis(),
                                        )
                                        val next = addBrowseFavorite(prefs, fav)
                                        favorites.clear()
                                        favorites.addAll(next)
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 添加网络源表单
                    NetworkSourceForm(
                        onAdd = { title, url, user, pwd ->
                            val src = BrowseNetworkSource(
                                id = UUID.randomUUID().toString(),
                                title = title.ifBlank { url },
                                url = url,
                                username = user,
                                password = pwd,
                                addedAtMs = System.currentTimeMillis(),
                            )
                            val next = addBrowseNetworkSource(prefs, src)
                            networkSources.clear()
                            networkSources.addAll(next)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 网络源列表
                    if (networkSources.isEmpty()) {
                        EmptySection(text = stringResource(R.string.browse_empty_network_sources))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(networkSources) { src ->
                                BrowseSmbCard(
                                    title = src.title,
                                    subtitle = src.url,
                                    onTap = { onPlayUrl(src.title, applyAuth(src)) },
                                    onMore = {
                                        val next = removeBrowseNetworkSource(prefs, src.id)
                                        networkSources.clear()
                                        networkSources.addAll(next)
                                    },
                                    onAddFavorite = {
                                        val fav = BrowseFavorite(
                                            id = UUID.randomUUID().toString(),
                                            title = src.title,
                                            url = applyAuth(src),
                                            source = BrowseSourceKind.NETWORK_URL,
                                            addedAtMs = System.currentTimeMillis(),
                                        )
                                        val next = addBrowseFavorite(prefs, fav)
                                        favorites.clear()
                                        favorites.addAll(next)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun applyAuth(src: BrowseNetworkSource): String {
    if (src.username.isBlank() && src.password.isBlank()) return src.url
    val schemeIdx = src.url.indexOf("://")
    if (schemeIdx < 0) return src.url
    val scheme = src.url.substring(0, schemeIdx + 3)
    val rest = src.url.substring(schemeIdx + 3)
    if (rest.contains("@")) return src.url
    val userInfo = if (src.password.isNotBlank()) "${src.username}:${src.password}@" else "${src.username}@"
    return "$scheme$userInfo$rest"
}

/**
 * VLC 风格顶部栏：橙色 VLC 图标 + "VLC" 文字 + 更多按钮
 */
@Composable
private fun BrowseTopBar(
    isScanning: Boolean = false,
    onScan: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // VLC 橙色图标（使用 emoji 代替）
        Text(
            text = "🎥",
            fontSize = 28.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = "VLC",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        // 网络扫描按钮
        IconButton(onClick = onScan, enabled = !isScanning) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = stringResource(R.string.browse_scan_local_network),
                    tint = colorScheme.primary,
                )
            }
        }
        IconButton(onClick = { /* 更多选项 */ }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.browse_more),
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 橙色分组标题
 */
@Composable
private fun BrowseSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * 文件夹卡片（用于收藏和存储设备）
 */
@Composable
private fun BrowseFolderCard(
    title: String,
    subtitle: String,
    icon: String,
    onTap: () -> Unit,
    onMore: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = icon,
                    fontSize = 32.sp,
                )
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.browse_more),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * SMB 网络设备卡片（带 "smb" 标签）
 */
@Composable
private fun BrowseSmbCard(
    title: String,
    subtitle: String,
    protocolLabel: String = "smb",
    onTap: () -> Unit,
    onMore: () -> Unit,
    onAddFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // 协议标签（smb / dlna / upnp / rtsp，按设备类型动态显示）
                Box(
                    modifier = Modifier
                        .background(colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = protocolLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                    )
                }
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.browse_more),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🌐",
                fontSize = 32.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 空白状态提示
 */
@Composable
private fun EmptySection(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 添加网络源表单
 */
@Composable
private fun NetworkSourceForm(
    onAdd: (title: String, url: String, username: String, password: String) -> Unit,
) {
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var userInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.network_form_add_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            BrowseTextInput(
                value = titleInput,
                hint = stringResource(R.string.network_form_hint_name),
                onValueChange = { titleInput = it },
            )
            BrowseTextInput(
                value = urlInput,
                hint = "rtsp://, http://, rtmp://, smb:// ...",
                onValueChange = { urlInput = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrowseTextInput(
                    value = userInput,
                    hint = stringResource(R.string.network_form_hint_username),
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                )
                BrowseTextInput(
                    value = passwordInput,
                    hint = stringResource(R.string.network_form_hint_password),
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    if (urlInput.isBlank()) return@Button
                    onAdd(titleInput.trim(), urlInput.trim(), userInput.trim(), passwordInput)
                    titleInput = ""
                    urlInput = ""
                    userInput = ""
                    passwordInput = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.network_form_save))
            }
            Text(
                text = stringResource(R.string.network_form_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrowseTextInput(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = hint, color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colorScheme.onSurface, fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
