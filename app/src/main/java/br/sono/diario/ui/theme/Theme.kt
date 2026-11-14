package br.sono.diario.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF34449A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF5B5D72), tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF7F8FC), surface = Color(0xFFF7F8FC),
    error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB9C3FF), onPrimary = Color(0xFF07237A),
    primaryContainer = Color(0xFF1B2C82), onPrimaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF11131A), surface = Color(0xFF11131A),
)

/** Cores fixas do gráfico de sono, legíveis nos dois temas. */
object ChartColors {
    val inBed = Color(0xFF9FB0E8)
    val asleep = Color(0xFF34449A)
    val asleepDark = Color(0xFF8C9EFF)
    val med = Color(0xFFC0532A)
}

@Composable
fun SonoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
