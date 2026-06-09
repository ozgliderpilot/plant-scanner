package com.nursery.scanner.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.relativeTime

/**
 * Sync tab. Sales export is automatic (silent ticker); these are the two MANUAL one-tap jobs
 * (spec #9): Update plant list, and Export now. Both disabled offline ("needs Wi-Fi").
 */
@Composable
fun SyncScreen(vm: SyncViewModel, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val plantCount by vm.plantCount.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    val canTalk = state.online && !state.isBusy && config.isComplete

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        if (!config.isComplete) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                    Text("Not connected yet", style = MaterialTheme.typography.titleMedium)
                    Text("Enter the Google Sheets link and access code to start syncing.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Plant list (pull)
        Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
            Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                Text("Plant list", style = MaterialTheme.typography.titleMedium)
                Text("$plantCount plants cached", style = MaterialTheme.typography.bodyLarge)
                BigButton(
                    text = "Update plant list",
                    onClick = { vm.updatePlantList() },
                    enabled = canTalk,
                )
                if (!state.online) Text("Needs Wi-Fi", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Sales export (push)
        Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
            Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                Text("Sales export", style = MaterialTheme.typography.titleMedium)
                Text("Auto every ${intervalLabel(config.autoExportSeconds)}", style = MaterialTheme.typography.bodyLarge)
                Text("${state.pendingCount} pending · last ${relativeTime(state.lastSyncedMs, now)}", style = MaterialTheme.typography.bodyMedium)
                BigButton(
                    text = "Export now",
                    onClick = { vm.exportNow() },
                    enabled = canTalk,
                )
                if (!state.online) Text("Needs Wi-Fi", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
        message?.let { msg ->
            Card(shape = RoundedCornerShape(Dimens.CardCorner)) {
                Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                    Text(msg, style = MaterialTheme.typography.bodyLarge)
                    BigButton(text = "OK", onClick = { vm.clearMessage() }, style = BigButtonStyle.Secondary)
                }
            }
        }

        BigButton(text = "Settings", onClick = onSettings, style = BigButtonStyle.Secondary)
    }
}

private fun intervalLabel(seconds: Int): String =
    if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds s"
