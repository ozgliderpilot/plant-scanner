package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.scanner.data.repo.SyncState
import com.nursery.scanner.ui.nav.BottomTabs

/** Top bar: screen title on the left, persistent sync status chip on the right. */
@Composable
fun NurseryTopBar(title: String, syncState: SyncState, now: Long) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusChip(state = syncState, now = now)
        }
    }
}

/** Bottom 3-tab bar; every item keeps its text label visible (never icon-only). */
@Composable
fun NurseryBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        BottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                alwaysShowLabel = true,
            )
        }
    }
}
