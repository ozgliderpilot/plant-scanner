package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.scanner.util.updatedAgoLabel

/**
 * Per-tab sync header for History (export) and Plants (import): title, manual ↻ action,
 * and "Updated X ago" subtitle.
 */
@Composable
fun SyncTabHeader(
    title: String,
    lastUpdatedMs: Long?,
    now: Long,
    online: Boolean,
    isBusy: Boolean,
    canSync: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                } else {
                    IconButton(
                        onClick = onSync,
                        enabled = canSync,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync now")
                    }
                }
            }
            Text(
                updatedAgoLabel(lastUpdatedMs, now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!online) {
                Text(
                    "Needs Wi-Fi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
