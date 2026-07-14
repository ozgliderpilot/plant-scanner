package com.nursery.scanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.scanner.ui.components.ConnectivityChip

/** Actions tab: GF Nursery header with connectivity chip, then the action buttons. */
@Composable
fun ActionsTabScreen(
    online: Boolean,
    onSell: () -> Unit,
    onCull: () -> Unit,
    onPrintLabel: () -> Unit,
    onRepot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "GF Nursery",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                ConnectivityChip(online = online)
            }
        }
        HomeScreen(
            onSell = onSell,
            onCull = onCull,
            onPrintLabel = onPrintLabel,
            onRepot = onRepot,
            modifier = Modifier.weight(1f),
        )
    }
}
