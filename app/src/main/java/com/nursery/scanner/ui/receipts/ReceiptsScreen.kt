package com.nursery.scanner.ui.receipts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.Money
import com.nursery.core.Receipt
import com.nursery.core.ReceiptStatus
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.formatDateTime

/** Receipts tab: sales history grouped by receipt (decision: tab screen, top/bottom bars from shell). */
@Composable
fun ReceiptsScreen(vm: ReceiptsViewModel, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val receipts by vm.receipts.collectAsStateWithLifecycle()

    if (receipts.isEmpty()) {
        Box(modifier.fillMaxSize().padding(Dimens.ScreenPadding), contentAlignment = Alignment.Center) {
            Text("No sales yet.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        items(receipts, key = { it.localId }) { receipt ->
            ReceiptCard(receipt = receipt, onClick = { onOpen(receipt.localId) })
        }
    }
}

@Composable
private fun ReceiptCard(receipt: Receipt, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Dimens.CardCorner),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(Dimens.Gap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(receipt.receiptNo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Money.formatAud(Money.receiptTotalCents(receipt.lines)), style = MaterialTheme.typography.titleMedium)
            }
            Text(formatDateTime(receipt.createdAtEpochMs), style = MaterialTheme.typography.bodyMedium)
            Text(statusLabel(receipt.status), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun statusLabel(status: ReceiptStatus): String = when (status) {
    ReceiptStatus.OPEN -> "Draft"
    ReceiptStatus.SAVED -> "Pending export"
    ReceiptStatus.EXPORTED -> "Exported"
}
