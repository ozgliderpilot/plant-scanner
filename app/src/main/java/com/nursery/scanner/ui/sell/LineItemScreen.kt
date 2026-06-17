package com.nursery.scanner.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.Money
import com.nursery.core.SaleUnit
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.PlantCard
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.centsToEditable
import com.nursery.scanner.util.parseDollarsToCents

/**
 * ② Line item: plant card auto-filled; keyed Pots, Unit price, Discount %. Live line total =
 * pots × price × (1 − discount%) (spec). Unit price is always keyed — no pre-fill (#6).
 */
@Composable
fun LineItemScreen(
    vm: SellViewModel,
    onAdded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft = ui.draft

    // Defensive: if there is no draft (e.g. process death), go back rather than crash.
    if (draft == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var qty by remember(draft) { mutableIntStateOf(draft.qty.coerceAtLeast(1)) }
    var unit by remember(draft) { mutableStateOf(draft.unit) }
    var priceText by remember(draft) { mutableStateOf(centsToEditable(draft.unitPriceCents)) }
    var discountText by remember(draft) { mutableStateOf(if (draft.discountPct == 0) "" else draft.discountPct.toString()) }

    val unitPriceCents = parseDollarsToCents(priceText) ?: 0L
    val discountPct = (discountText.toIntOrNull() ?: 0).coerceIn(0, 100)
    val lineTotal = Money.lineTotalCents(qty, unitPriceCents, discountPct)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (draft.editIndex != null) "Edit item" else "Line item",
            onBack = { vm.discardDraft(); onBack() },
        )
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
                light = draft.light,
                accession = draft.accession,
                isUnknown = draft.isUnknown,
            )

            UnitDropdown(selected = unit, onSelect = { unit = it })

            // Quantity stepper
            Text("Quantity", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                FilledTonalIconButton(onClick = { if (qty > 1) qty-- }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "One fewer")
                }
                Text("$qty", style = MaterialTheme.typography.displaySmall)
                FilledTonalIconButton(onClick = { qty++ }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "One more")
                }
            }

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Unit price (\$)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = discountText,
                onValueChange = { discountText = it.filter(Char::isDigit).take(3) },
                label = { Text("Discount %") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Line total: ${Money.formatAud(lineTotal)}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Dimens.GapSmall),
            )

            BigButton(
                text = if (draft.editIndex != null) "Save changes" else "Add to receipt",
                onClick = {
                    vm.commitDraft(qty = qty, unitPriceCents = unitPriceCents, discountPct = discountPct, unit = unit)
                    onAdded()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(selected: SaleUnit, onSelect: (SaleUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SaleUnit.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
