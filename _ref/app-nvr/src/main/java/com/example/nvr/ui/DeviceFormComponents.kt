package com.example.nvr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nvr.ui.theme.LocalNvrAccent

@Composable
fun DeviceDividerDark() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
}

@Composable
fun DeviceSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun DeviceGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
fun DeviceDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
}

@Composable
fun DeviceInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    valuePlaceholder: String = "",
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(96.dp)
        )
        if (isPassword && passwordVisible) {
            Text(
                text = if (value.isEmpty()) valuePlaceholder else value,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                ),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = valuePlaceholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        when {
            onTogglePassword != null -> {
                Text(
                    text = if (passwordVisible) "🙈" else "👁",
                    color = LocalNvrAccent.current,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clickable(enabled = enabled, onClick = onTogglePassword)
                )
            }
            trailing != null -> {
                Text(
                    text = trailing,
                    color = LocalNvrAccent.current,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
fun DeviceStaticRow(
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
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(96.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
fun DeviceSwitchRow(
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
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DeviceActionRow(
    label: String,
    color: Color,
    leadingBadgeColor: Color,
    leadingText: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(leadingBadgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = leadingText, color = color)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(text = trailing, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
