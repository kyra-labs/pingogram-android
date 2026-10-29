package dev.kyralabs.pingogram.feature.settings.ui.privacy.messages

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy

/** Who may start a chat with the user (`REQUIREMENTS.md` FR-S1, Privacy and security → Messages). */
sealed interface NewChatPrivacyUiState {
    data object Loading : NewChatPrivacyUiState

    @Immutable
    data class Error(
        val error: DomainError,
    ) : NewChatPrivacyUiState

    /** The [privacy] as Telegram has it, with the choice just made while [isSaving]. */
    @Immutable
    data class Content(
        val privacy: NewChatPrivacy,
        val isSaving: Boolean = false,
    ) : NewChatPrivacyUiState
}

sealed interface NewChatPrivacyEvent {
    data object Retry : NewChatPrivacyEvent

    /** Non-contacts may write first ([allows]) or not. */
    data class Select(
        val allows: Boolean,
    ) : NewChatPrivacyEvent
}

sealed interface NewChatPrivacyEffect {
    data class ShowError(
        val error: DomainError,
    ) : NewChatPrivacyEffect
}
