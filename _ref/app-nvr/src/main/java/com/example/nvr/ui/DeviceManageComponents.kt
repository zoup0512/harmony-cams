package com.example.nvr.ui

import com.example.nvr.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nvr.model.CameraDevice
import com.example.nvr.ui.theme.LocalNvrAccent

enum class DeviceFlowStep {
    Landing,
    Form,
    Verifying,
}

data class VerificationPreview(
    val name: String,
    val primaryUrl: String,
    val streamScheme: String,
    val host: String,
    val port: String,
    val resolution: String,
    val codec: String,
    val streamPath: String,
)

@Composable
fun AddCameraLandingScreen(
    modifier: Modifier = Modifier,
    onAddLocalCamera: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "网络搜索", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⌕", color = LocalNvrAccent.current, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "正在搜索网络...", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "搜索并自动连接网络上的摄像头。没有看到您要找的东西？进行深度扫描",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp),
        ) {
            AddCameraOptionCard(
                icon = "+",
                iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = LocalNvrAccent.current,
                title = stringResource(R.string.device_add_local_camera),
                subtitle = stringResource(R.string.device_add_local_camera_subtitle),
                onClick = onAddLocalCamera,
            )
        }
    }
}

@Composable
fun AddCameraOptionCard(
    icon: String,
    iconBackground: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon, color = iconColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun AddCameraFormScreen(
    modifier: Modifier = Modifier,
    manageName: String,
    onManageNameChange: (String) -> Unit,
    manageStreamScheme: String,
    onManageStreamSchemeChange: (String) -> Unit,
    manageHost: String,
    onManageHostChange: (String) -> Unit,
    managePort: String,
    onManagePortChange: (String) -> Unit,
    manageStreamPath: String,
    onManageStreamPathChange: (String) -> Unit,
    manageLoginEnabled: Boolean,
    onManageLoginEnabledChange: (Boolean) -> Unit,
    manageUsername: String,
    onManageUsernameChange: (String) -> Unit,
    managePassword: String,
    onManagePasswordChange: (String) -> Unit,
    managePasswordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    forceHttpSoftwareDecode: Boolean,
    onForceHttpSoftwareDecodeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val selectedScheme = when (PlaybackStreamType.fromScheme(manageStreamScheme) ?: PlaybackStreamType.RTSP) {
        PlaybackStreamType.RTMP -> PlaybackStreamType.HTTP
        else -> PlaybackStreamType.fromScheme(manageStreamScheme) ?: PlaybackStreamType.RTSP
    }
    val previewUrl = buildPlaybackUrl(
        streamType = selectedScheme,
        host = manageHost.trim(),
        port = managePort.trim().ifEmpty { defaultPlaybackPort(selectedScheme) },
        path = manageStreamPath.trim().ifEmpty { "stream1" },
        loginEnabled = manageLoginEnabled,
        username = manageUsername.trim(),
        password = managePassword,
    )
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                DarkFormCard {
                    DarkInputRow(label = "命名", value = manageName, placeholder = "命名", onValueChange = onManageNameChange)
                    DeviceDividerDark()
                    DarkStaticRow(label = "图标", value = "IPCams", trailing = "◉")
                }
            }
            item {
                Text(text = "URL", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            item {
                DarkFormCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "方案", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(98.dp))
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActivityStreamOptionChip(
                                title = "RTSP",
                                selected = selectedScheme == PlaybackStreamType.RTSP,
                                modifier = Modifier.weight(1f),
                                onClick = { onManageStreamSchemeChange(PlaybackStreamType.RTSP.scheme) },
                            )
                            ActivityStreamOptionChip(
                                title = "HTTP",
                                selected = selectedScheme == PlaybackStreamType.HTTP,
                                modifier = Modifier.weight(1f),
                                onClick = { onManageStreamSchemeChange(PlaybackStreamType.HTTP.scheme) },
                            )
                        }
                    }
                    DeviceDividerDark()
                    DarkInputRow(label = "主机 / IP / 域名", value = manageHost, placeholder = "cam.example.com", onValueChange = onManageHostChange, trailing = "⌕")
                    DeviceDividerDark()
                    DarkInputRow(label = "端口", value = managePort, placeholder = defaultPlaybackPort(selectedScheme), onValueChange = onManagePortChange)
                    DeviceDividerDark()
                    DarkInputRow(label = "路径", value = manageStreamPath, placeholder = "stream1", onValueChange = onManageStreamPathChange)
                }
            }
            item {
                Text(
                    text = "IPCams 将连接在 ${sanitizePlaybackUrl(previewUrl)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Text(text = "登录", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            item {
                DarkFormCard {
                    DarkSwitchRow(label = "已启用", checked = manageLoginEnabled, onCheckedChange = onManageLoginEnabledChange)
                    DeviceDividerDark()
                    DarkInputRow(label = "用户名", value = manageUsername, placeholder = "admin", onValueChange = onManageUsernameChange)
                    DeviceDividerDark()
                    DarkInputRow(
                        label = "密码",
                        value = managePassword,
                        placeholder = "密码",
                        onValueChange = onManagePasswordChange,
                        isPassword = true,
                        passwordVisible = managePasswordVisible,
                        onTogglePassword = onTogglePasswordVisible,
                    )
                }
            }
            if (selectedScheme == PlaybackStreamType.HTTP) {
                item {
                    Text(text = "HTTP 兼容性", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
                item {
                    DarkFormCard {
                        DarkSwitchRow(
                            label = "强制软件解码",
                            checked = forceHttpSoftwareDecode,
                            onCheckedChange = onForceHttpSoftwareDecodeChange,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalNvrAccent.current)
                .clickable(onClick = onSave)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "保存", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AddCameraVerificationScreen(
    modifier: Modifier = Modifier,
    result: VerificationPreview?,
    forceHttpSoftwareDecode: Boolean,
    onSave: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(text = "主码流", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            item {
                VerificationCard(
                    title = "主码流",
                    subtitleLines = listOf(
                        "协议： ${result?.streamScheme?.uppercase() ?: "RTSP"}",
                        "分辨率： ${result?.resolution ?: "2560x1440"}",
                        "编解码器： ${result?.codec ?: "hevc"}",
                        "软件解码： ${if (forceHttpSoftwareDecode) "强制启用" else "未启用"}",
                        "URL： ${sanitizePlaybackUrl(result?.primaryUrl.orEmpty())}",
                    ),
                    checked = true,
                )
            }
            item {
                Text(text = "次码流", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            item {
                VerificationCard(
                    title = "次码流",
                    subtitleLines = listOf("未配置"),
                    checked = false,
                )
            }
            item {
                Text(text = "快照", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            item {
                VerificationCard(
                    title = "快照",
                    subtitleLines = listOf("未配置"),
                    checked = false,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalNvrAccent.current)
                .clickable(onClick = onSave)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "保存", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VerificationCard(
    title: String,
    subtitleLines: List<String>,
    checked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(6.dp))
            subtitleLines.forEach { line ->
                Text(text = line, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = if (checked) "✔" else "●",
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
fun DarkFormCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp),
        content = content,
    )
}

@Composable
fun DarkInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    trailing: String? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(98.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    }
                    innerTextField()
                }
            },
        )
        when {
            onTogglePassword != null -> {
                Text(
                    text = if (passwordVisible) "👁" else "👁",
                    color = LocalNvrAccent.current,
                    modifier = Modifier.padding(start = 10.dp).clickable(onClick = onTogglePassword),
                )
            }
            trailing != null -> {
                Text(text = trailing, color = LocalNvrAccent.current, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun DarkStaticRow(
    label: String,
    value: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(98.dp))
        Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (trailing != null) {
            Text(text = trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun DarkSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun cameraBadge(
    camera: CameraDevice,
    prefs: android.content.SharedPreferences,
): String {
    val vendor = prefs.getString("camera_vendor_${camera.id}", "").orEmpty().trim()
    if (vendor.isNotBlank()) {
        return vendor
    }
    return if (camera.rtspUrl.contains("rtsp", ignoreCase = true)) "RTSP" else "CAM"
}

@Composable
fun DeviceManageTopBar(
    title: String,
    onCancel: () -> Unit,
    flowStep: DeviceFlowStep,
    isEditingExistingCamera: Boolean,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (flowStep) {
                DeviceFlowStep.Landing -> {
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.clickable(onClick = onCancel)
                    )
                }
                DeviceFlowStep.Form -> {
                    Text(
                        text = if (isEditingExistingCamera) "取消" else "‹ 添加相机",
                        color = if (isEditingExistingCamera) MaterialTheme.colorScheme.error else LocalNvrAccent.current,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable {
                            if (isEditingExistingCamera) onCancel() else onBack()
                        }
                    )
                }
                DeviceFlowStep.Verifying -> {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onCancel)
                    )
                }
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = if (flowStep == DeviceFlowStep.Landing) "?" else "", color = LocalNvrAccent.current, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun DeviceSelectorStrip(
    devices: List<CameraDevice>,
    selectedDeviceId: String?,
    onAddNew: () -> Unit,
    onSelect: (CameraDevice) -> Unit,
) {
    if (devices.isEmpty()) {
        TextButton(onClick = onAddNew) { Text("新增设备") }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        devices.forEach { device ->
            val selected = selectedDeviceId == device.id
            Text(
                text = device.name.ifBlank { "未命名" },
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) LocalNvrAccent.current else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (selected) LocalNvrAccent.current else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    .clickable { onSelect(device) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Text(
            text = "+ 新增",
            color = LocalNvrAccent.current,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onAddNew)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
