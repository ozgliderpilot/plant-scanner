package com.nursery.scanner.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.scanner.ui.components.BarcodeScanShell
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.theme.Dimens

/**
 * ① Scan a Code 128 barcode, or type the accession number. A found plant advances to the line-item
 * form; a miss shows the not-found options (Retry · Type # · Sell as unknown) — never lost (#7).
 */
@Composable
fun ScanScreen(
    vm: SellViewModel,
    onResolved: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.resolved.collect { onResolved() }
    }

    BarcodeScanShell(
        title = "Scan plant",
        scanning = ui.notFoundCode == null,
        onBarcode = { code -> vm.onCode(code) },
        onClose = onClose,
        permissionMessage = "The camera is used to scan plant barcodes.",
        modifier = modifier,
        onTypedCode = { code -> vm.onCode(code) },
        notFound = {
            val notFound = ui.notFoundCode
            if (notFound != null) {
                Card(
                    shape = RoundedCornerShape(Dimens.CardCorner),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.Gap),
                        verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
                    ) {
                        Text("Not in plant list", style = MaterialTheme.typography.titleMedium)
                        Text("Scanned: $notFound", style = MaterialTheme.typography.bodyMedium)
                        BigButton(text = "Sell as unknown", onClick = { vm.sellAsUnknown() })
                        BigButton(text = "Retry", onClick = { vm.clearNotFound() }, style = BigButtonStyle.Secondary)
                    }
                }
            }
        },
    )
}
