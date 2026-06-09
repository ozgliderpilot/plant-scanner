package com.nursery.scanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens

/** Per-device setup: receipt prefix (#11), Google Sheets Web App URL + access code, export interval. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()

    var prefix by remember(config) { mutableStateOf(config.devicePrefix) }
    var url by remember(config) { mutableStateOf(config.endpointUrl) }
    var secret by remember(config) { mutableStateOf(config.sharedSecret) }
    var intervalText by remember(config) { mutableStateOf(config.autoExportSeconds.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it.filter(Char::isDigit).take(2) },
                label = { Text("Device prefix (two digits)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Google Sheets Web App URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("Access code (shared secret)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter(Char::isDigit).take(5) },
                label = { Text("Auto-export every (seconds)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }

            BigButton(
                text = "Save",
                onClick = {
                    val seconds = intervalText.toIntOrNull() ?: 0
                    val err = vm.save(prefix = prefix, url = url, secret = secret, intervalSeconds = seconds)
                    if (err == null) onBack() else error = err
                },
            )
        }
    }
}
