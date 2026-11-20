package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R

/**
 * An album's name, for a new album or a renamed one (owner decision 2026-11-20): a `BasicAlertDialog` in the alert
 * dialog's shape and colors, [title] in `titleLargeEmphasized` (a heading) over a filled one-line `TextField` holding
 * [initial] (user content, focused as it opens, kept across process death), then "Cancel" and [confirm], disabled
 * while the name is blank or unchanged; the keyboard's Done confirms too. The pinned TDLib states no length limit, so
 * Telegram's refusal of a name too long is the error shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val canConfirm = name.isNotBlank() && name.trim() != initial
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyLarge.userContent,
                    label = { Text(stringResource(R.string.story_album_name_label)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(name.trim()) }),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.story_album_cancel))
                    }
                    Button(
                        onClick = { onConfirm(name.trim()) },
                        enabled = canConfirm,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(confirm), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
            }
        }
    }
    LaunchedEffect(focus) { focus.requestFocus() }
}
