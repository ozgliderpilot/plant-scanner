package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.theme.Dimens

/**
 * Large − / value / + row (min 1 by default). Unit dropdown stays outside, composed adjacent.
 */
@Composable
fun QtyStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int? = null,
    onChange: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        FilledTonalIconButton(
            onClick = {
                if (value > min) {
                    onValueChange(value - 1)
                    onChange?.invoke()
                }
            },
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "One fewer")
        }
        Text("$value", style = MaterialTheme.typography.displaySmall)
        FilledTonalIconButton(
            onClick = {
                if (max == null || value < max) {
                    onValueChange(value + 1)
                    onChange?.invoke()
                }
            },
            modifier = Modifier.size(64.dp).testTag(TestTags.QTY_PLUS),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "One more")
        }
    }
}
