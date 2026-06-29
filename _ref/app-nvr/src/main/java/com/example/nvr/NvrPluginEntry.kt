package com.example.nvr

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.combo.core.api.IPluginEntryClass
import com.combo.core.model.PluginContext
import com.example.nvr.ui.NvrMainScreen
import com.example.nvr.ui.theme.AppearanceThemeState
import com.example.nvr.ui.theme.NvrTheme
import com.example.nvr.ui.theme.PREFS_NAME
import org.koin.core.module.Module

/**
 * NVR 插件入口类，实现 ComboLite 的 IPluginEntryClass 接口。
 *
 * 使用 [NvrTheme] 提供完整的 MaterialTheme，包含主题模式（system/light/dark）
 * 与 accent 颜色的动态应用。
 */
class NvrPluginEntry : IPluginEntryClass {

    override val pluginModule: List<Module>
        get() = emptyList()

    override fun onLoad(context: PluginContext) {
        // 这里可以做一些插件初始化工作（例如数据库、日志等）
    }

    override fun onUnload() {
        // 这里可以做一些资源释放工作
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        // 从 SharedPreferences 初始化主题状态，使切换主题时重组
        rememberAppearanceThemeState(context)
        NvrTheme(context) {
            NvrMainScreen()
        }
    }
}

@Composable
private fun rememberAppearanceThemeState(context: Context) {
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    remember(prefs) {
        AppearanceThemeState.initFromPrefs(prefs)
    }
}
