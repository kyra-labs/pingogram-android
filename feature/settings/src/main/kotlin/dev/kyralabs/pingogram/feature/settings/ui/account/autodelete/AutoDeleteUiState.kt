package dev.kyralabs.pingogram.feature.settings.ui.account.autodelete

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/** The self-destruct timer of the chats the user starts (`REQUIREMENTS.md` FR-S1, Privacy and security). */
sealed interface AutoDeleteUiState {
    data object Loading : AutoDeleteUiState

    @Immutable
    data class Error(
        val error: DomainError,
    ) : AutoDeleteUiState

    /**
     * The timer in [seconds] (0: off) among the [options] offered (Telegram's, and a value another app set), while
     * [isSaving] the choice just made.
     */
    @Immutable
    data class Content(
        val seconds: Int,
        val options: ImmutableList<Int>,
        val isSaving: Boolean = false,
    ) : AutoDeleteUiState
}

sealed interface AutoDeleteEvent {
    data object Retry : AutoDeleteEvent

    data class Select(
        val seconds: Int,
    ) : AutoDeleteEvent
}

sealed interface AutoDeleteEffect {
    data class ShowError(
        val error: DomainError,
    ) : AutoDeleteEffect
}
