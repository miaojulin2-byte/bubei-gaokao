package cn.bubei.gaokao.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import cn.bubei.gaokao.data.Subject

/** 每科一个主色：文言朱砂、古诗文黛蓝、生物竹青。 */
fun Subject.accent(dark: Boolean): Color = when (this) {
    Subject.WENYAN -> if (dark) Color(0xFFE38B73) else Color(0xFFB8492F)
    Subject.GUSHI -> if (dark) Color(0xFF8DB4D6) else Color(0xFF2F5F86)
    Subject.BIOLOGY -> if (dark) Color(0xFF7CC9A3) else Color(0xFF2A7D5A)
}

/**
 * 古文用宋体。很多国产系统没把中文衬线体接到 FontFamily.Serif 上，
 * 所以直接读系统自带的思源宋体（ttc 第 3 个字面是简体中文）；读不到就退回默认。
 */
val Serif: FontFamily by lazy {
    val file = java.io.File("/system/fonts/NotoSerifCJK-Regular.ttc")
    runCatching {
        FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.Builder(file).setTtcIndex(2).build()))
    }.getOrNull()?.takeIf { file.exists() } ?: FontFamily.Serif
}

@Composable
fun GaokaoTheme(subject: Subject, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val accent = subject.accent(dark)
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF14110F),
        background = Color(0xFF121212), surface = Color(0xFF121212),
        surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF242424),
        onBackground = Color(0xFFECE8E4), onSurface = Color(0xFFECE8E4), onSurfaceVariant = Color(0xFFA8A29E),
        outlineVariant = Color(0xFF34302D),
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        background = Color(0xFFF6F5F3), surface = Color(0xFFF6F5F3),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEFEDEA),
        onBackground = Color(0xFF242220), onSurface = Color(0xFF242220), onSurfaceVariant = Color(0xFF7D7873),
        outlineVariant = Color(0xFFE4E1DD),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
