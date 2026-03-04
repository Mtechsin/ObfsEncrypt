package com.obfs.encrypt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFFE57373),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2D2D2D),
    onPrimary = Color(0xFF1B5E20),
    onSecondary = Color(0xFF0D47A1),
    onTertiary = Color(0xFFB71C1C),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFB0B0B0),
    primaryContainer = Color(0xFF1B5E20),
    secondaryContainer = Color(0xFF0D47A1),
    tertiaryContainer = Color(0xFFB71C1C),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2A2A2A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFFD32F2F),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF757575),
    primaryContainer = Color(0xFFC8E6C9),
    secondaryContainer = Color(0xFFBBDEFB),
    tertiaryContainer = Color(0xFFFFCDD2),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEEEEEE),
    surfaceContainerHigh = Color(0xFFE0E0E0)
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfsEncryptTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    android.util.Log.d("Theme", "ObfsEncryptTheme COMPOSING - dark: $isDarkTheme")
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
