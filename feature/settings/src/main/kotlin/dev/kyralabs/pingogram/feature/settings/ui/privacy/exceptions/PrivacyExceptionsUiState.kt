package dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** A user, or a group whose members, a privacy setting treats apart: [id] is a user id, or a chat id for a group. */
@Immutable
data class ExceptionKey(
    val id: Long,
    val isGroup: Boolean,
) {
    /** A key for lists, which must be one a `Bundle` holds: "g-100" for a group, "u5" for a user. */
    val itemKey: String get() = (if (isGroup) "g" else "u") + id
}

/** An exception as a row: its [title] (a name, "Deleted Account", a group's title) and avatar. */
@Immutable
data class ExceptionEntry(
    val key: ExceptionKey,
    val title: UiText,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "ExceptionEntry(key=$key, …)"
}

/** The users and groups of one side of a privacy setting's exceptions (`REQUIREMENTS.md` FR-S1). */
sealed interface PrivacyExceptionsUiState {
    data object Loading : PrivacyExceptionsUiState

    @Immutable
    data class Error(
        val error: DomainError,
    ) : PrivacyExceptionsUiState

    /** The [entries] in TDLib's order, users first; while [isSaving], a change waits for Telegram. */
    @Immutable
    data class Content(
        val entries: ImmutableList<ExceptionEntry>,
        val isSaving: Boolean = false,
    ) : PrivacyExceptionsUiState
}

/** A chat-list person or group the picker offers, [selected] while picked. */
@Immutable
data class ExceptionCandidate(
    val entry: ExceptionEntry,
    val isGroup: Boolean,
    val selected: Boolean,
)

/** The picker sheet while it shows, kept apart from the page's state: the [query] and what matches it. */
@Immutable
data class ExceptionPickerUiState(
    val query: String,
    val candidates: ImmutableList<ExceptionCandidate>,
)

sealed interface PrivacyExceptionsEvent {
    data object Retry : PrivacyExceptionsEvent

    data class Remove(
        val key: ExceptionKey,
    ) : PrivacyExceptionsEvent

    data object OpenPicker : PrivacyExceptionsEvent

    data class PickerQueryChanged(
        val query: String,
    ) : PrivacyExceptionsEvent

    data class TogglePicked(
        val key: ExceptionKey,
    ) : PrivacyExceptionsEvent

    /** "Done" or dismissing the picker: what is picked is saved. */
    data object ClosePicker : PrivacyExceptionsEvent
}

sealed interface PrivacyExceptionsEffect {
    data class ShowError(
        val error: DomainError,
    ) : PrivacyExceptionsEffect
}
