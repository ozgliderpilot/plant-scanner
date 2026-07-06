package com.nursery.scanner.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.LineItem
import com.nursery.core.Money
import com.nursery.core.PaymentMethod
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.components.SegmentedControl
import com.nursery.scanner.ui.theme.Dimens

/**
 * ③ The receipt (cart): running line list + Total. Tap a line to edit; an explicit Remove button
 * deletes it (no swipe-to-delete — accessibility rule). Scan another, or Finish & save.
 */
@Composable
fun CartScreen(
    vm: SellViewModel,
    onScanAnother: () -> Unit,
    onEditLine: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.saved) {
        if (ui.saved != null) onSaved()
    }

    // Leaving the cart drops the in-progress receipt, so confirm first when there's anything to lose.
    // An empty cart leaves straight away. Both the header arrow and system back route through here.
    var showDiscard by remember { mutableStateOf(false) }
    val attemptBack = {
        if (ui.lines.isNotEmpty()) showDiscard = true else onBack()
    }
    BackHandler { attemptBack() }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard this sale?") },
            text = { Text("The items in this sale will be lost.") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onBack() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Current sale", onBack = attemptBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
        ) {
            if (ui.lines.isEmpty()) {
                Text("No items yet — scan a plant to start.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(Dimens.Gap))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                    ui.lines.forEachIndexed { index, line ->
                        LineRow(
                            line = line,
                            onEdit = { vm.beginEdit(index); onEditLine() },
                            onRemove = { vm.removeLine(index) },
                        )
                    }
                    HorizontalDivider()
                    Text(
                        "Total: ${Money.formatAud(ui.totalCents)}",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text("How will they pay?", style = MaterialTheme.typography.titleMedium)
                    SegmentedControl(
                        options = PaymentMethod.entries,
                        selected = ui.paymentMethod,
                        onSelect = vm::setPaymentMethod,
                        enabled = !ui.isSaving,
                    )
                }
                Spacer(Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(Modifier.height(Dimens.Gap))
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                BigButton(
                    text = "Scan another",
                    onClick = onScanAnother,
                    leadingIcon = Icons.Filled.Add,
                    style = BigButtonStyle.Secondary,
                    enabled = !ui.isSaving,
                )
                BigButton(
                    text = "Finish & save",
                    onClick = { vm.finishAndSave() },
                    enabled = ui.lines.isNotEmpty() && !ui.isSaving,
                )
            }
        }
    }
}

@Composable
private fun LineRow(line: LineItem, onEdit: () -> Unit, onRemove: () -> Unit) {
    val total = Money.lineTotalCents(line.qty, line.unitPriceCents, line.discountPct)
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.Gap)) {
            // Tapping the body edits the line (explicit tap target, not a gesture).
            Column(modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)) {
                Text(line.name, style = MaterialTheme.typography.titleMedium)
                val discountLabel = if (line.discountPct > 0) "  −${line.discountPct}%" else ""
                Text(
                    "${line.qty} ${line.unit.labelFor(line.qty)} × ${Money.formatAud(line.unitPriceCents)}$discountLabel",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    Money.formatAud(total),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) { Text("Edit", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onRemove) { Text("Remove", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
