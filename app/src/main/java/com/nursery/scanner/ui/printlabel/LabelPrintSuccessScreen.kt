package com.nursery.scanner.ui.printlabel

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
import com.nursery.scanner.ui.TestTags
import com.nursery.scanner.ui.components.BigButton
import com.nursery.scanner.ui.components.BigButtonStyle
import com.nursery.scanner.ui.theme.Dimens

/** Label print request saved confirmation. Pending sync count is intentionally not shown. */
@Composable
fun LabelPrintSuccessScreen(
    vm: LabelPrintViewModel,
    onPrintAnother: () -> Unit,
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
        Text(saved.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${saved.copies} ${if (saved.copies == 1) "copy" else "copies"} queued",
            style = MaterialTheme.typography.bodyLarge,
        )
        BigButton(text = "Print another", onClick = onPrintAnother)
        BigButton(
            text = "Done",
            onClick = onDone,
            style = BigButtonStyle.Secondary,
            modifier = Modifier.testTag(TestTags.DONE),
        )
    }
}
