package com.nursery.scanner.ui.labels

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
import com.nursery.core.LabelPrintList
import com.nursery.core.LabelPrintRequest
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens
import com.nursery.scanner.util.formatDateTime

@Composable
fun LabelListScreen(vm: LabelListViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsStateWithLifecycle()
    val requests by vm.requests.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Labels", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Search labels") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.GapSmall),
        )
        when {
            requests.isEmpty() && query.isBlank() ->
                EmptyMessage("No label prints recorded yet.")
            requests.isEmpty() ->
                EmptyMessage("No labels match “${query.trim()}”.")
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    items(requests, key = { it.localId }) { request -> LabelCard(request) }
                }
        }
    }
}

@Composable
private fun LabelCard(request: LabelPrintRequest) {
    val pending = LabelPrintList.isPending(request.status)
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(Dimens.StripeWidth)
                    .background(if (pending) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
            Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                Text(request.name, style = MaterialTheme.typography.titleMedium)
                Text("Accession: ${request.accession}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (request.copies == 1) "1 label" else "${request.copies} labels",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatDateTime(request.createdAtEpochMs),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
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
