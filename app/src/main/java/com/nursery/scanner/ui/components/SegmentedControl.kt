package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.core.PaymentMethod
import com.nursery.scanner.ui.theme.Dimens

/**
 * Two-option segmented control for mutually exclusive choices (e.g. Card | Cash).
 * Selected option uses the same light green fill as quantity steppers; unselected matches
 * secondary [BigButton]s (white outlined). Large tap targets for elderly volunteers.
 */
@Composable
fun SegmentedControl(
    options: List<PaymentMethod>,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
    ) {
        val selectedColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(Dimens.CardCorner)
            val btnModifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.BigButtonHeight)
            val label = option.displayLabel
            if (isSelected) {
                FilledTonalButton(
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    shape = shape,
                    modifier = btnModifier,
                    colors = selectedColors,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    shape = shape,
                    modifier = btnModifier,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}
