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
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Full-screen, scrollable, read-only list of the cached plants with a single search box that
 * filters across all fields. Reached from the Sync tab; works offline (it only reads the cache).
 */
@Composable
fun PlantListScreen(vm: PlantListViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsStateWithLifecycle()
    val plants by vm.plants.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Plants", onBack = onBack)
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
            // Blank query returns the whole cache, so empty-here-with-blank means nothing is cached.
            plants.isEmpty() && query.isBlank() ->
                EmptyMessage("No plants cached — tap Update plant list on the Sync screen.")
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
            // Per-accession counts: non-zero only, fixed order T · P · M · St (empty -> omitted).
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
