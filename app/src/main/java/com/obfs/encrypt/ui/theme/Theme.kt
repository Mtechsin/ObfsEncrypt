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

// ============================================================================
// COLOR PALETTES - 70-20-10 Approach
// 70% Dominant (background, surface)
// 20% Secondary (primary container, secondary colors)
// 10% Accent (primary, tertiary for highlights)
// ============================================================================

// ============================================================================
// DARK THEMES
// ============================================================================

// Dark Theme - Blue (Default)
private val DarkBlueColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFF64B5F6),
    primaryContainer = Color(0xFF1565C0),
    onPrimary = Color(0xFF0D47A1),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFF4DB6AC),
    secondaryContainer = Color(0xFF00695C),
    onSecondary = Color(0xFF004D40),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFA5D6A7),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiary = Color(0xFF1B5E20),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFF0A0E14),
    surface = Color(0xFF12161F),
    surfaceVariant = Color(0xFF1E2530),
    surfaceContainerLow = Color(0xFF0F141C),
    surfaceContainer = Color(0xFF151B24),
    surfaceContainerHigh = Color(0xFF1A212B),
    
    // Text colors
    onBackground = Color(0xFFE8EAF0),
    onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFFB0B8C5)
)

// Dark Theme - Red
private val DarkRedColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFFEF5350),
    primaryContainer = Color(0xFFC62828),
    onPrimary = Color(0xFF8E0000),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFFEF9A9A),
    secondaryContainer = Color(0xFF8E0000),
    onSecondary = Color(0xFF5C0000),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFFFCDD2),
    tertiaryContainer = Color(0xFFB71C1C),
    onTertiary = Color(0xFF8E0000),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFF140A0A),
    surface = Color(0xFF1E0F0F),
    surfaceVariant = Color(0xFF2D1818),
    surfaceContainerLow = Color(0xFF160D0D),
    surfaceContainer = Color(0xFF1A1010),
    surfaceContainerHigh = Color(0xFF211414),
    
    // Text colors
    onBackground = Color(0xFFF0E8E8),
    onSurface = Color(0xFFF0E8E8),
    onSurfaceVariant = Color(0xFFC5B0B0)
)

// Dark Theme - Green
private val DarkGreenColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFF81C784),
    primaryContainer = Color(0xFF2E7D32),
    onPrimary = Color(0xFF1B5E20),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFFA5D6A7),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondary = Color(0xFF0D3D1B),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFC8E6C9),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiary = Color(0xFF1B5E20),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFF0A140A),
    surface = Color(0xFF0F1E0F),
    surfaceVariant = Color(0xFF182D18),
    surfaceContainerLow = Color(0xFF0D160D),
    surfaceContainer = Color(0xFF111A11),
    surfaceContainerHigh = Color(0xFF162116),
    
    // Text colors
    onBackground = Color(0xFFE8F0E8),
    onSurface = Color(0xFFE8F0E8),
    onSurfaceVariant = Color(0xFFB0C5B0)
)

// Dark Theme - Orange
private val DarkOrangeColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFFFFB74D),
    primaryContainer = Color(0xFFF57C00),
    onPrimary = Color(0xFFE65100),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFFFFCC80),
    secondaryContainer = Color(0xFFE65100),
    onSecondary = Color(0xFFBF360C),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFFFE0B2),
    tertiaryContainer = Color(0xFFEF6C00),
    onTertiary = Color(0xFFE65100),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFF14100A),
    surface = Color(0xFF1E180F),
    surfaceVariant = Color(0xFF2D2518),
    surfaceContainerLow = Color(0xFF16120D),
    surfaceContainer = Color(0xFF1A1510),
    surfaceContainerHigh = Color(0xFF211A13),
    
    // Text colors
    onBackground = Color(0xFFF0ECE8),
    onSurface = Color(0xFFF0ECE8),
    onSurfaceVariant = Color(0xFFC5BEB0)
)

// Dark Theme - Navy
private val DarkNavyColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFF64B5F6),
    primaryContainer = Color(0xFF1565C0),
    onPrimary = Color(0xFF0D47A1),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFF90CAF9),
    secondaryContainer = Color(0xFF0D47A1),
    onSecondary = Color(0xFF002171),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFBBDEFB),
    tertiaryContainer = Color(0xFF1565C0),
    onTertiary = Color(0xFF0D47A1),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFF0A0E14),
    surface = Color(0xFF0F1621),
    surfaceVariant = Color(0xFF182333),
    surfaceContainerLow = Color(0xFF0D121A),
    surfaceContainer = Color(0xFF111821),
    surfaceContainerHigh = Color(0xFF161E2B),
    
    // Text colors
    onBackground = Color(0xFFE8EEF5),
    onSurface = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFFB0BCC5)
)

// Dark Theme - AMOLED (Pure Black for OLED screens)
private val DarkAMOLEDColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color (Vibrant Purple-Blue for night out)
    primary = Color(0xFF7C4DFF),
    primaryContainer = Color(0xFF651FFF),
    onPrimary = Color(0xFF311B92),
    
    // 20% Secondary - Supporting color (Neon Cyan)
    secondary = Color(0xFF00E5FF),
    secondaryContainer = Color(0xFF00B8D4),
    onSecondary = Color(0xFF006064),
    
    // Tertiary for additional accents (Magenta/Pink for night vibe)
    tertiary = Color(0xFFFF4081),
    tertiaryContainer = Color(0xFFF50057),
    onTertiary = Color(0xFF880E4F),
    
    // 70% Dominant - Pure black for OLED power savings
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF0D0D0D),
    
    // Text colors (Slightly dimmed for night comfort)
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

// ============================================================================
// LIGHT THEMES
// ============================================================================

// Light Theme - Lemon (Default)
private val LightLemonColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFFC0CA33),
    primaryContainer = Color(0xFFF0F4C3),
    onPrimary = Color(0xFF333900),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFFDCE775),
    secondaryContainer = Color(0xFFF9FBE7),
    onSecondary = Color(0xFF242800),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFE6EE9C),
    tertiaryContainer = Color(0xFFF0F4C3),
    onTertiary = Color(0xFF2E3300),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFFFAFAF5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5E8),
    surfaceContainerLow = Color(0xFFFAFAF0),
    surfaceContainer = Color(0xFFF8F8EA),
    surfaceContainerHigh = Color(0xFFF3F3E0),
    
    // Text colors
    onBackground = Color(0xFF1A1C14),
    onSurface = Color(0xFF1A1C14),
    onSurfaceVariant = Color(0xFF5C5F4A)
)

// Light Theme - Red
private val LightRedColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFFE53935),
    primaryContainer = Color(0xFFFFCDD2),
    onPrimary = Color(0xFF690000),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFFEF9A9A),
    secondaryContainer = Color(0xFFFFF0F0),
    onSecondary = Color(0xFF5C0000),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFFFCDD2),
    tertiaryContainer = Color(0xFFFFF5F5),
    onTertiary = Color(0xFF410002),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFFFAF5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5E8E8),
    surfaceContainerLow = Color(0xFFFAF5F5),
    surfaceContainer = Color(0xFFF8F0F0),
    surfaceContainerHigh = Color(0xFFF3E5E5),
    
    // Text colors
    onBackground = Color(0xFF1C1414),
    onSurface = Color(0xFF1C1414),
    onSurfaceVariant = Color(0xFF5F4A4A)
)

// Light Theme - Orange
private val LightOrangeColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFFF57C00),
    primaryContainer = Color(0xFFFFE0B2),
    onPrimary = Color(0xFF431E00),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFFFFB74D),
    secondaryContainer = Color(0xFFFFF8F0),
    onSecondary = Color(0xFF4F2900),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFFFCC80),
    tertiaryContainer = Color(0xFFFFF8E8),
    onTertiary = Color(0xFF4F2F00),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F0E8),
    surfaceContainerLow = Color(0xFFFAF8F5),
    surfaceContainer = Color(0xFFF8F5F0),
    surfaceContainerHigh = Color(0xFFF3EDE5),
    
    // Text colors
    onBackground = Color(0xFF1C1914),
    onSurface = Color(0xFF1C1914),
    onSurfaceVariant = Color(0xFF5F554A)
)

// Light Theme - Green
private val LightGreenColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color
    primary = Color(0xFF43A047),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimary = Color(0xFF003808),
    
    // 20% Secondary - Supporting color
    secondary = Color(0xFF81C784),
    secondaryContainer = Color(0xFFF0F8F0),
    onSecondary = Color(0xFF003D1B),
    
    // Tertiary for additional accents
    tertiary = Color(0xFFA5D6A7),
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiary = Color(0xFF003D1B),
    
    // 70% Dominant - Background and surfaces
    background = Color(0xFFF5FAF5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8F0E8),
    surfaceContainerLow = Color(0xFFF5FAF5),
    surfaceContainer = Color(0xFFF0F8F0),
    surfaceContainerHigh = Color(0xFFE5F0E5),
    
    // Text colors
    onBackground = Color(0xFF141C14),
    onSurface = Color(0xFF141C14),
    onSurfaceVariant = Color(0xFF4A5F4A)
)

// Light Theme - AMOLED (Clean white with vibrant accents)
private val LightAMOLEDColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color (Vibrant Purple)
    primary = Color(0xFF7C4DFF),
    primaryContainer = Color(0xFFEDE7F6),
    onPrimary = Color(0xFF311B92),
    
    // 20% Secondary - Supporting color (Bright Cyan)
    secondary = Color(0xFF00BCD4),
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondary = Color(0xFF006064),
    
    // Tertiary for additional accents (Vibrant Pink)
    tertiary = Color(0xFFFF4081),
    tertiaryContainer = Color(0xFFFCE4EC),
    onTertiary = Color(0xFF880E4F),
    
    // 70% Dominant - Pure white backgrounds
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5F5),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF8F8F8),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    
    // Text colors
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF757575)
)

// ============================================================================
// THEME ENUM
// ============================================================================

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AppTheme {
    DEFAULT,      // Blue (dark) / Lemon (light)
    RED,
    GREEN,
    ORANGE,
    NAVY,
    AMOLED        // Pure black (dark) / Clean white (light) with neon accents
}

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalDynamicColor = compositionLocalOf { false }
val LocalAppTheme = compositionLocalOf { AppTheme.DEFAULT }

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfsEncryptTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appTheme: AppTheme = AppTheme.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    android.util.Log.d("Theme", "ObfsEncryptTheme COMPOSING - dark: $isDarkTheme, appTheme: $appTheme, dynamic: $dynamicColor")
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> when (appTheme) {
            AppTheme.RED -> DarkRedColorScheme
            AppTheme.GREEN -> DarkGreenColorScheme
            AppTheme.ORANGE -> DarkOrangeColorScheme
            AppTheme.NAVY -> DarkNavyColorScheme
            AppTheme.AMOLED -> DarkAMOLEDColorScheme
            AppTheme.DEFAULT -> DarkBlueColorScheme
        }
        else -> when (appTheme) {
            AppTheme.RED -> LightRedColorScheme
            AppTheme.GREEN -> LightGreenColorScheme
            AppTheme.ORANGE -> LightOrangeColorScheme
            AppTheme.AMOLED -> LightAMOLEDColorScheme
            AppTheme.DEFAULT, AppTheme.NAVY -> LightLemonColorScheme
        }
    }

    // Apply theme to system bars
    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
