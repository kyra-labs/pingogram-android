package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.drop
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The note about a contact (`docs/DESIGN_SYSTEM.md` §6 "Contact note", the owner's board 15b), a [BasicAlertDialog] in
 * the alert dialog's shape and colors, scrolling where it does not fit:
 * - a [MaterialShapes.Cookie9Sided] badge in `primaryContainer` with the note symbol, decorative;
 * - "Note about Arjun" in `titleLargeEmphasized`, a heading, the name isolated;
 * - "Only you see this note. It stays with your contacts on all your devices." in `bodyMedium` `onSurfaceVariant`;
 * - the note in a filled multi-line [TextField] (user content, sentences capitalized), labelled "Note", with why a save
 *   failed (a polite live region) at the start below it and the counter ("66 / 140", tabular figures, read as "66 of
 *   140 characters") at the end;
 * - a [LinearWavyProgressIndicator] while Telegram answers, then [TextButton] "Cancel" and the default [Button] "Save"
 *   (`labelLargeEmphasized`), both with the press morph; "Save" stays disabled until the note changed.
 *
 * Back and a tap outside close it as "Cancel" does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactNoteDialog(
    editor: ProfileNoteEditor,
    onEvent: (ProfileEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    BasicAlertDialog(onDismissRequest = { onEvent(ProfileEvent.DismissNote) }) {
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
                NoteBadge()
                Text(
                    text = stringResource(R.string.profile_note_title, editor.name.bidiIsolated()).bidiIsolated(),
                    modifier = Modifier.semantics { heading() },
                    color = AlertDialogDefaults.titleContentColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = stringResource(R.string.profile_note_text),
                    color = AlertDialogDefaults.textContentColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                NoteField(editor, onEvent)
                if (editor.isSaving) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
                NoteButtons(editor, onEvent)
            }
        }
    }
}

/** The note symbol on a soft cookie in `primaryContainer`; decorative. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoteBadge() {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarRow)
                .clip(shape)
                .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_description),
            contentDescription = null,
            tint = colors.onPrimaryContainer,
        )
    }
}

/** The note's field, its error and its counter. */
@Composable
private fun NoteField(
    editor: ProfileNoteEditor,
    onEvent: (ProfileEvent) -> Unit,
) {
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val error = editor.error?.let { errorMessage(resources, it, showDetails) }
    val text = rememberTextFieldState(editor.text)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect { currentOnEvent(ProfileEvent.NoteChanged(it)) }
    }
    TextField(
        state = text,
        modifier = Modifier.fillMaxWidth(),
        enabled = !editor.isSaving,
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = { Text(stringResource(R.string.profile_note)) },
        supportingText = { NoteSupport(error, editor.length) },
        isError = error != null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        inputTransformation = NoteLength,
        lineLimits = TextFieldLineLimits.MultiLine(NOTE_MIN_LINES, NOTE_MAX_LINES),
    )
}

/** Keeps the note within [ProfileLimits.NOTE_LENGTH_MAX] characters (code points, as Telegram counts them). */
private object NoteLength : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val typed = asCharSequence()
        val max = ProfileLimits.NOTE_LENGTH_MAX
        if (Character.codePointCount(typed, 0, typed.length) > max) {
            replace(Character.offsetByCodePoints(typed, 0, max), length, "")
        }
    }
}

/** Why the save failed, if it did, at the start; the counter at the end. */
@Composable
private fun NoteSupport(
    error: String?,
    length: Int,
) {
    val max = ProfileLimits.NOTE_LENGTH_MAX
    val description = pluralStringResource(R.plurals.profile_note_counter_description, max, length, max)
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            text = error.orEmpty(),
            modifier = Modifier.weight(1f).semantics { if (error != null) liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = stringResource(R.string.profile_note_counter, length, max),
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** "Cancel" and "Save", at the end. */
@Composable
private fun NoteButtons(
    editor: ProfileNoteEditor,
    onEvent: (ProfileEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
    ) {
        TextButton(onClick = { onEvent(ProfileEvent.DismissNote) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.profile_note_cancel), style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = { onEvent(ProfileEvent.SaveNote) },
            enabled = editor.canSave,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.profile_note_save), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

private const val NOTE_MIN_LINES = 3
private const val NOTE_MAX_LINES = 6

/** The dialog's states for previews: editing, unchanged, saving, failed. */
internal class ContactNoteEditorStates : PreviewParameterProvider<ProfileNoteEditor> {
    private val note = "Met at DroidCon '25. Owes me a chai. Prefers Signal for work stuff."

    override val values =
        sequenceOf(
            ProfileNoteEditor(name = "Arjun", text = note, saved = "Met at DroidCon '25."),
            ProfileNoteEditor(name = "Arjun", text = note, saved = note),
            ProfileNoteEditor(name = "Arjun", text = note, saved = "", isSaving = true),
            ProfileNoteEditor(name = "Arjun", text = note, saved = "", error = DomainError.Network),
        )
}

@PingogramPreviews
@Composable
private fun ContactNoteDialogPreview(
    @PreviewParameter(ContactNoteEditorStates::class) editor: ProfileNoteEditor,
) {
    PingogramPreview { ContactNoteDialog(editor, onEvent = {}) }
}
