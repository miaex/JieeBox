package com.jiee.box.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JieeDarkColors = darkColorScheme(
    primary = JieeBlue,
    background = JieeBackground,
    surface = JieeSurface,
    onBackground = JieeTextPrimary,
    onSurface = JieeTextPrimary,
    outline = JieeOutline
)

@Composable
fun JieeBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JieeDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
