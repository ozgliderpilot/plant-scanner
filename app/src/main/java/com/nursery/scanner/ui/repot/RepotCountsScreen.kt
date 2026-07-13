package com.nursery.scanner.ui.repot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.PlantCard
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens

@Composable
fun RepotCountsScreen(
    vm: RepotViewModel,
    onRecorded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft = ui.draft

    LaunchedEffect(ui.saved, draft) {
        when {
            ui.saved != null -> onRecorded()
            draft == null -> onBack()
        }
    }

    if (draft == null) return

    var tubes by remember(draft) { mutableIntStateOf(draft.tubesBefore) }
    var pots by remember(draft) { mutableIntStateOf(draft.potsBefore) }
    var misc by remember(draft) { mutableIntStateOf(draft.miscBefore) }
    var stock by remember(draft) { mutableIntStateOf(draft.stockBefore) }
    var tubesForSale by remember(draft) { mutableStateOf(draft.initialForSale.tubes) }
    var potsForSale by remember(draft) { mutableStateOf(draft.initialForSale.pots) }
    var miscForSale by remember(draft) { mutableStateOf(draft.initialForSale.misc) }

    if (ui.needsAllZeroConfirm) {
        AlertDialog(
            onDismissRequest = { vm.clearAllZeroConfirm() },
            title = { Text("No plants left for this accession. Is that correct?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveRepot(
                            tubes, pots, misc, stock,
                            tubesForSale, potsForSale, miscForSale,
                            confirmedAllZero = true,
                        )
                    },
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearAllZeroConfirm() }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Update counts", onBack = { vm.discardDraft(); onBack() })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            PlantCard(
                name = draft.name,
                group = draft.group,
                accession = draft.accession,
                isUnknown = false,
            )

            if (draft.stockSummary.isNotEmpty()) {
                Text(
                    "Current: ${draft.stockSummary}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            CountStepper(
                label = "Tubes",
                value = tubes,
                onChange = { tubes = it },
                plusTestTag = TestTags.QTY_PLUS,
            )
            ReadyForSaleTick(
                label = "Ready for sale (Tubes)",
                checked = tubesForSale,
                onCheckedChange = { tubesForSale = it },
            )

            CountStepper(label = "Pots", value = pots, onChange = { pots = it })
            ReadyForSaleTick(
                label = "Ready for sale (Pots)",
                checked = potsForSale,
                onCheckedChange = { potsForSale = it },
            )

            CountStepper(label = "Misc.", value = misc, onChange = { misc = it })
            ReadyForSaleTick(
                label = "Ready for sale (Misc.)",
                checked = miscForSale,
                onCheckedChange = { miscForSale = it },
            )

            CountStepper(label = "Stock plant", value = stock, onChange = { stock = it })

            ui.submitError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BigButton(
                text = "Save repot",
                onClick = {
                    vm.saveRepot(tubes, pots, misc, stock, tubesForSale, potsForSale, miscForSale)
                },
                modifier = Modifier.testTag(TestTags.SAVE_REPOT),
            )
        }
    }
}

@Composable
private fun CountStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    plusTestTag: String? = null,
) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        FilledTonalIconButton(
            onClick = { if (value > 0) onChange(value - 1) },
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "One fewer $label")
        }
        Text("$value", style = MaterialTheme.typography.displaySmall)
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier
                .size(64.dp)
                .then(if (plusTestTag != null) Modifier.testTag(plusTestTag) else Modifier),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "One more $label")
        }
    }
}

@Composable
private fun ReadyForSaleTick(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(vertical = Dimens.GapSmall),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Dimens.GapSmall),
        )
    }
}
