package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.drop

/**
 * Renaming a community: a [BasicAlertDialog] in the alert dialog's shape and colors, "Rename community" in
 * `titleLargeEmphasized` (a heading), the name in a filled one-line [TextField] (user content, words capitalized), a
 * [LinearWavyProgressIndicator] while Telegram answers, then "Cancel" and the default [Button] "Save", disabled while
 * the name is blank. Back and a tap outside close it as "Cancel" does.
 */
@Composable
internal fun RenameCommunityDialog(
    name: String,
    isBusy: Boolean,
    onEvent: (CommunityEvent) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = { onEvent(CommunityEvent.DismissDialog) }) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            RenameCommunityContent(name, isBusy, onEvent)
        }
    }
}

/** What the rename dialog holds: its title, the name's field, progress and the buttons. */
@Composable
internal fun RenameCommunityContent(
    name: String,
    isBusy: Boolean,
    onEvent: (CommunityEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val text = rememberTextFieldState(name)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect { currentOnEvent(CommunityEvent.NameChanged(it)) }
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.community_rename_title),
            modifier = Modifier.semantics { heading() },
            color = AlertDialogDefaults.titleContentColor,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        TextField(
            state = text,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            textStyle = MaterialTheme.typography.bodyLarge.userContent,
            label = { Text(stringResource(R.string.community_name)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        if (isBusy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
        ) {
            TextButton(
                onClick = { onEvent(CommunityEvent.DismissDialog) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.community_cancel), style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { onEvent(CommunityEvent.Confirm) },
                enabled = !isBusy && name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(R.string.community_save),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}
