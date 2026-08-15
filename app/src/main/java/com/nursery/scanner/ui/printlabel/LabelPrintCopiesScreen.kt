package com.nursery.scanner.ui.printlabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nursery.core.LabelPrintRequest
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.PlantCard
import com.nursery.scanner.ui.components.QtyStepper
import com.nursery.scanner.ui.components.ScreenHeader
import com.nursery.scanner.ui.theme.Dimens

@Composable
fun LabelPrintCopiesScreen(
    vm: LabelPrintViewModel,
    onRecorded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft = ui.draft

    LaunchedEffect(ui.saved, draft) {
        when {
            ui.saved != null -> onRecorded()
            draft == null -> onBack()
        }
    }

    if (draft == null) return

    var copies by remember(draft) { mutableIntStateOf(1) }
    val copiesCeiling = minOf(draft.stockTotal, LabelPrintRequest.COPIES_MAX).coerceAtLeast(1)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Print label", onBack = { vm.discardDraft(); onBack() })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            PlantCard(
                name = draft.name,
                group = draft.group,
                accession = draft.accession,
                isUnknown = false,
            )

            if (draft.stockSummary.isNotEmpty()) {
                Text(
                    "Nursery stock: ${draft.stockSummary}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Text("Copies", style = MaterialTheme.typography.titleMedium)
            QtyStepper(
                value = copies,
                onValueChange = { copies = it },
                max = copiesCeiling,
                onChange = { vm.clearSubmitError() },
            )

            ui.submitError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BigButton(
                text = "Confirm print",
                onClick = { vm.confirmPrint(copies) },
                modifier = Modifier.testTag(TestTags.CONFIRM_PRINT),
            )
        }
    }
}
