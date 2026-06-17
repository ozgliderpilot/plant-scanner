package com.nursery.scanner.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nursery.core.Money
import com.nursery.core.Receipt
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.formatDateTime

@Composable
fun ReceiptDetailScreen(
    vm: ReceiptsViewModel,
    receiptId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val receipt by produceState<Receipt?>(initialValue = null, receiptId) { value = vm.load(receiptId) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Receipt", onBack = onBack)
        val r = receipt
        if (r == null) {
            Text("Loading…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Dimens.ScreenPadding))
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
        ) {
            Text(r.receiptNo, style = MaterialTheme.typography.headlineSmall)
            Text(formatDateTime(r.createdAtEpochMs), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.GapSmall))

            r.lines.forEach { line ->
                val total = Money.lineTotalCents(line.qty, line.unitPriceCents, line.discountPct)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, style = MaterialTheme.typography.titleMedium)
                        val disc = if (line.discountPct > 0) "  −${line.discountPct}%" else ""
                        Text(
                            "${line.qty} ${line.unit.labelFor(line.qty)} × ${Money.formatAud(line.unitPriceCents)}$disc",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Accession: ${line.accession}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(Money.formatAud(total), style = MaterialTheme.typography.titleMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.GapSmall))
            Text(
                "Total: ${Money.formatAud(Money.receiptTotalCents(r.lines))}",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
