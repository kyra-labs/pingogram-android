package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Whether a chat is unanswered at [now] (`REQUIREMENTS.md` FR-X22): a private chat with a person (never a bot, a
 * deleted account or the user's own Saved Messages, [myUserId]) whose last message is theirs, not a service message,
 * and has had no answer for [afterDays] days, at most [MAX_AGE]; never a [locked] chat, nor one [dismissed] with that
 * same last message.
 */
internal class UnansweredRule(
    private val myUserId: Long?,
    private val locked: Set<Long>,
    private val dismissed: Map<Long, Long>,
    afterDays: Int,
    private val now: Instant,
) {
    private val after: Duration = afterDays.days

    /** Whether [chat], with the other person [user] (null while unknown), is unanswered. */
    fun matches(
        chat: Chat,
        user: User?,
    ): Boolean {
        val message = chat.lastMessage ?: return false
        val type = chat.type
        val waiting = (now.epochSecond - message.date).seconds
        return type is ChatType.Private &&
            type.userId != myUserId &&
            user?.type == UserType.Regular &&
            chat.id !in locked &&
            !message.isOutgoing &&
            message.content !is MessageContent.Service &&
            dismissed[chat.id] != message.id &&
            waiting in after..MAX_AGE
    }

    companion object {
        /** A chat whose last message is older than this is no longer offered: the talk has moved on. */
        val MAX_AGE: Duration = 30.days
    }
}
