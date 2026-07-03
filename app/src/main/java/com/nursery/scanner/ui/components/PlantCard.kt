package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.scanner.ui.theme.Dimens

/**
 * The shared "plant card" (spec) reused by Sell (and later Phase 2). Shows the auto-filled plant
 * details, or, for a not-found scan, "Unknown plant" + the scanned accession code.
 */
@Composable
fun PlantCard(
    name: String,
    group: String?,
    accession: String,
    isUnknown: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.Gap)) {
            Text(
                text = if (isUnknown) "Unknown plant" else name,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Accession: $accession",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (isUnknown) {
                Text(
                    "Will be recorded as unknown and reconciled later.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                group?.takeIf { it.isNotBlank() }?.let {
                    Text("Group: $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
