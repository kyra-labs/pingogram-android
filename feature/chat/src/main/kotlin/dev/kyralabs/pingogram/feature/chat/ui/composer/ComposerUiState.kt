package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R

/** What the bottom of the conversation shows (`REQUIREMENTS.md` FR-C5): the composer, why there is none, or nothing. */
@Immutable
sealed interface ComposerUiState {
    /** Nothing: the chat is not known yet, or it is a channel the user reads without posting. */
    data object Hidden : ComposerUiState

    /** No composer, and a short label in its place that says why: [reason]. */
    data class Unavailable(
        val reason: UiText,
    ) : ComposerUiState

    /** The composer. [canSchedule] whether its send menu offers scheduling, which secret chats do not allow. */
    data class Available(
        val canSchedule: Boolean,
    ) : ComposerUiState
}

/**
 * The composer of [chat], from the current user's [writeAccess] to it: its [user] for a private or secret chat, its
 * [basicGroup] or [supergroup] for a group or channel (null while unknown, or for other chats).
 */
internal fun composerState(
    chat: Chat,
    user: User?,
    basicGroup: BasicGroup?,
    supergroup: Supergroup?,
): ComposerUiState =
    when (chat.writeAccess(user, basicGroup, supergroup)) {
        WriteAccess.Allowed -> ComposerUiState.Available(canSchedule = chat.type !is ChatType.Secret)
        WriteAccess.ReadOnly, WriteAccess.Unknown -> ComposerUiState.Hidden
        WriteAccess.NotMember -> ComposerUiState.Unavailable(UiText.resource(R.string.composer_not_member))
        WriteAccess.Restricted -> ComposerUiState.Unavailable(UiText.resource(R.string.composer_restricted))
    }
