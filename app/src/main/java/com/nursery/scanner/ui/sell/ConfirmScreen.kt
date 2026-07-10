package com.nursery.scanner.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.Money
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.theme.Dimens

/**
 * ④ Confirmation: receipt #, total to collect, "Saved locally · N pending". Payment is handled
 * outside the app (decision #3) — this is just the amount to collect.
 */
@Composable
fun ConfirmScreen(
    vm: SellViewModel,
    pendingCount: Int,
    onNewSale: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved = ui.saved

    if (saved == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text("Receipt ${saved.receiptNo}", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Total to collect",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            Money.formatAud(Money.receiptTotalCents(saved.lines)),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            "Payment: ${saved.paymentMethod.displayLabel}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Saved locally · $pendingCount pending",
            style = MaterialTheme.typography.bodyLarge,
        )

        BigButton(text = "New sale", onClick = onNewSale)
        BigButton(
            text = "Done",
            onClick = onDone,
            style = BigButtonStyle.Secondary,
            modifier = Modifier.testTag(TestTags.DONE),
        )
    }
}
