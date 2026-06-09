package com.nursery.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NurseryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NurseryColorScheme,
        typography = NurseryTypography,
        content = content,
    )
}
