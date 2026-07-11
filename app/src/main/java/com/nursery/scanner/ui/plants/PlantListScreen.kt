package com.nursery.scanner.ui.plants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.nursery.core.Plant
import com.nursery.core.PlantStock
import com.nursery.scanner.data.repo.SyncState
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.components.SyncTabHeader
import com.nursery.scanner.ui.theme.Dimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Scrollable, read-only plant list with search. As a bottom-tab root it shows a cloud-sync
 * header with manual ↻; as a sub-screen it shows a back header instead.
 */
@Composable
fun PlantListScreen(
    vm: PlantListViewModel,
    syncState: SyncState,
    isTabRoot: Boolean,
    canUpdate: Boolean,
    onUpdate: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val plants by vm.plants.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    Column(modifier = modifier.fillMaxSize()) {
        if (isTabRoot) {
            SyncTabHeader(
                title = "Plants",
                lastUpdatedMs = syncState.lastPlantListUpdateMs,
                now = now,
                online = syncState.online,
                isBusy = syncState.isBusy,
                canSync = canUpdate,
                onSync = onUpdate,
            )
        } else {
            onBack?.let { ScreenHeader(title = "Plants", onBack = it) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Search plants") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.GapSmall),
        )
        when {
            plants.isEmpty() && query.isBlank() ->
                EmptyMessage(
                    if (isTabRoot) "No plants cached — tap ↻ above to update."
                    else "No plants cached.",
                )
            plants.isEmpty() ->
                EmptyMessage("No plants match “${query.trim()}”.")
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    items(plants, key = { it.accession }) { plant -> PlantRow(plant) }
                }
        }
    }
}

@Composable
private fun PlantRow(plant: Plant) {
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.Gap)) {
            Text(plant.name, style = MaterialTheme.typography.titleMedium)
            Text("Accession: ${plant.accession}", style = MaterialTheme.typography.bodyMedium)
            plant.group?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            PlantStock.summary(plant).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
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
