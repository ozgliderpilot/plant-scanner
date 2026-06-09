package com.nursery.scanner.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// High-contrast (WCAG AA) light palette. Fixed light scheme — predictable, legible for elderly
// volunteers; dynamic colour is intentionally NOT used so contrast is never weakened.
private val DeepGreen = Color(0xFF1B5E20)
private val LightGreen = Color(0xFFA5D6A7)
private val NearBlack = Color(0xFF14180F)
private val PaperWhite = Color(0xFFFDFDF6)
private val ErrorRed = Color(0xFFB3261E)

val NurseryColorScheme = lightColorScheme(
    primary = DeepGreen,
    onPrimary = Color.White,
    primaryContainer = LightGreen,
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF00513A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6F2D6),
    onSecondaryContainer = Color(0xFF00210F),
    background = PaperWhite,
    onBackground = NearBlack,
    surface = Color.White,
    onSurface = NearBlack,
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF2E332B),
    outline = Color(0xFF5C6359),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)
