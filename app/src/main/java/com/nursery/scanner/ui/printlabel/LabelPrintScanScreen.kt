package com.nursery.scanner.ui.printlabel

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.LabelPrintRequest
import com.nursery.scanner.ci.CiMode
import com.nursery.scanner.scanner.ScannerSlot
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens

@Composable
fun LabelPrintScanScreen(
    vm: LabelPrintViewModel,
    onResolved: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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

    var showType by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val typedFocus = remember { FocusRequester() }

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

    LaunchedEffect(showType) {
        if (showType) {
            typedFocus.requestFocus()
            keyboard?.show()
        }
    }

    BackHandler { onClose() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Print label", onBack = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            if (!hasCamera) {
                Text("The camera is used to scan plant barcodes.", style = MaterialTheme.typography.bodyLarge)
                BigButton(text = "Allow camera", onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            } else if (!showType) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.CardCorner)),
                ) {
                    ScannerSlot(
                        modifier = Modifier.fillMaxSize(),
                        scanning = ui.notFoundCode == null,
                        onBarcode = { code -> vm.onCode(code) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
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
                            Text(
                                LabelPrintRequest.NOT_FOUND_MESSAGE,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Scanned: $notFound", style = MaterialTheme.typography.bodyMedium)
                            BigButton(
                                text = "Retry",
                                onClick = { vm.clearNotFound() },
                                style = BigButtonStyle.Secondary,
                            )
                        }
                    }
                }

                BigButton(
                    text = if (showType) "Hide keypad" else "Type number instead",
                    onClick = { showType = !showType },
                    style = BigButtonStyle.Secondary,
                    modifier = Modifier.testTag(TestTags.TYPE_NUMBER),
                )
                if (showType) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Accession number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitTyped() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(typedFocus),
                    )
                    BigButton(
                        text = "Find",
                        onClick = submitTyped,
                        enabled = typed.isNotBlank(),
                        modifier = Modifier.testTag(TestTags.FIND),
                    )
                }
            }
        }
    }
}
