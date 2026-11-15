package com.txa.cleantxa.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF2EC4B6)

private val Dark = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0E3B37),
    onPrimaryContainer = Color(0xFFB6F2EA),
    secondaryContainer = Color(0xFF1C2B3A),
    onSecondaryContainer = Color(0xFFD6E6F5),
    background = Color(0xFF0B1118),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0B1118),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF16212C),
    onSurfaceVariant = Color(0xFFB4C0CC),
    error = Color(0xFFFFB4AB),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F2EA),
    onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFDCE8F3),
    onSecondaryContainer = Color(0xFF0E1E2B),
    background = Color(0xFFF6FAFB),
    onBackground = Color(0xFF111A22),
    surface = Color(0xFFF6FAFB),
    onSurface = Color(0xFF111A22),
    surfaceVariant = Color(0xFFE4ECF0),
    onSurfaceVariant = Color(0xFF3E4A55),
    error = Color(0xFFB3261E),
)

@Composable
fun CleanTxaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
