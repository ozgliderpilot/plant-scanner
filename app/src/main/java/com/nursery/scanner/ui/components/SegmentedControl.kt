package com.nursery.scanner.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.core.PaymentMethod
import com.nursery.scanner.ui.theme.Dimens

/**
 * Two-option segmented control for mutually exclusive choices (e.g. Card | Cash).
 * Large tap targets and a clear selected state for elderly volunteers.
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
        val tonalColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(Dimens.CardCorner)
            val btnModifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.BigButtonHeight)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                )
            val label = option.displayLabel
            FilledTonalButton(
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = shape,
                modifier = btnModifier,
                colors = tonalColors,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
