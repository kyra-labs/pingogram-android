package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.DirectMessagesTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageThreadRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.ShownChatListRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedJumps
import javax.inject.Inject

/**
 * The repositories of what the conversation shows besides its messages (`REQUIREMENTS.md` FR-C9): the pinned messages
 * and the bars the user hid, the forum topics a conversation of a topic shows, the message threads (a channel post's
 * comments, a message's replies) a conversation of a thread shows, the Saved Messages topics of its tabs, and a
 * channel's direct messages topics with who wrote them (the users and chats that name them), and the chat list the
 * Chats tab shows, whose next unread channel a read channel offers. One
 * parameter of [ChatViewModel] for them, which already takes one repository per kind of data it combines.
 */
@Suppress("LongParameterList") // One holder, so that ChatViewModel takes a single parameter for all of them.
class ThreadRepositories
    @Inject
    constructor(
        val pinned: PinnedMessageRepository,
        val hiddenPins: HiddenPinsRepository,
        val forumTopics: ForumTopicRepository,
        val threads: MessageThreadRepository,
        val savedTopics: SavedMessagesTopicRepository,
        val directTopics: DirectMessagesTopicRepository,
        val users: UserRepository,
        val chats: ChatRepository,
        val shownLists: ShownChatListRepository,
        val pinnedJumps: PinnedJumps = PinnedJumps(),
    )
