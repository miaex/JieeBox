package com.jiee.box.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JieeColors = darkColorScheme(
    primary = JieeBlue,
    background = JieeBackground,
    surface = JieeSurface,
    onBackground = JieeTextPrimary,
    onSurface = JieeTextPrimary,
    outline = JieeOutline,
    secondary = JieeTerracotta,
    tertiary = JieePink
)

@Composable
fun JieeBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JieeColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
