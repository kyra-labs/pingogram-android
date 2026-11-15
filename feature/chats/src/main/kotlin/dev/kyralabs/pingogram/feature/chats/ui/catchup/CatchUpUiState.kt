package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Catch-up's screen ([CatchUpViewModel]): where it stands, and whether its settings sheet shows. */
@Immutable
data class CatchUpUiState(
    val stage: CatchUpStage = CatchUpStage.Loading,
    /** Muted chats and channels are included (`ChatListSettings.catchUpIncludesMuted`). */
    val includesMuted: Boolean = false,
    /** The settings sheet shows. */
    val showsSettings: Boolean = false,
)

/** What Catch-up shows. */
@Immutable
sealed interface CatchUpStage {
    /** The chats are being read, the first time. */
    data object Loading : CatchUpStage

    /** Nothing to catch up on: no unread chat Catch-up takes. */
    data object Empty : CatchUpStage

    /**
     * Chat [position] of [total] ([current]), with [next] peeking behind it, and about [minutesLeft] minutes to go at
     * 20 seconds a chat.
     */
    data class Cards(
        val position: Int,
        val total: Int,
        val minutesLeft: Int,
        val current: CatchUpCard,
        val next: CatchUpCard? = null,
        /** The reply field on [current]'s card, while it shows. */
        val reply: CatchUpReplyState? = null,
    ) : CatchUpStage

    /**
     * Every chat of the round handled: [chats] in [minutes], [read] marked as read, [replied] answered, [skipped] left
     * for later, which "Review skipped" goes through again.
     */
    data class Done(
        val chats: Int,
        val minutes: Int,
        val read: Int,
        val replied: Int,
        val skipped: Int,
    ) : CatchUpStage
}

/**
 * One chat's card: its name and avatar, [unreadCount] unread messages ([mentionCount] mentioning the user) since
 * [since], and the newest of them ([messages], oldest first), with [earlierCount] older ones not shown; [messages] is
 * null while its history loads.
 */
@Immutable
data class CatchUpCard(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val avatarShape: AvatarShape,
    val unreadCount: Int,
    val mentionCount: Int,
    val since: String?,
    val messages: ImmutableList<CatchUpMessage>? = persistentListOf(),
    val earlierCount: Int = 0,
    /** What "Reply" answers: the newest message that mentions the user, else the newest unread one. */
    val replyTo: CatchUpMessage? = null,
)

/** The reply field on a card: [isSending] while Telegram takes the reply. */
@Immutable
data class CatchUpReplyState(
    val isSending: Boolean = false,
)

/** A message on a card: who sent it (null in a private chat or a channel), what it says in one line, and when. */
@Immutable
data class CatchUpMessage(
    val id: Long,
    val sender: UiText?,
    val text: UiText,
    val time: String,
)

/** What the screen reads from Catch-up. */
sealed interface CatchUpEvent {
    /** "Mark as read": the shown chat is read, as the conversation reads it. */
    data object MarkAsRead : CatchUpEvent

    /** "Skip": the shown chat waits for "Review skipped". */
    data object Skip : CatchUpEvent

    /** "Open chat": the shown chat's conversation opens; it counts as read once the conversation read it. */
    data object OpenChat : CatchUpEvent

    /** "Reply": the card shows the reply field. */
    data object Reply : CatchUpEvent

    /** Closes the reply field. */
    data object CancelReply : CatchUpEvent

    /** "Send and go to next": [text] goes to the shown chat in reply, which then counts as read and replied. */
    data class SendReply(
        val text: String,
    ) : CatchUpEvent

    /** "N earlier messages": the card shows the older unread messages loaded too. */
    data object ShowEarlier : CatchUpEvent

    /** "Review skipped": a new round of the skipped chats. */
    data object ReviewSkipped : CatchUpEvent

    /** Shows or hides the settings sheet. */
    data class ShowSettings(
        val show: Boolean,
    ) : CatchUpEvent

    /** "Include muted chats and channels". */
    data class SetIncludesMuted(
        val include: Boolean,
    ) : CatchUpEvent
}

/** What Catch-up asks of the screen once. */
sealed interface CatchUpEffect {
    /** Opens chat [chatId]'s conversation. */
    data class OpenChat(
        val chatId: Long,
    ) : CatchUpEffect

    /** Says that something did not work. */
    data class ShowError(
        val error: DomainError,
    ) : CatchUpEffect
}
