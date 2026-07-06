package com.nursery.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nursery.core.PaymentMethod
import com.nursery.scanner.ui.theme.Dimens

/**
 * Two-option segmented control for mutually exclusive choices (e.g. Card | Cash).
 * Single [surfaceVariant] track with a deep-green pill for the selected segment — a setting, not
 * an action, so it must not compete with full-width buttons like Scan another.
 */
@Composable
fun SegmentedControl(
    options: List<PaymentMethod>,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackShape = RoundedCornerShape(Dimens.CardCorner)
    val segmentShape = RoundedCornerShape(Dimens.CardCorner - 4.dp)
    val trackPadding = 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.FieldHeight)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(trackPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { option ->
                val isSelected = option == selected
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val segmentBackground = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(segmentShape)
                        .background(segmentBackground)
                        .clickable(enabled = enabled) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
                    ) {
                        Icon(
                            imageVector = option.icon(),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            option.displayLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

private fun PaymentMethod.icon(): ImageVector = when (this) {
    PaymentMethod.CARD -> Icons.Filled.CreditCard
    PaymentMethod.CASH -> Icons.Filled.Payments
}
