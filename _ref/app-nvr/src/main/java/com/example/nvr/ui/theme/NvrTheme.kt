package com.example.nvr.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * NVR 主题入口。
 *
 * 根据 appearance_theme_mode（system/light/dark）与 appearance_accent_color（#RRGGBB）
 * 动态构建 MaterialTheme.colorScheme，使 accent 颜色真正应用到全应用。
 *
 * 用法：
 * ```
 * NvrTheme(context) {
 *     // 你的 Composable
 * }
 * ```
 */
@Composable
fun NvrTheme(
    context: Context,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit,
) {
    val themeMode = AppearanceThemeState.themeMode.value
    val accent = Color(0xFF20B486)

    val isDark = when (themeMode) {
        "system" -> isSystemInDarkTheme()
        "dark" -> true
        else -> false
    }

    val colorScheme = if (isDark) buildDarkScheme(accent) else buildLightScheme(accent)

    CompositionLocalProvider(LocalNvrAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/** 当前生效的 accent 颜色，可在任意子 Composable 中读取。 */
val LocalNvrAccent = compositionLocalOf { Color(0xFF20B486) }

/* ------------------------------------------------------------------ */
/*  ColorScheme 构造                                                   */
/* ------------------------------------------------------------------ */

private fun buildLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.15f),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.8f),
    onSecondary = Color.White,
    tertiary = accent,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1B1B1D),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = Color(0xFF6B6F76),
    outline = Color(0xFFE0E2E8),
    error = Color(0xFFE53935),
)

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    primaryContainer = accent.copy(alpha = 0.2f),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.8f),
    onSecondary = Color.Black,
    tertiary = accent,
    background = Color(0xFF121316),
    onBackground = Color(0xFFE6E6E9),
    surface = Color(0xFF1B1D21),
    onSurface = Color(0xFFE6E6E9),
    surfaceVariant = Color(0xFF272A30),
    onSurfaceVariant = Color(0xFF9AA0A8),
    outline = Color(0xFF3A3D44),
    error = Color(0xFFEF5350),
)

/* ------------------------------------------------------------------ */
/*  偏好读写                                                           */
/* ------------------------------------------------------------------ */

const val PREFS_NAME = "com.example.nvr_preferences"
const val KEY_THEME_MODE = "appearance_theme_mode"
const val KEY_ACCENT = "appearance_accent_color"
const val DEFAULT_ACCENT = "#20B486"

/** 主题与强调色的全局可观察状态，切换时触发 Compose 重组 */
object AppearanceThemeState {
    val themeMode: MutableState<String> = mutableStateOf("light")

    fun update(mode: String) {
        themeMode.value = mode
    }

    fun initFromPrefs(prefs: android.content.SharedPreferences) {
        themeMode.value = prefs.getString(KEY_THEME_MODE, "light") ?: "light"
    }
}

/** 从 Application 初始化主题模式（AppCompatDelegate 夜间模式） */
fun applyAppearanceThemeMode(application: Application) {
    val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val mode = prefs.getString(KEY_THEME_MODE, "light") ?: "light"
    AppearanceThemeState.initFromPrefs(prefs)
    applyAppearanceThemeMode(mode)
}

fun applyAppearanceThemeMode(mode: String) {
    val nightMode = when (mode) {
        "system" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
    }
    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
}
