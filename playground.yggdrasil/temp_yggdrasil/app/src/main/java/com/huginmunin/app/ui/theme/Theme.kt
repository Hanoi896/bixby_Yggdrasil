package com.huginmunin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Pure_White,
    onPrimary = Pure_Black,
    primaryContainer = Medium_Gray,
    onPrimaryContainer = Pure_White,
    
    secondary = Soft_Gray,
    onSecondary = Pure_Black,
    secondaryContainer = Dark_Gray,
    onSecondaryContainer = Light_Gray,
    
    tertiary = Accent_Blue,
    onTertiary = Pure_White,
    
    background = Pure_Black,
    onBackground = Pure_White,
    
    surface = Dark_Gray,
    onSurface = Pure_White,
    surfaceVariant = Medium_Gray,
    onSurfaceVariant = Light_Gray,
    
    error = Accent_Red,
    onError = Pure_White,
    errorContainer = Color(0xFF601410),
    onErrorContainer = Accent_Red,
    
    outline = Soft_Gray,
    outlineVariant = Medium_Gray
)

private val LightColorScheme = lightColorScheme(
    primary = Pure_Black,
    onPrimary = Pure_White,
    primaryContainer = Light_Gray,
    onPrimaryContainer = Pure_Black,
    
    secondary = Soft_Gray,
    onSecondary = Pure_White,
    secondaryContainer = Light_Gray,
    onSecondaryContainer = Pure_Black,
    
    tertiary = Accent_Blue,
    onTertiary = Pure_White,
    
    background = Pure_White,
    onBackground = Pure_Black,
    
    surface = Light_Gray,
    onSurface = Pure_Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Dark_Gray,
    
    error = Accent_Red,
    onError = Pure_White,
    errorContainer = Color(0xFFFFEDEA),
    onErrorContainer = Accent_Red,
    
    outline = Soft_Gray,
    outlineVariant = Light_Gray
)

@Composable
fun HuginMuninTheme(
    darkTheme: Boolean = true, // Default to dark theme for modern look
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
