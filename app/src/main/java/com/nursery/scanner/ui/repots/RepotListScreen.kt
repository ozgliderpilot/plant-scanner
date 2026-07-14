package com.nursery.scanner.ui.repots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.RepotList
import com.nursery.core.RepotRecord
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.formatDateTime

@Composable
fun RepotListScreen(vm: RepotListViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsStateWithLifecycle()
    val repots by vm.repots.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Repots", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Search repots") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.GapSmall),
        )
        when {
            repots.isEmpty() && query.isBlank() ->
                EmptyMessage("No repots recorded yet.")
            repots.isEmpty() ->
                EmptyMessage("No repots match “${query.trim()}”.")
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    items(repots, key = { it.localId }) { repot -> RepotCard(repot) }
                }
        }
    }
}

@Composable
private fun RepotCard(repot: RepotRecord) {
    val pending = RepotList.isPending(repot.status)
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(Dimens.StripeWidth)
                    .background(if (pending) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
            Column(
                modifier = Modifier.padding(Dimens.Gap),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            ) {
                Text(repot.name, style = MaterialTheme.typography.titleMedium)
                Text("Accession: ${repot.accession}", style = MaterialTheme.typography.bodyMedium)
                Text(repot.repotNo, style = MaterialTheme.typography.bodyMedium)
                Text(
                    countsLine(repot),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatDateTime(repot.createdAtEpochMs),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun countsLine(repot: RepotRecord): String {
    val parts = buildList {
        if (repot.tubesBefore != repot.tubes) {
            add("T ${repot.tubesBefore} -> ${repot.tubes}")
        } else if (repot.tubes > 0) {
            add("T ${repot.tubes}")
        }
        if (repot.potsBefore != repot.pots) {
            add("P ${repot.potsBefore} -> ${repot.pots}")
        } else if (repot.pots > 0) {
            add("P ${repot.pots}")
        }
        if (repot.miscBefore != repot.misc) {
            add("M ${repot.miscBefore} -> ${repot.misc}")
        } else if (repot.misc > 0) {
            add("M ${repot.misc}")
        }
        if (repot.stockBefore != repot.stock) {
            add("St ${repot.stockBefore} -> ${repot.stock}")
        } else if (repot.stock > 0) {
            add("St ${repot.stock}")
        }
    }
    return parts.joinToString(" · ").ifEmpty { "No count change" }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
