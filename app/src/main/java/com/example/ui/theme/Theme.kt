package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = AcademicBlueLight,
    onPrimary = Color.White,
    primaryContainer = AcademicBlueDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = AcademicTealLight,
    onSecondary = Color.Black,
    secondaryContainer = AcademicTeal,
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = AcademicAmberLight,
    onTertiary = Color.Black,
    background = Slate900,
    surface = Slate800,
    surfaceVariant = Color(0xFF243247),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = AcademicBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = AcademicTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = AcademicAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    background = Slate50,
    surface = Color.White,
    surfaceVariant = Slate100,
    onBackground = Slate900,
    onSurface = Slate900,
    onSurfaceVariant = Slate700,
    outline = Color(0xFF94A3B8),
    outlineVariant = Slate200
)

@Composable
fun StudyMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false to maintain consistent academic branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for backwards compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = StudyMateTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
