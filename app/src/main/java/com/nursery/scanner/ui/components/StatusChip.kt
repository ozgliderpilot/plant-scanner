package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nursery.scanner.data.repo.SyncState
import com.nursery.scanner.util.relativeTime

/**
 * Persistent trust signal (spec): "Synced 2h ago" / "Pending N" / "Offline·N". Calm, non-flashing.
 */
@Composable
fun StatusChip(state: SyncState, now: Long, modifier: Modifier = Modifier) {
    data class Look(val label: String, val icon: ImageVector, val bg: Color, val fg: Color)

    val scheme = MaterialTheme.colorScheme
    val look = when {
        !state.online -> Look("Offline·${state.pendingCount}", Icons.Filled.CloudOff, scheme.errorContainer, scheme.onErrorContainer)
        state.isBusy -> Look("Syncing…", Icons.Filled.Sync, scheme.secondaryContainer, scheme.onSecondaryContainer)
        state.pendingCount > 0 -> Look("Pending ${state.pendingCount}", Icons.Filled.CloudQueue, scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> Look("Synced ${relativeTime(state.lastSyncedMs, now)}", Icons.Filled.CloudDone, scheme.primaryContainer, scheme.onPrimaryContainer)
    }

    Surface(shape = RoundedCornerShape(50), color = look.bg, contentColor = look.fg, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(look.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(look.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
