package dev.kyralabs.pingogram.feature.settings.ui.snippets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.flow.drop

/**
 * The snippet editor (`docs/DESIGN_SYSTEM.md` §6 "Snippets"), a `ModalBottomSheet` that opens expanded:
 * [SnippetEditorContent]. Back, dragging it away and the scrim close it as nothing saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SnippetEditorSheet(
    draft: SnippetDraft,
    onEvent: (SnippetsEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    PingogramBottomSheet(onDismissRequest = { currentOnEvent(SnippetsEvent.CancelDraft) }, sheetState = sheetState) {
        SnippetEditorContent(draft, onEvent)
    }
}

/**
 * "New snippet" or "Edit snippet" in `titleLargeEmphasized` (a heading); the name in a filled field after a fixed "/",
 * with what a name may be, or why this one cannot be saved (`error`, a polite live region), below it; the text in a
 * field of three to eight lines; then "Delete" (an `error` [TextButton], for a saved snippet) and the default [Button]
 * "Save", disabled until the draft can be saved.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SnippetEditorContent(
    draft: SnippetDraft,
    onEvent: (SnippetsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val name = rememberTextFieldState(draft.name)
    val text = rememberTextFieldState(draft.text)
    ReportChanges(name, text, onEvent)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text =
                stringResource(
                    if (draft.id ==
                        null
                    ) {
                        R.string.settings_snippets_new
                    } else {
                        R.string.settings_snippets_edit
                    },
                ),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        NameField(name, draft.nameError)
        TextField(
            state = text,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.userContent,
            label = { Text(stringResource(R.string.settings_snippets_text_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            inputTransformation = InputTransformation.maxLength(Snippet.MAX_TEXT_LENGTH),
            lineLimits =
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = MIN_TEXT_LINES,
                    maxHeightInLines = MAX_TEXT_LINES,
                ),
        )
        EditorButtons(draft, onEvent)
    }
}

/** The name's field after a fixed "/", with what a name may be or why [error] keeps it from being saved below it. */
@Composable
private fun NameField(
    name: TextFieldState,
    error: SnippetNameError?,
) {
    val message =
        when (error) {
            SnippetNameError.NotOneWord -> stringResource(R.string.settings_snippets_name_not_one_word)
            SnippetNameError.Taken -> stringResource(R.string.settings_snippets_name_taken)
            null -> null
        }
    TextField(
        state = name,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = { Text(stringResource(R.string.settings_snippets_name_label)) },
        prefix = { Text(stringResource(R.string.settings_snippets_name, "")) },
        supportingText = {
            Text(
                text = message ?: stringResource(R.string.settings_snippets_name_hint),
                modifier = Modifier.semantics { if (message != null) liveRegion = LiveRegionMode.Polite },
            )
        },
        isError = message != null,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        inputTransformation = InputTransformation.maxLength(Snippet.MAX_NAME_LENGTH),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** Sends each change of the [name] and [text] fields to the view model, which keeps the draft. */
@Composable
private fun ReportChanges(
    name: TextFieldState,
    text: TextFieldState,
    onEvent: (SnippetsEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(name) {
        snapshotFlow { name.text.toString() }.drop(1).collect { currentOnEvent(SnippetsEvent.EditName(it)) }
    }
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect { currentOnEvent(SnippetsEvent.EditText(it)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorButtons(
    draft: SnippetDraft,
    onEvent: (SnippetsEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draft.id != null) {
            TextButton(
                onClick = { onEvent(SnippetsEvent.AskDelete) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_snippets_delete), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(Modifier.weight(1f))
        Button(
            onClick = { onEvent(SnippetsEvent.SaveDraft) },
            enabled = draft.canSave,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.settings_snippets_save), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

private const val MIN_TEXT_LINES = 3
private const val MAX_TEXT_LINES = 8

@PingogramPreviews
@Composable
private fun SnippetEditorNewPreview() {
    PingogramPreview { SnippetEditorContent(SnippetsPreviewStates.newDraft, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SnippetEditorEditingPreview() {
    PingogramPreview { SnippetEditorContent(SnippetsPreviewStates.editing, onEvent = {}) }
}
