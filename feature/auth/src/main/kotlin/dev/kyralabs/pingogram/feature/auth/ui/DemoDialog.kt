package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.auth.R

/**
 * Whether to open the demo (`docs/adr/0034-demo-mode-for-store-review.md`, `docs/DESIGN_SYSTEM.md` §6 "Demo"), after
 * the Welcome screen's logo was held: an `AlertDialog`, "Open the demo?", what it shows, "Cancel" and "Open".
 */
@Composable
internal fun DemoDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_demo_title)) },
        text = { Text(stringResource(R.string.auth_demo_text)) },
        confirmButton = {
            TextButton(onClick = onOpen, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.auth_demo_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.auth_demo_cancel))
            }
        },
    )
}

@PingogramPreviews
@Composable
private fun DemoDialogPreview() {
    PingogramPreview { DemoDialog(onOpen = {}, onDismiss = {}) }
}
