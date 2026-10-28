package ru.meow.reminder.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import ru.meow.reminder.data.AppSettings
import ru.meow.reminder.R

val accents = listOf(Color(0xFF78669E), Color(0xFF9A6075), Color(0xFF506E85), Color(0xFF8C6447))
val pastels = listOf(Color(0xFFE9E1F8), Color(0xFFF5DDE6), Color(0xFFE1EEF6), Color(0xFFF8E7D9), Color(0xFFE2EBDF))

@Composable
fun MeowTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFFCEC0EE), secondary = Color(0xFFE6B9C9), background = Color(0xFF19181E), surface = Color(0xFF211F28), onSurface = Color(0xFFEAE4EE))
    else lightColorScheme(primary = accents[settings.accent.coerceIn(0, 3)], secondary = Color(0xFF966579), background = Color(0xFFFAF8F6), surface = Color(0xFFFAF8F6), onSurface = Color(0xFF292733), primaryContainer = Color(0xFFE9E1F8), secondaryContainer = Color(0xFFF5DDE6))
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = scheme.background.toArgb()
            }
        }
    }
    val base = Typography()
    val serif = FontFamily(Font(R.font.noto_serif))
    val typography = base.copy(
        headlineLarge = TextStyle(fontFamily = serif, fontSize = 34.sp, lineHeight = 42.sp),
        headlineMedium = TextStyle(fontFamily = serif, fontSize = 28.sp, lineHeight = 36.sp),
        titleLarge = TextStyle(fontFamily = serif, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
