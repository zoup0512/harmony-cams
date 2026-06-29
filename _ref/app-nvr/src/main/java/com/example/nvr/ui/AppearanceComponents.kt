package com.example.nvr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceSettingsScreen(
    themeMode: String,
    modifier: Modifier = Modifier,
    onThemeModeChange: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val themeOptions = listOf(
        "system" to "系统",
        "light" to "明亮",
        "dark" to "暗",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colorScheme.surface)
            .padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "主题",
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(46.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                themeOptions.forEach { (value, label) ->
                    val selected = value == themeMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colorScheme.surface else Color.Transparent)
                            .clickable { onThemeModeChange(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppearanceRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = subtitle, color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}
