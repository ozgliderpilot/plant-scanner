package com.nursery.scanner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** One-shot Done/Error from manual ↻ (History / Plants). */
@Composable
fun SyncResultDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text("Sync") },
        text = { Text(message) },
    )
}
