package com.nursery.scanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.theme.Dimens

/**
 * Action-first home: Sell, Cull, Print label, and Repot enabled; New accession dimmed.
 */
@Composable
fun HomeScreen(
    onSell: () -> Unit,
    onCull: () -> Unit,
    onPrintLabel: () -> Unit,
    onRepot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        BigButton(
            text = "Sell plants",
            onClick = onSell,
            leadingIcon = Icons.Filled.PointOfSale,
            style = BigButtonStyle.Primary,
            modifier = Modifier.testTag(TestTags.SELL_PLANTS),
        )
        BigButton(
            text = "Cull plants",
            onClick = onCull,
            leadingIcon = Icons.Filled.LocalFlorist,
            style = BigButtonStyle.Primary,
            modifier = Modifier.testTag(TestTags.CULL_PLANTS),
        )
        BigButton(
            text = "Print label",
            onClick = onPrintLabel,
            leadingIcon = Icons.Filled.Label,
            style = BigButtonStyle.Primary,
            modifier = Modifier.testTag(TestTags.PRINT_LABEL),
        )
        BigButton(
            text = "Repot",
            onClick = onRepot,
            leadingIcon = Icons.Filled.LocalFlorist,
            style = BigButtonStyle.Primary,
            modifier = Modifier.testTag(TestTags.REPOT),
        )

        Text(
            "Coming later",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Dimens.Gap),
        )

        BigButton(
            text = "New accession",
            onClick = {},
            enabled = false,
            leadingIcon = Icons.Filled.LocalFlorist,
            style = BigButtonStyle.Secondary,
        )
    }
}
