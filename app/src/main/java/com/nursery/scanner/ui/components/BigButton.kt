package com.nursery.scanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nursery.scanner.ui.theme.Dimens

enum class BigButtonStyle { Primary, Secondary }

/**
 * Large, full-width, labelled button (>= 72dp tall, well above the 56dp accessibility minimum).
 * Always has a visible text label; an optional leading icon supplements but never replaces it.
 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    style: BigButtonStyle = BigButtonStyle.Primary,
) {
    val shape = RoundedCornerShape(Dimens.CardCorner)
    val inner: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall, Alignment.CenterHorizontally),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
            Text(text)
        }
    }
    val btnModifier = modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.BigButtonHeight)

    when (style) {
        BigButtonStyle.Primary ->
            Button(onClick = onClick, enabled = enabled, shape = shape, modifier = btnModifier) { inner() }
        BigButtonStyle.Secondary ->
            OutlinedButton(onClick = onClick, enabled = enabled, shape = shape, modifier = btnModifier) { inner() }
    }
}
