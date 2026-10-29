package dev.kyralabs.pingogram.feature.settings.ui.reactions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.ChatSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.QuickReactionRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the double-tap page shows. */
@Immutable
sealed interface QuickReactionUiState {
    data object Loading : QuickReactionUiState

    /**
     * The emoji Telegram offers as reactions ([reactions], in its order), the quick reaction ([chosen]: one of them, a
     * choice on its way to Telegram, or null while TDLib has not announced it or it is a custom emoji
     * ([isCustomEmoji]), which only other apps set), and where a double tap adds it ([doubleTap]).
     */
    data class Content(
        val reactions: ImmutableList<String>,
        val chosen: String?,
        val isCustomEmoji: Boolean,
        val doubleTap: DoubleTapReactions,
    ) : QuickReactionUiState
}

sealed interface QuickReactionEvent {
    data class Choose(
        val emoji: String,
    ) : QuickReactionEvent

    data class SetIncoming(
        val on: Boolean,
    ) : QuickReactionEvent

    data class SetOutgoing(
        val on: Boolean,
    ) : QuickReactionEvent
}

sealed interface QuickReactionEffect {
    data class ShowError(
        val error: DomainError,
    ) : QuickReactionEffect
}

/**
 * Settings → Appearance → Chats → "Double-tap to react" (`docs/audit/feature-gaps-octogram.md`, P1): the account's
 * quick reaction (`QuickReactionRepository`), shown chosen at once and taken back with a snackbar when Telegram refuses
 * it, and whether a double tap adds it to others' and to the user's own messages (`DoubleTapSettingsRepository`).
 */
@HiltViewModel
class QuickReactionViewModel
    @Inject
    constructor(
        private val quickReactions: QuickReactionRepository,
        private val doubleTapSettings: DoubleTapSettingsRepository,
        chatSettings: ChatSettingsRepository,
    ) : ViewModel() {
        /** The emoji being sent, shown until TDLib announces it or refuses it. */
        private val pending = MutableStateFlow<String?>(null)

        val uiState: StateFlow<QuickReactionUiState> =
            combine(
                chatSettings.activeReactions,
                quickReactions.quickReaction,
                pending,
                doubleTapSettings.doubleTap,
            ) { reactions, announced, sending, doubleTap ->
                // TDLib announces the emoji once it started; none yet means not yet.
                if (reactions.isEmpty()) {
                    QuickReactionUiState.Loading
                } else {
                    QuickReactionUiState.Content(
                        reactions = reactions,
                        chosen = sending ?: (announced as? ReactionType.Emoji)?.emoji,
                        isCustomEmoji = sending == null && announced is ReactionType.CustomEmoji,
                        doubleTap = doubleTap,
                    )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                QuickReactionUiState.Loading,
            )

        private val effectChannel = Channel<QuickReactionEffect>(Channel.BUFFERED)
        val effects: Flow<QuickReactionEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: QuickReactionEvent) {
            when (event) {
                is QuickReactionEvent.Choose -> choose(event.emoji)
                is QuickReactionEvent.SetIncoming -> setDoubleTap { it.copy(incoming = event.on) }
                is QuickReactionEvent.SetOutgoing -> setDoubleTap { it.copy(outgoing = event.on) }
            }
        }

        private fun choose(emoji: String) {
            val current = pending.value ?: (quickReactions.quickReaction.value as? ReactionType.Emoji)?.emoji
            if (emoji == current) return
            pending.value = emoji
            viewModelScope.launch {
                val saved = quickReactions.setQuickReaction(ReactionType.Emoji(emoji))
                // TDLib announces the change before it answers; what it holds now is what counts.
                if (pending.value == emoji) pending.value = null
                saved.showFailure()
            }
        }

        private fun setDoubleTap(change: (DoubleTapReactions) -> DoubleTapReactions) {
            viewModelScope.launch {
                doubleTapSettings.setDoubleTap(change(doubleTapSettings.doubleTap.first())).showFailure()
            }
        }

        private fun Outcome<Unit>.showFailure() {
            if (this is Outcome.Failure && error != DomainError.Silent) {
                effectChannel.trySend(QuickReactionEffect.ShowError(error))
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
