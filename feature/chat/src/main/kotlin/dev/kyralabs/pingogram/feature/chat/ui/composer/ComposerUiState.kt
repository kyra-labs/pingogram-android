package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsChoice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the bottom of the conversation shows (`REQUIREMENTS.md` FR-C5): the composer, why there is none, or nothing. */
@Immutable
sealed interface ComposerUiState {
    /** Nothing: the chat is not known yet, or it is a channel the user reads without posting. */
    data object Hidden : ComposerUiState

    /** No composer, and a short label in its place that says why: [reason]. */
    data class Unavailable(
        val reason: UiText,
    ) : ComposerUiState

    /**
     * The composer. [canSchedule] whether its send menu offers scheduling, which secret chats do not allow; [bar] the
     * message it replies to or edits, shown above the text field; null for a new message. [attachOptions] what its
     * attach button offers, as the chat allows; empty hides the button, as while editing. [sendAs] who new messages go
     * out as, where the user can choose (its avatar opens the send-as picker); null hides it, as while editing.
     */
    data class Available(
        val canSchedule: Boolean,
        val bar: ComposerBar? = null,
        val attachOptions: ImmutableList<AttachOption> = persistentListOf(),
        val sendAs: SendAsChoice? = null,
    ) : ComposerUiState
}

/** The bar above the text field: the message the composer replies to or edits (`REQUIREMENTS.md` FR-C5, FR-C6). */
@Immutable
sealed interface ComposerBar {
    /** The message, which tapping the bar shows in the list. */
    val messageId: Long

    /** One line of the message: its text, or what kind of message it is. */
    val preview: UiText

    /**
     * Replying to message [messageId]: its [sender]'s name, "You" for the user's own, as reply quotes say. Without a
     * sender while the message loads.
     */
    data class Reply(
        override val messageId: Long,
        val sender: UiText?,
        override val preview: UiText,
    ) : ComposerBar {
        override fun toString() = "Reply(messageId=$messageId, …)"
    }

    /**
     * Editing message [messageId]: the send button becomes a check that saves the edit. [canBeEmpty] for a caption,
     * which an empty text removes; a text message needs a text.
     */
    data class Edit(
        override val messageId: Long,
        override val preview: UiText,
        val canBeEmpty: Boolean,
    ) : ComposerBar {
        override fun toString() = "Edit(messageId=$messageId, canBeEmpty=$canBeEmpty, …)"
    }
}

/**
 * The composer of [chat], from the current user's [writeAccess] to it: its [user] for a private or secret chat, its
 * [basicGroup] or [supergroup] for a group or channel (null while unknown, or for other chats). A secret chat also
 * needs its [secretChat] ready (`REQUIREMENTS.md` FR-E1): while it waits for the other user, and once it ended, a
 * label says why there is no composer; nothing shows while TDLib has not announced it.
 */
internal fun composerState(
    chat: Chat,
    user: User?,
    basicGroup: BasicGroup?,
    supergroup: Supergroup?,
    secretChat: SecretChat? = null,
): ComposerUiState =
    if (chat.type is ChatType.Secret && secretChat?.state != SecretChatState.Ready) {
        secretComposerState(secretChat?.state)
    } else {
        when (chat.writeAccess(user, basicGroup, supergroup)) {
            WriteAccess.Allowed -> ComposerUiState.Available(canSchedule = chat.type !is ChatType.Secret)
            WriteAccess.ReadOnly, WriteAccess.Unknown -> ComposerUiState.Hidden
            WriteAccess.NotMember -> ComposerUiState.Unavailable(UiText.resource(R.string.composer_not_member))
            WriteAccess.Restricted -> ComposerUiState.Unavailable(UiText.resource(R.string.composer_restricted))
        }
    }

/** The label of a secret chat that is not ready: pending or ended; nothing while it is not known. */
private fun secretComposerState(state: SecretChatState?): ComposerUiState =
    when (state) {
        SecretChatState.Pending -> ComposerUiState.Unavailable(UiText.resource(R.string.composer_secret_pending))
        SecretChatState.Closed -> ComposerUiState.Unavailable(UiText.resource(R.string.composer_secret_closed))
        SecretChatState.Ready, null -> ComposerUiState.Hidden
    }
