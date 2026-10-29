package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings

/**
 * The archive's settings page (`REQUIREMENTS.md` FR-L9): Telegram's settings of the account ([settings]) and how the
 * "Archived chats" row shows in the chat list on this device ([rowStyle]).
 */
@Immutable
data class ArchiveSettingsUiState(
    val settings: ArchiveSettingsContent = ArchiveSettingsContent.Loading,
    val rowStyle: ArchiveRowStyle = ArchiveRowStyle.Full,
)

/** Telegram's side of the page. */
@Immutable
sealed interface ArchiveSettingsContent {
    /** Asked for, not answered yet. */
    data object Loading : ArchiveSettingsContent

    /** Reading them failed with [error]; "Try again" ([ArchiveSettingsEvent.Retry]) asks again. */
    data class Error(
        val error: DomainError,
    ) : ArchiveSettingsContent

    /**
     * The [settings] as shown (a change shows at once, before Telegram took it), and whether Telegram lets the account
     * archive new chats from non-contacts ([canArchiveAndMuteNewChats]); where not, that switch is disabled unless it
     * is on, so it can still be turned off.
     */
    data class Content(
        val settings: ArchiveSettings,
        val canArchiveAndMuteNewChats: Boolean,
    ) : ArchiveSettingsContent {
        /** The switch for new chats from non-contacts can be used. */
        val canChangeNewChats: Boolean
            get() = canArchiveAndMuteNewChats || settings.archiveAndMuteNewChatsFromNonContacts
    }
}

/** What the user does on the page, sent to [ArchiveSettingsViewModel.onEvent]. */
sealed interface ArchiveSettingsEvent {
    /** A switch was flipped: [settings] are the page's settings with that change. */
    data class Change(
        val settings: ArchiveSettings,
    ) : ArchiveSettingsEvent

    /** A style of the "Archived chats" row was picked. */
    data class SetRowStyle(
        val style: ArchiveRowStyle,
    ) : ArchiveSettingsEvent

    /** "Try again" after reading the settings failed. */
    data object Retry : ArchiveSettingsEvent
}

/** One-off effects of [ArchiveSettingsViewModel], handled by the screen once each. */
sealed interface ArchiveSettingsEffect {
    /** Saving a change failed (it was taken back): a snackbar shows the error's text. */
    data class ShowError(
        val error: DomainError,
    ) : ArchiveSettingsEffect
}
