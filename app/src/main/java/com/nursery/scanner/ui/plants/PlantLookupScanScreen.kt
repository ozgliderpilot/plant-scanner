package com.nursery.scanner.ui.plants

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nursery.scanner.ci.CiMode
import com.nursery.scanner.scanner.ScannerSlot
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens

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
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            CiMode.active ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    var armed by remember { mutableStateOf(true) }

    BackHandler { onClose() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Scan accession", onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            if (!hasCamera) {
                Text(
                    "The camera is used to scan plant accessions.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                BigButton(
                    text = "Allow camera",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.CardCorner)),
                ) {
                    ScannerSlot(
                        modifier = Modifier.fillMaxSize(),
                        scanning = armed,
                        onBarcode = { code ->
                            if (armed && code.isNotBlank()) {
                                armed = false
                                onScanned(code)
                            }
                        },
                    )
                }
            }
        }
    }
}
