package dev.kyralabs.pingogram.feature.profile.notes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "On this phone" (`REQUIREMENTS.md` FR-X20, `docs/DESIGN_SYSTEM.md` §6 "Notes and names on this phone"): the user's
 * own name for the chat and their private note, each a segmented row of the page's look, its value (user content) or
 * "Add a name" / "Add a note" in `onSurfaceVariant`, over what it is; either row opens the editor. Nothing while the
 * setting is off ([ChatNoteUiState.shown]).
 */
@Composable
internal fun ChatNoteGroup(
    state: ChatNoteUiState,
    onEvent: (ChatNoteEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.shown) return
    val edit = { onEvent(ChatNoteEvent.Edit) }
    ProfileGroup(
        rows =
            listOf(
                ProfileRow(NAME_ROW) { shapes -> ChatNoteRow(state.name, NameRow, shapes, edit) },
                ProfileRow(NOTE_ROW) { shapes -> ChatNoteRow(state.note, NoteRow, shapes, edit) },
            ),
        modifier = modifier,
        title = stringResource(R.string.chat_note_section),
    )
}

/** What a row is: its [label], what it says without a value ([add]), and its [icon]. */
private class RowKind(
    @param:StringRes val label: Int,
    @param:StringRes val add: Int,
    @param:DrawableRes val icon: Int,
)

private val NameRow = RowKind(R.string.chat_note_name, R.string.chat_note_name_add, UiR.drawable.ic_edit)
private val NoteRow = RowKind(R.string.chat_note_note, R.string.chat_note_note_add, UiR.drawable.ic_description)

/** One row: [value], or the [kind]'s "Add" without one, over its label, its icon at the start; a tap edits. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatNoteRow(
    value: String,
    kind: RowKind,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val editLabel = stringResource(R.string.chat_note_edit)
    SegmentedListItem(
        onClick = onClick,
        modifier = Modifier.semantics { onClick(label = editLabel, action = null) },
        shapes = shapes,
        colors = profileRowColors(),
        leadingContent = { Icon(painterResource(kind.icon), contentDescription = null) },
        supportingContent = { Text(stringResource(kind.label)) },
    ) {
        if (value.isBlank()) {
            Text(stringResource(kind.add), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.userContent,
                maxLines = NOTE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val NAME_ROW = "chat_note_name"
private const val NOTE_ROW = "chat_note_note"

/** The most lines of the note the row shows; the editor shows all of it. */
private const val NOTE_LINES = 4

/** The group's states for previews: a name and a note, and neither yet. */
internal class ChatNoteGroupStates : PreviewParameterProvider<ChatNoteUiState> {
    override val values =
        sequenceOf(
            ChatNoteUiState(shown = true, name = "Book club", note = "Meets on Tuesdays. Bring the book."),
            ChatNoteUiState(shown = true),
        )
}

@PingogramPreviews
@Composable
private fun ChatNoteGroupPreview(
    @PreviewParameter(ChatNoteGroupStates::class) state: ChatNoteUiState,
) {
    PingogramPreview { ChatNoteGroup(state, onEvent = {}) }
}
