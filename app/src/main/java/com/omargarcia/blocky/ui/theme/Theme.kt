@file:Suppress("FunctionName")

package com.omargarcia.blocky.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80,
    background = Color(0xFF323238),
    surface = Color(0xFF3D3D45),
    onPrimary = Color(0xFF0D1B2A),
    onSecondary = Color(0xFF0D1B2A),
    onTertiary = Color(0xFF0D1B2A),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF4C4C56),
    onSurfaceVariant = Color(0xFFE2E2E8),
    primaryContainer = ActiveShieldBlueContainer,
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFECEFF1),
)

private val LightColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80,
    background = Color(0xFF323238),
    surface = Color(0xFF3D3D45),
    onPrimary = Color(0xFF0D1B2A),
    onSecondary = Color(0xFF0D1B2A),
    onTertiary = Color(0xFF0D1B2A),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF4C4C56),
    onSurfaceVariant = Color(0xFFE2E2E8),
    primaryContainer = ActiveShieldBlueContainer,
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFECEFF1),
)

@Composable
fun BlockyTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = PixelTypography,
        content = content,
    )
}

