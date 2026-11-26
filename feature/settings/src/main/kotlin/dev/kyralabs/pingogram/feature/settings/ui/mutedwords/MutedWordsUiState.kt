package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings

/** Muted words' page (`REQUIREMENTS.md` FR-X21): the [settings], null until read. */
@Immutable
data class MutedWordsUiState(
    val settings: MutedWordSettings? = null,
) {
    /** Whether another word may be added. */
    val canAdd: Boolean get() = (settings?.words?.size ?: 0) < MutedWordSettings.MAX_WORDS
}

/** What the user does on the page, handled by [MutedWordsViewModel.onEvent]. */
sealed interface MutedWordsEvent {
    data class SetEnabled(
        val enabled: Boolean,
    ) : MutedWordsEvent

    data class Add(
        val word: String,
    ) : MutedWordsEvent

    data class Remove(
        val word: String,
    ) : MutedWordsEvent
}

/** One-off side effects of [MutedWordsViewModel], handled once by its route. */
sealed interface MutedWordsEffect {
    /** The word was added: the field empties. */
    data object Added : MutedWordsEffect

    data class ShowError(
        val error: DomainError,
    ) : MutedWordsEffect
}
