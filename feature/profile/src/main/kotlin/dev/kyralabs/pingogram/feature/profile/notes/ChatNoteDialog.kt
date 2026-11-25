package dev.kyralabs.pingogram.feature.profile.notes

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.drop
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The name and note on this phone (`docs/DESIGN_SYSTEM.md` §6 "Notes and names on this phone"), a [BasicAlertDialog]
 * in the alert dialog's shape and colors, scrolling where it does not fit, as the contact note's: the phone symbol on
 * a `MaterialShapes.Cookie9Sided` badge in `primaryContainer`; "Name and note" in `titleLargeEmphasized`, a heading;
 * that only this phone keeps them; the name in a one-line [TextField] and the note in a multi-line one (user content),
 * each with its counter; why a save failed (a polite live region); then "Cancel" and "Save", enabled once something
 * changed. Back and a tap outside close it as "Cancel" does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatNoteDialog(
    editor: ChatNoteEditor,
    onEvent: (ChatNoteEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    BasicAlertDialog(onDismissRequest = { onEvent(ChatNoteEvent.Dismiss) }) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                PhoneBadge()
                Text(
                    text = stringResource(R.string.chat_note_title),
                    modifier = Modifier.semantics { heading() },
                    color = AlertDialogDefaults.titleContentColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = stringResource(R.string.chat_note_text),
                    color = AlertDialogDefaults.textContentColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                NoteFields(editor, onEvent)
                if (editor.failed) {
                    Text(
                        text = stringResource(R.string.chat_note_failed),
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                NoteButtons(editor, onEvent)
            }
        }
    }
}

/** The phone symbol on a soft cookie in `primaryContainer`; decorative. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhoneBadge() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarRow)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(UiR.drawable.ic_smartphone), contentDescription = null, tint = colors.onPrimaryContainer)
    }
}

/** The name's field and the note's, each reporting what is typed. */
@Composable
private fun NoteFields(
    editor: ChatNoteEditor,
    onEvent: (ChatNoteEvent) -> Unit,
) {
    val name = rememberTextFieldState(editor.name)
    val note = rememberTextFieldState(editor.note)
    Reported(name) { onEvent(ChatNoteEvent.NameChanged(it)) }
    Reported(note) { onEvent(ChatNoteEvent.NoteChanged(it)) }
    NoteField(name, R.string.chat_note_name, ChatNote.MAX_NAME_LENGTH, TextFieldLineLimits.SingleLine, editor)
    NoteField(note, R.string.chat_note_note, ChatNote.MAX_NOTE_LENGTH, NoteLines, editor)
}

/** Calls [onChange] with each edit of [state] after the first text. */
@Composable
private fun Reported(
    state: TextFieldState,
    onChange: (String) -> Unit,
) {
    val current by rememberUpdatedState(onChange)
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.drop(1).collect { current(it) } }
}

@Composable
private fun NoteField(
    state: TextFieldState,
    @StringRes label: Int,
    max: Int,
    lines: TextFieldLineLimits,
    editor: ChatNoteEditor,
) {
    TextField(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        enabled = !editor.isSaving,
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = { Text(stringResource(label)) },
        supportingText = {
            Text(
                text = stringResource(R.string.chat_note_counter, state.text.length, max),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        inputTransformation = InputTransformation.maxLength(max),
        lineLimits = lines,
    )
}

/** "Cancel" and "Save", at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoteButtons(
    editor: ChatNoteEditor,
    onEvent: (ChatNoteEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
    ) {
        TextButton(onClick = { onEvent(ChatNoteEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.profile_note_cancel), style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = { onEvent(ChatNoteEvent.Save) }, enabled = editor.canSave, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.profile_note_save), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

private const val NOTE_MIN_LINES = 3
private const val NOTE_MAX_LINES = 6
private val NoteLines = TextFieldLineLimits.MultiLine(NOTE_MIN_LINES, NOTE_MAX_LINES)

/** The dialog's states for previews: a change to save, nothing changed, a failed save. */
internal class ChatNoteEditorStates : PreviewParameterProvider<ChatNoteEditor> {
    override val values =
        sequenceOf(
            ChatNoteEditor("Book club", "Meets on Tuesdays. Bring the book.", savedName = "", savedNote = ""),
            ChatNoteEditor("Book club", "Tuesdays", savedName = "Book club", savedNote = "Tuesdays"),
            ChatNoteEditor("Readers", "Tuesdays", savedName = "Book club", savedNote = "Tuesdays", failed = true),
        )
}

@PingogramPreviews
@Composable
private fun ChatNoteDialogPreview(
    @PreviewParameter(ChatNoteEditorStates::class) editor: ChatNoteEditor,
) {
    PingogramPreview { ChatNoteDialog(editor, onEvent = {}) }
}
