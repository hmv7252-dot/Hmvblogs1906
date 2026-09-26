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
    primary = CyanNeon,
    onPrimary = Color.Black,
    primaryContainer = VioletNeon,
    onPrimaryContainer = Color.White,
    secondary = GoldAccent,
    onSecondary = Color.Black,
    secondaryContainer = AmberGlow,
    onSecondaryContainer = Color.Black,
    tertiary = MagentaNeon,
    onTertiary = Color.White,
    background = DarkNavyBg,
    onBackground = Color.White,
    surface = DarkNavySurface,
    onSurface = Color.White,
    surfaceVariant = DarkNavySurfaceElevated,
    onSurfaceVariant = Color(0xFFD1D4E0)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4EED0),
    onSecondaryContainer = Color(0xFF00210E),
    tertiary = MagentaNeon,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = Color(0xFF191C1E),
    surface = LightSurface,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = Color(0xFF49454E)
)

@Composable
fun SlideMasterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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

// Retain alias for any legacy previews
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SlideMasterTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
