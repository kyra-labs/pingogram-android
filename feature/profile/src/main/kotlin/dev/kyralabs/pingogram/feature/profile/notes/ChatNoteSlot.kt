package dev.kyralabs.pingogram.feature.profile.notes

import androidx.compose.runtime.Immutable

/**
 * A chat's name and note on this phone as a page shows them (`REQUIREMENTS.md` FR-X20): the [state] of its
 * [ChatNoteViewModel] and where its [onEvent]s go, handed down to the page's "On this phone" rows as one.
 */
@Immutable
class ChatNoteSlot(
    val state: ChatNoteUiState,
    val onEvent: (ChatNoteEvent) -> Unit,
)
