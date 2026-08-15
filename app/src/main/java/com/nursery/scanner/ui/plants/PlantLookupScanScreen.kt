package com.nursery.scanner.ui.plants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nursery.scanner.ui.components.BarcodeScanShell

/**
 * Lookup-only accession scan for the plant list. On a read barcode, [onScanned] returns the
 * accession to the list search — no plant-book resolve and no unknown-scan record.
 */
@Composable
fun PlantLookupScanScreen(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var armed by remember { mutableStateOf(true) }

    BarcodeScanShell(
        title = "Scan accession",
        scanning = armed,
        onBarcode = { code ->
            if (armed && code.isNotBlank()) {
                armed = false
                onScanned(code)
            }
        },
        onClose = onClose,
        permissionMessage = "The camera is used to scan plant accessions.",
        modifier = modifier,
    )
}
