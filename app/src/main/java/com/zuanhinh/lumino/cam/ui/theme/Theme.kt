package com.zuanhinh.lumino.cam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LuminoDarkScheme = darkColorScheme(
    primary = LuminoPrimary,
    onPrimary = LuminoOnPrimary,
    background = LuminoBackground,
    surface = LuminoSurface,
    onSurface = LuminoOnSurface
)

@Composable
fun LuminoCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LuminoDarkScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
