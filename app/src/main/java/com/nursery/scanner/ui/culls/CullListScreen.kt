package com.nursery.scanner.ui.culls

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.CullList
import com.nursery.core.CullRecord
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.formatDateTime

@Composable
fun CullListScreen(vm: CullListViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsStateWithLifecycle()
    val culls by vm.culls.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Culled plants", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Search culls") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.GapSmall),
        )
        when {
            culls.isEmpty() && query.isBlank() ->
                EmptyMessage("No culls recorded yet.")
            culls.isEmpty() ->
                EmptyMessage("No culls match “${query.trim()}”.")
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    items(culls, key = { it.localId }) { cull -> CullCard(cull) }
                }
        }
    }
}

@Composable
private fun CullCard(cull: CullRecord) {
    val pending = CullList.isPending(cull.status)
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(Dimens.StripeWidth)
                    .background(if (pending) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
            Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                Text(cull.name, style = MaterialTheme.typography.titleMedium)
                Text("Accession: ${cull.accession}", style = MaterialTheme.typography.bodyMedium)
                cull.group?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${cull.qty} ${cull.unit.labelFor(cull.qty)} · ${cull.reason.label}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                cull.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(cull.cullNo, style = MaterialTheme.typography.bodyMedium)
                Text(formatDateTime(cull.createdAtEpochMs), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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
