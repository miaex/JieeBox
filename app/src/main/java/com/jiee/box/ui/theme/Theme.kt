package com.jiee.box.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val JieeLightColors = lightColorScheme(
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
        colorScheme = JieeLightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
