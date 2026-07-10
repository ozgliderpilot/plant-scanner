package com.nursery.scanner.ui.cull

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.CullRecord
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.PlantCard
import com.nursery.scanner.ui.components.ReasonDropdown
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.components.UnitDropdown
import com.nursery.scanner.ui.theme.Dimens

@Composable
fun EnterInfoScreen(
    vm: CullViewModel,
    onRecorded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft = ui.draft
    val focusManager = LocalFocusManager.current

    // After save, `saved` is set — navigate to success. If draft is missing (e.g. process death), go back.
    // Mirror CartScreen → ConfirmScreen (Sell flow).
    LaunchedEffect(ui.saved, draft) {
        when {
            ui.saved != null -> onRecorded()
            draft == null -> onBack()
        }
    }

    if (draft == null) return

    var qty by remember(draft) { mutableIntStateOf(draft.qty.coerceAtLeast(1)) }
    var unit by remember(draft) { mutableStateOf(draft.unit) }
    var reason by remember(draft) { mutableStateOf(draft.reason) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Record cull", onBack = { vm.discardDraft(); onBack() })
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
                isUnknown = draft.isUnknown,
            )

            Text("Quantity", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                FilledTonalIconButton(onClick = { if (qty > 1) qty-- }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "One fewer")
                }
                Text("$qty", style = MaterialTheme.typography.displaySmall)
                FilledTonalIconButton(onClick = { qty++ }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "One more")
                }
                UnitDropdown(selected = unit, qty = qty, onSelect = { unit = it }, modifier = Modifier.weight(1f))
            }

            Text("Reason", style = MaterialTheme.typography.titleMedium)
            ReasonDropdown(selected = reason, onSelect = { reason = it }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = CullRecord.sanitizeNotes(it) },
                label = { Text("Notes (optional)") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )

            BigButton(
                text = "Record cull",
                onClick = { vm.recordCull(qty = qty, unit = unit, reason = reason, notes = notes) },
                modifier = Modifier.testTag(TestTags.RECORD_CULL),
            )
        }
    }
}
