package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageThreadRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import javax.inject.Inject

/**
 * The repositories of what the conversation shows besides its messages (`REQUIREMENTS.md` FR-C9): the pinned messages
 * and the bars the user hid, the forum topics a conversation of a topic shows, and the message threads (a channel
 * post's comments, a message's replies) a conversation of a thread shows. One parameter of [ChatViewModel] for them,
 * which already takes one repository per kind of data it combines.
 */
class ThreadRepositories
    @Inject
    constructor(
        val pinned: PinnedMessageRepository,
        val hiddenPins: HiddenPinsRepository,
        val forumTopics: ForumTopicRepository,
        val threads: MessageThreadRepository,
    )
