package com.nursery.scanner.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nursery.scanner.ci.CiMode

/**
 * Camera preview slot shared by sell and cull scan screens.
 * In CI mode shows a static placeholder instead of CameraX.
 */
@Composable
fun ScannerSlot(
    scanning: Boolean,
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (CiMode.useCameraPlaceholder) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Camera preview (CI)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        ScannerView(
            modifier = modifier.fillMaxSize(),
            scanning = scanning,
            onBarcode = onBarcode,
        )
    }
}
