package dev.kyralabs.pingogram.feature.chats.ui.catchup

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.ChatListTimeFormatter
import dev.kyralabs.pingogram.feature.chats.ui.isChannel
import dev.kyralabs.pingogram.feature.chats.ui.isGroup
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * What Catch-up's cards are made of: a chat, its history (`MessageRepository.history`, which Catch-up loads with
 * `loadNewest`, from TDLib's local database first: loading reads nothing), the users who wrote and the supergroups (a
 * forum's avatar shape).
 */
internal class CatchUpSources
    @Inject
    constructor(
        val chatRepository: ChatRepository,
        val messageRepository: MessageRepository,
        private val userRepository: UserRepository,
        private val groupRepository: GroupRepository,
    ) {
        /** Chat [chatId]'s card as [mapper] makes it, then every change; null while TDLib does not know the chat. */
        fun card(
            chatId: Long,
            showsEarlier: Boolean,
            minutes: Flow<Instant>,
            mapper: CatchUpCardMapper,
        ): Flow<CatchUpCard?> =
            combine(
                chatRepository.chat(chatId),
                messageRepository.history(chatId),
                userRepository.users,
                combine(groupRepository.basicGroups, groupRepository.supergroups, ::CatchUpGroups),
                minutes,
            ) { chat, history, users, groups, now ->
                chat?.let { mapper.card(it, history, users, groups, now, showsEarlier) }
            }
    }

/**
 * Turns a chat and its history into a [CatchUpCard]: the newest [SHOWN_MESSAGES] unread incoming messages (up to
 * [EXPANDED_MESSAGES] once the earlier ones show), oldest first, each in one line with its sender in groups and its
 * time as the chat list words times; with nothing unread loaded, the last message.
 */
internal class CatchUpCardMapper(
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) {
    private var formatter: ChatListTimeFormatter? = null

    @Suppress("LongParameterList") // What a card shows comes from all of them.
    fun card(
        chat: Chat,
        history: ChatHistory,
        users: Map<Long, User>,
        groups: CatchUpGroups,
        now: Instant,
        showsEarlier: Boolean,
    ): CatchUpCard {
        val time = formatter()
        val incoming = history.messages.filter { !it.isOutgoing }
        val unread = incoming.filter { it.id > chat.lastReadInboxMessageId }.ifEmpty { incoming.take(1) }
        val shown = unread.take(if (showsEarlier) EXPANDED_MESSAGES else SHOWN_MESSAGES)
        val loading = history.messages.isEmpty() && !history.reachedStart
        val name = senderNames(chat, users)
        val supergroup = (chat.type as? ChatType.Supergroup)?.let { groups.supergroups[it.supergroupId] }
        val hue = senderHues(users)
        return CatchUpCard(
            chatId = chat.id,
            title = chat.title,
            photo = chat.photo,
            avatarShape =
                AvatarShape.ofChat(
                    isForum = supergroup?.isForum == true,
                    isGroup = chat.isGroup,
                    isChannel = chat.isChannel,
                ),
            unreadCount = chat.unreadCount,
            mentionCount = chat.unreadMentionCount,
            since = unread.lastOrNull()?.let { time.format(it.date, now) },
            messages =
                if (loading) {
                    null
                } else {
                    shown.asReversed().map { it.toCardMessage(chat, name, hue, time, now) }.toImmutableList()
                },
            earlierCount =
                if (showsEarlier) {
                    0
                } else {
                    (
                        maxOf(
                            chat.unreadCount,
                            unread.size,
                        ) - shown.size
                    ).coerceAtLeast(0)
                },
            replyTo =
                (unread.firstOrNull { it.containsUnreadMention } ?: unread.firstOrNull())
                    ?.takeUnless { loading }
                    ?.toCardMessage(chat, name, hue, time, now, withSender = true),
            canReply = chat.writeAccess(users, groups) == WriteAccess.Allowed,
        )
    }

    @Suppress("LongParameterList") // A message's line takes the chat's and the formatter's parts.
    private fun Message.toCardMessage(
        chat: Chat,
        name: (MessageSender) -> UiText?,
        hue: (MessageSender) -> Int,
        time: ChatListTimeFormatter,
        now: Instant,
        withSender: Boolean = false,
    ) = CatchUpMessage(
        id = id,
        sender = if (chat.isGroup || withSender) name(sender) else null,
        text = messagePreviewText(this, myUserId = null, name = name),
        time = time.format(date, now),
        senderHue = hue(sender),
    )

    /** A sender's hue (§13.6): their Telegram accent color, or their id without one. */
    private fun senderHues(users: Map<Long, User>): (MessageSender) -> Int =
        { sender ->
            when (sender) {
                is MessageSender.User -> SenderHues.indexOf(users[sender.userId]?.accentColorId ?: -1, sender.userId)
                is MessageSender.Chat -> SenderHues.indexOf(-1, sender.chatId)
            }
        }

    private fun Chat.writeAccess(
        users: Map<Long, User>,
        groups: CatchUpGroups,
    ): WriteAccess {
        val type = type
        val user =
            when (type) {
                is ChatType.Private -> users[type.userId]
                is ChatType.Secret -> users[type.userId]
                else -> null
            }
        return writeAccess(
            user = user,
            basicGroup = (type as? ChatType.BasicGroup)?.let { groups.basicGroups[it.basicGroupId] },
            supergroup = (type as? ChatType.Supergroup)?.let { groups.supergroups[it.supergroupId] },
        )
    }

    private fun senderNames(
        chat: Chat,
        users: Map<Long, User>,
    ): (MessageSender) -> UiText? =
        { sender ->
            when (sender) {
                is MessageSender.User -> users[sender.userId]?.shortName()
                is MessageSender.Chat -> if (sender.chatId == chat.id) UiText.Plain(chat.title) else null
            }
        }

    private fun formatter(): ChatListTimeFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return formatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatListTimeFormatter(zone, locale, is24HourClock, timePatterns).also { formatter = it }
    }

    private companion object {
        const val SHOWN_MESSAGES = 4
        const val EXPANDED_MESSAGES = 30
    }
}

/** The groups a card needs: whether the user may write in its chat, and a forum's avatar shape. */
internal data class CatchUpGroups(
    val basicGroups: Map<Long, BasicGroup> = emptyMap(),
    val supergroups: Map<Long, Supergroup> = emptyMap(),
)
