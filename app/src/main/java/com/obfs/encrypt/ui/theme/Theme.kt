package com.obfs.encrypt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
// 70-20-10 Ratio: 70% neutral dark, 20% muted secondary, 10% vibrant accent
private val DarkBlueColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color (vibrant but readable)
    primary = Color(0xFF64B5F6),
    primaryContainer = Color(0xFF1E5A8F),
    onPrimary = Color(0xFF001624),

    // 20% Secondary - Supporting color (muted for less eye strain)
    secondary = Color(0xFF5DBAB1),
    secondaryContainer = Color(0xFF004D47),
    onSecondary = Color(0xFF00201D),

    // Tertiary for additional accents (muted green)
    tertiary = Color(0xFF81C784),
    tertiaryContainer = Color(0xFF1E4D2B),
    onTertiary = Color(0xFF00211A),

    // 70% Dominant - Background and surfaces (neutral dark grays)
    background = Color(0xFF0F1115),
    surface = Color(0xFF151922),
    surfaceVariant = Color(0xFF1E2330),
    surfaceContainerLow = Color(0xFF12151C),
    surfaceContainer = Color(0xFF181D27),
    surfaceContainerHigh = Color(0xFF1F2533),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFFF5F7FA),
    onSurface = Color(0xFFF5F7FA),
    onSurfaceVariant = Color(0xFFC5CBD6)
)

// Dark Theme - Red
// 70-20-10 Ratio: 70% neutral dark, 20% muted secondary, 10% vibrant accent
private val DarkRedColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color (vibrant red)
    primary = Color(0xFFEF5350),
    primaryContainer = Color(0xFF8E1C1C),
    onPrimary = Color(0xFF2B0A0A),

    // 20% Secondary - Supporting color (muted rose)
    secondary = Color(0xFFE57373),
    secondaryContainer = Color(0xFF6B1515),
    onSecondary = Color(0xFF2B0808),

    // Tertiary for additional accents (muted pink)
    tertiary = Color(0xFFF48FB1),
    tertiaryContainer = Color(0xFF7D1F3F),
    onTertiary = Color(0xFF2B0A14),

    // 70% Dominant - Background and surfaces (neutral dark grays)
    background = Color(0xFF140A0A),
    surface = Color(0xFF1E0F0F),
    surfaceVariant = Color(0xFF2D1818),
    surfaceContainerLow = Color(0xFF160D0D),
    surfaceContainer = Color(0xFF1A1010),
    surfaceContainerHigh = Color(0xFF211414),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFFF8F1F1),
    onSurface = Color(0xFFF8F1F1),
    onSurfaceVariant = Color(0xFFD5C0C0)
)

// Dark Theme - Green
// 70-20-10 Ratio: 70% neutral dark, 20% muted secondary, 10% vibrant accent
private val DarkGreenColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color (vibrant green)
    primary = Color(0xFF81C784),
    primaryContainer = Color(0xFF1E5A2B),
    onPrimary = Color(0xFF0A1F0F),

    // 20% Secondary - Supporting color (muted sage)
    secondary = Color(0xFF8BC38E),
    secondaryContainer = Color(0xFF154D1A),
    onSecondary = Color(0xFF071F0A),

    // Tertiary for additional accents (muted teal)
    tertiary = Color(0xFF80CBC4),
    tertiaryContainer = Color(0xFF124D47),
    onTertiary = Color(0xFF051F1C),

    // 70% Dominant - Background and surfaces (neutral dark grays)
    background = Color(0xFF0A140A),
    surface = Color(0xFF0F1E0F),
    surfaceVariant = Color(0xFF182D18),
    surfaceContainerLow = Color(0xFF0D160D),
    surfaceContainer = Color(0xFF111A11),
    surfaceContainerHigh = Color(0xFF162116),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFFF1F8F1),
    onSurface = Color(0xFFF1F8F1),
    onSurfaceVariant = Color(0xFFC5D5C5)
)

// Dark Theme - Orange
// 70-20-10 Ratio: 70% neutral dark, 20% muted secondary, 10% vibrant accent
private val DarkOrangeColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color (vibrant orange)
    primary = Color(0xFFFFB74D),
    primaryContainer = Color(0xFF8F4D00),
    onPrimary = Color(0xFF2B1400),

    // 20% Secondary - Supporting color (muted amber)
    secondary = Color(0xFFFFCC80),
    secondaryContainer = Color(0xFF6B3800),
    onSecondary = Color(0xFF2B1200),

    // Tertiary for additional accents (muted yellow)
    tertiary = Color(0xFFFFD54F),
    tertiaryContainer = Color(0xFF7D5C00),
    onTertiary = Color(0xFF2B1E00),

    // 70% Dominant - Background and surfaces (neutral dark grays)
    background = Color(0xFF14100A),
    surface = Color(0xFF1E180F),
    surfaceVariant = Color(0xFF2D2518),
    surfaceContainerLow = Color(0xFF16120D),
    surfaceContainer = Color(0xFF1A1510),
    surfaceContainerHigh = Color(0xFF211A13),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFFF8F5F1),
    onSurface = Color(0xFFF8F5F1),
    onSurfaceVariant = Color(0xFFD5C8B8)
)

// Dark Theme - Navy
// 70-20-10 Ratio: 70% neutral dark, 20% muted secondary, 10% vibrant accent
private val DarkNavyColorScheme = darkColorScheme(
    // 10% Accent - Primary highlight color (vibrant blue)
    primary = Color(0xFF64B5F6),
    primaryContainer = Color(0xFF1E5A8F),
    onPrimary = Color(0xFF001624),

    // 20% Secondary - Supporting color (muted light blue)
    secondary = Color(0xFF90CAF9),
    secondaryContainer = Color(0xFF0D3A6B),
    onSecondary = Color(0xFF001624),

    // Tertiary for additional accents (muted indigo)
    tertiary = Color(0xFF7986CB),
    tertiaryContainer = Color(0xFF1E2D5A),
    onTertiary = Color(0xFF0F162B),

    // 70% Dominant - Background and surfaces (neutral dark grays)
    background = Color(0xFF0A0E14),
    surface = Color(0xFF0F1621),
    surfaceVariant = Color(0xFF182333),
    surfaceContainerLow = Color(0xFF0D121A),
    surfaceContainer = Color(0xFF111821),
    surfaceContainerHigh = Color(0xFF161E2B),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFFF4F7FB),
    onSurface = Color(0xFFF4F7FB),
    onSurfaceVariant = Color(0xFFC5D0E0)
)

// ============================================================================
// LIGHT THEMES
// ============================================================================

// Light Theme - Lemon (Default)
// 70-20-10 Ratio: 70% clean white, 20% soft pastel, 10% vibrant accent
private val LightLemonColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color (vibrant lime)
    primary = Color(0xFFC0CA33),
    primaryContainer = Color(0xFFE8EE9C),
    onPrimary = Color(0xFF2B3000),

    // 20% Secondary - Supporting color (soft yellow)
    secondary = Color(0xFFD4E157),
    secondaryContainer = Color(0xFFF1F8C9),
    onSecondary = Color(0xFF222600),

    // Tertiary for additional accents (muted lime)
    tertiary = Color(0xFFDCE775),
    tertiaryContainer = Color(0xFFF5F9D9),
    onTertiary = Color(0xFF262B00),

    // 70% Dominant - Background and surfaces (clean whites)
    background = Color(0xFFFAFAF5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2F2E8),
    surfaceContainerLow = Color(0xFFFAFAF0),
    surfaceContainer = Color(0xFFF6F6EC),
    surfaceContainerHigh = Color(0xFFEFEFE5),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFF141610),
    onSurface = Color(0xFF141610),
    onSurfaceVariant = Color(0xFF525542)
)

// Light Theme - Red
// 70-20-10 Ratio: 70% clean white, 20% soft pastel, 10% vibrant accent
private val LightRedColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color (vibrant red)
    primary = Color(0xFFE53935),
    primaryContainer = Color(0xFFFFD6D6),
    onPrimary = Color(0xFF5C0000),

    // 20% Secondary - Supporting color (soft rose)
    secondary = Color(0xFFEF5350),
    secondaryContainer = Color(0xFFFFEBEB),
    onSecondary = Color(0xFF520000),

    // Tertiary for additional accents (soft pink)
    tertiary = Color(0xFFF48FB1),
    tertiaryContainer = Color(0xFFFFF0F5),
    onTertiary = Color(0xFF3D0014),

    // 70% Dominant - Background and surfaces (clean whites)
    background = Color(0xFFFAF5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3E5E5),
    surfaceContainerLow = Color(0xFFFAF5F5),
    surfaceContainer = Color(0xFFF6F0F0),
    surfaceContainerHigh = Color(0xFFEFE5E5),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFF161010),
    onSurface = Color(0xFF161010),
    onSurfaceVariant = Color(0xFF524242)
)

// Light Theme - Orange
// 70-20-10 Ratio: 70% clean white, 20% soft pastel, 10% vibrant accent
private val LightOrangeColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color (vibrant orange)
    primary = Color(0xFFF57C00),
    primaryContainer = Color(0xFFFFE5CC),
    onPrimary = Color(0xFF3D1800),

    // 20% Secondary - Supporting color (soft amber)
    secondary = Color(0xFFFF9800),
    secondaryContainer = Color(0xFFFFF5E5),
    onSecondary = Color(0xFF3D1A00),

    // Tertiary for additional accents (soft yellow)
    tertiary = Color(0xFFFFB74D),
    tertiaryContainer = Color(0xFFFFFAF0),
    onTertiary = Color(0xFF472400),

    // 70% Dominant - Background and surfaces (clean whites)
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EDE5),
    surfaceContainerLow = Color(0xFFFAF8F5),
    surfaceContainer = Color(0xFFF6F4F0),
    surfaceContainerHigh = Color(0xFFEFEBE5),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFF161410),
    onSurface = Color(0xFF161410),
    onSurfaceVariant = Color(0xFF524A42)
)

// Light Theme - Green
// 70-20-10 Ratio: 70% clean white, 20% soft pastel, 10% vibrant accent
private val LightGreenColorScheme = lightColorScheme(
    // 10% Accent - Primary highlight color (vibrant green)
    primary = Color(0xFF43A047),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimary = Color(0xFF00310A),

    // 20% Secondary - Supporting color (soft sage)
    secondary = Color(0xFF66BB6A),
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondary = Color(0xFF00330A),

    // Tertiary for additional accents (soft mint)
    tertiary = Color(0xFF81C784),
    tertiaryContainer = Color(0xFFF1F8F1),
    onTertiary = Color(0xFF00360B),

    // 70% Dominant - Background and surfaces (clean whites)
    background = Color(0xFFF5FAF5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8F0E8),
    surfaceContainerLow = Color(0xFFF5FAF5),
    surfaceContainer = Color(0xFFF0F8F0),
    surfaceContainerHigh = Color(0xFFE5F0E5),

    // Text colors (high contrast for readability - WCAG AA compliant)
    onBackground = Color(0xFF101410),
    onSurface = Color(0xFF101410),
    onSurfaceVariant = Color(0xFF425242)
)

// ============================================================================
// AMOLED MODE - Pure black surface/background overlay
// Applied on top of any dark theme (including Material You) when enabled
// Keeps accent colors from the selected theme, replaces surfaces with pure black
// ============================================================================

/**
 * Apply AMOLED (pure black) overrides to any dark ColorScheme.
 * Keeps the accent colors (primary, secondary, tertiary, error) intact
 * while replacing all surface/background colors with pure black variants.
 */
private fun ColorScheme.withAmoledOverrides(): ColorScheme {
    return this.copy(
        // Pure black backgrounds for maximum OLED power savings
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceVariant = Color(0xFF0C0C0C),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF060606),
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerHigh = Color(0xFF101010),
        surfaceContainerHighest = Color(0xFF161616),

        // Slightly brighter text for better contrast on pure black
        onBackground = Color(0xFFF0F0F0),
        onSurface = Color(0xFFF0F0F0),
        onSurfaceVariant = Color(0xFFB8B8B8),

        // Subtle outline for definition on pure black
        outline = Color(0xFF3A3A3A),
        outlineVariant = Color(0xFF252525),

        // Crisp inverse for snackbars/tooltips
        inverseSurface = Color(0xFFE8E8E8),
        inverseOnSurface = Color(0xFF1A1A1A)
    )
}

// ============================================================================
// AMOLED BUTTON STYLES
// Stroke-only buttons with transparent fill for AMOLED mode
// ============================================================================

/**
 * Returns true if AMOLED mode is currently active (enabled + dark theme).
 * Use this to apply custom button styling for AMOLED mode.
 */
@Composable
fun isAMOLEDTheme(): Boolean {
    return LocalAmoledMode.current
}

/**
 * Get container color for AMOLED outlined buttons (transparent).
 */
@Composable
fun amoledOutlinedButtonContainerColor(): Color {
    return Color.Transparent
}

/**
 * Get content color for AMOLED outlined buttons.
 */
@Composable
fun amoledOutlinedButtonContentColor(
    primaryColor: Color = MaterialTheme.colorScheme.primary
): Color {
    return primaryColor
}

/**
 * Get disabled content color for AMOLED outlined buttons.
 */
@Composable
fun amoledOutlinedButtonDisabledContentColor(
    primaryColor: Color = MaterialTheme.colorScheme.primary
): Color {
    return primaryColor.copy(alpha = 0.38f)
}

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
    NAVY
}

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalDynamicColor = compositionLocalOf { false }
val LocalAppTheme = compositionLocalOf { AppTheme.DEFAULT }
val LocalAmoledMode = compositionLocalOf { false }

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfsEncryptTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appTheme: AppTheme = AppTheme.DEFAULT,
    dynamicColor: Boolean = false,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> when (appTheme) {
            AppTheme.RED -> DarkRedColorScheme
            AppTheme.GREEN -> DarkGreenColorScheme
            AppTheme.ORANGE -> DarkOrangeColorScheme
            AppTheme.NAVY -> DarkNavyColorScheme
            AppTheme.DEFAULT -> DarkBlueColorScheme
        }
        else -> when (appTheme) {
            AppTheme.RED -> LightRedColorScheme
            AppTheme.GREEN -> LightGreenColorScheme
            AppTheme.ORANGE -> LightOrangeColorScheme
            AppTheme.DEFAULT, AppTheme.NAVY -> LightLemonColorScheme
        }
    }

    // Apply AMOLED overrides when enabled + dark mode
    val colorScheme = if (amoledMode && isDarkTheme) {
        baseColorScheme.withAmoledOverrides()
    } else {
        baseColorScheme
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

    val isAmoledActive = amoledMode && isDarkTheme

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAmoledMode provides isAmoledActive
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
