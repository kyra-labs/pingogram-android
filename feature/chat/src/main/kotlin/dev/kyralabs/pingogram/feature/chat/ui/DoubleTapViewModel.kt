package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.DoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.QuickReactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What a double tap on a bubble adds: the account's quick reaction ([reaction], null while TDLib has not announced it),
 * on others' messages where [incoming] and on the user's own where [outgoing] (Settings → Appearance → Chats).
 */
@Immutable
data class DoubleTapReaction(
    val reaction: ReactionLabel?,
    val incoming: Boolean,
    val outgoing: Boolean,
) {
    /** The reaction a double tap adds to a message, outgoing or not; null where a double tap does nothing. */
    fun on(isOutgoing: Boolean): ReactionLabel? = reaction?.takeIf { if (isOutgoing) outgoing else incoming }

    companion object {
        /** Where no conversation provides it (previews, other screens): double taps do nothing. */
        val Off = DoubleTapReaction(reaction = null, incoming = false, outgoing = false)
    }
}

/**
 * The quick reaction and where double taps add it ([QuickReactionRepository], [DoubleTapSettingsRepository]), for the
 * conversation's route to hand its bubbles through [LocalDoubleTapReaction]; apart from `ChatViewModel`, as both are
 * the account's and the device's, not the chat's. The reaction itself goes through `ChatEvent.ToggleReaction`.
 */
@HiltViewModel
class DoubleTapViewModel
    @Inject
    constructor(
        quickReactions: QuickReactionRepository,
        settings: DoubleTapSettingsRepository,
    ) : ViewModel() {
        val doubleTap: StateFlow<DoubleTapReaction> =
            combine(quickReactions.quickReaction, settings.doubleTap) { reaction, places ->
                DoubleTapReaction(
                    // The paid reaction costs Stars: never from a double tap.
                    reaction = reaction?.takeUnless { it == ReactionType.Paid }?.label,
                    incoming = places.incoming,
                    outgoing = places.outgoing,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DoubleTapReaction.Off)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** What a double tap on the bubbles below adds; the conversation's route provides it. */
internal val LocalDoubleTapReaction = staticCompositionLocalOf { DoubleTapReaction.Off }
