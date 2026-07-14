package com.nursery.scanner.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.nursery.scanner.BuildConfig
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.components.SyncTabHeader
import com.nursery.scanner.ui.components.rememberSettingsGate
import com.nursery.scanner.ui.theme.Dimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.scanner.ui.sync.SyncViewModel

/**
 * History tab hub: review past work (receipts, culls, labels) with cloud sync in the header.
 * Settings opens via 5× tap on the version string at the bottom.
 */
@Composable
fun HistoryScreen(
    vm: SyncViewModel,
    onViewReceipts: () -> Unit,
    onViewCulls: () -> Unit,
    onViewLabels: () -> Unit,
    onViewRepots: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val canExport = state.online && !state.isBusy && config.isComplete
    val onVersionTap = rememberSettingsGate(active = true, onTriggered = onOpenSettings)

    Column(modifier = modifier.fillMaxSize()) {
        SyncTabHeader(
            title = "History",
            lastUpdatedMs = state.lastSyncedMs,
            now = now,
            online = state.online,
            isBusy = state.isBusy,
            canSync = canExport,
            onSync = { vm.syncNow() },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            BigButton(
                text = "View Receipts",
                onClick = onViewReceipts,
                leadingIcon = Icons.Filled.ReceiptLong,
                style = BigButtonStyle.Primary,
                modifier = Modifier.testTag(TestTags.VIEW_RECEIPTS),
            )
            BigButton(
                text = "View Culled",
                onClick = onViewCulls,
                leadingIcon = Icons.Filled.LocalFlorist,
                style = BigButtonStyle.Primary,
                modifier = Modifier.testTag(TestTags.VIEW_CULLED),
            )
            BigButton(
                text = "View Labels",
                onClick = onViewLabels,
                leadingIcon = Icons.Filled.Label,
                style = BigButtonStyle.Primary,
                modifier = Modifier.testTag(TestTags.VIEW_LABELS),
            )
            BigButton(
                text = "View Repots",
                onClick = onViewRepots,
                leadingIcon = Icons.Filled.LocalFlorist,
                style = BigButtonStyle.Primary,
                modifier = Modifier.testTag(TestTags.VIEW_REPOTS),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.ScreenPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable(onClick = onVersionTap)
                    .padding(vertical = Dimens.GapSmall),
            )
        }
    }
}
