package com.nursery.scanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val TAP_TARGET = 5
private const val IDLE_RESET_MS = 2_000L

/**
 * Hidden admin gate: [onTriggered] fires after [TAP_TARGET] consecutive taps, resetting after
 * ~2 s idle or when [active] becomes false (e.g. leaving the History tab).
 */
@Composable
fun rememberSettingsGate(active: Boolean, onTriggered: () -> Unit): () -> Unit {
    var tapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(active) {
        if (!active) tapCount = 0
    }

    LaunchedEffect(tapCount) {
        if (tapCount == 0) return@LaunchedEffect
        delay(IDLE_RESET_MS)
        tapCount = 0
    }

    return remember(onTriggered) {
        {
            tapCount++
            if (tapCount >= TAP_TARGET) {
                tapCount = 0
                onTriggered()
            }
        }
    }
}
