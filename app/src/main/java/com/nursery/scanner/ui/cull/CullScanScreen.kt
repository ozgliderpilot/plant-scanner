package com.nursery.scanner.ui.cull

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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.scanner.ci.CiMode
import com.nursery.scanner.ci.CiScanPlaceholder
import com.nursery.scanner.scanner.ScannerView
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens

@Composable
fun CullScanScreen(
    vm: CullViewModel,
    onResolved: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var hasCamera by remember {
        mutableStateOf(
            CiMode.skipCameraPermission ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    var showType by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    val submitTyped = {
        if (typed.isNotBlank()) {
            vm.onCode(typed)
            typed = ""
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(Unit) {
        vm.resolved.collect { onResolved() }
    }

    BackHandler { onClose() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Cull plant", onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            if (!hasCamera) {
                Text("The camera is used to scan plant barcodes.", style = MaterialTheme.typography.bodyLarge)
                BigButton(text = "Allow camera", onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.CardCorner)),
                ) {
                    if (CiMode.useCameraPlaceholder) {
                        CiScanPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        ScannerView(
                            modifier = Modifier.fillMaxSize(),
                            scanning = ui.notFoundCode == null,
                            onBarcode = { code -> vm.onCode(code) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                val notFound = ui.notFoundCode
                if (notFound != null) {
                    Card(
                        shape = RoundedCornerShape(Dimens.CardCorner),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(Dimens.Gap), verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
                            Text("Not in plant list", style = MaterialTheme.typography.titleMedium)
                            Text("Scanned: $notFound", style = MaterialTheme.typography.bodyMedium)
                            BigButton(text = "Cull as unknown", onClick = { vm.cullAsUnknown() })
                            BigButton(text = "Retry", onClick = { vm.clearNotFound() }, style = BigButtonStyle.Secondary)
                        }
                    }
                }

                BigButton(
                    text = if (showType) "Hide keypad" else "Type number instead",
                    onClick = { showType = !showType },
                    style = BigButtonStyle.Secondary,
                )
                if (showType) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Accession number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitTyped() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BigButton(text = "Find", onClick = submitTyped, enabled = typed.isNotBlank())
                }
            }
        }
    }
}
