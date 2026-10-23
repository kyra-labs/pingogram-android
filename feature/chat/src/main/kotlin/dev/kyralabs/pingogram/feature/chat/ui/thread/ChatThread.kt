package dev.kyralabs.pingogram.feature.chat.ui.thread

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageThread
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ThreadRepositories
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.pinned.ChatPinnedMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Where in chat [chatId] the conversation shows messages, for `ChatViewModel` (`REQUIREMENTS.md` FR-C9): the whole
 * chat ([topic] null), one of its forum topics, or a message thread (a channel post's comments, which live in the
 * channel's discussion group, or the replies to a group's message). A topic's or thread's conversation is the chat's,
 * scoped to it:
 *
 * - **Repositories** ([messages], [attachments], [voiceNotes], [chats]): its own history and pages, every message sent
 *   into it, its messages viewed there (`TopicMessageRepository` and the others). A thread's history starts with the
 *   messages that start it (its head: the channel post, or the message replied to), without their link to the thread.
 * - **The chat** ([inThread]) reads as the topic or thread: its unread count, read state and last message, so the jump
 *   button's badge, the read checks and opening at the first unread message follow it; it has no draft, as topics and
 *   threads keep drafts of their own, which the composer does not sync yet ([drafts]: none).
 * - **Header** ([header]): the topic's name over the forum's, or the chat's name over "12 comments" (what someone
 *   is doing still shows).
 * - **Composer** ([composer]): a closed topic takes messages from its administrators only; a discussion group that does
 *   not ask to join takes comments from anyone.
 *
 * [state] follows the topic (`ForumTopicRepository.topic`) while the conversation collects it, in [scope]:
 * [ThreadState.Missing] when it cannot be found. A thread is asked for once (`MessageThreadRepository.thread`), again
 * with each of [attempts] (the conversation's "Try again"): its replies then come and go with the history. [open]
 * finds the thread of a message of this conversation and hands it to [onEffect] to open.
 */
internal class ChatThread(
    private val chatId: Long,
    val topic: MessageTopic?,
    private val repositories: ThreadRepositories,
    attempts: Flow<Int>,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    val state: Flow<ThreadState> =
        when (topic) {
            null -> {
                flowOf(ThreadState.WholeChat)
            }

            is MessageTopic.Forum -> {
                repositories.forumTopics
                    .topic(chatId, topic.topicId)
                    .map { found -> found?.let(ThreadState::Topic) ?: ThreadState.Missing }
            }

            is MessageTopic.Thread -> {
                attempts.map { replies(topic.threadId) }
            }
        }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The messages that start a message thread, without their link to it; none elsewhere. */
    private val head: Flow<List<Message>> =
        state.map { state ->
            (state as? ThreadState.Replies)
                ?.thread
                ?.head
                .orEmpty()
                .map(Message::withoutThread)
        }

    private suspend fun replies(threadId: Long): ThreadState =
        when (val found = repositories.threads.thread(chatId, threadId)) {
            is Outcome.Success -> ThreadState.Replies(found.value)
            is Outcome.Failure -> ThreadState.Failed(found.error)
        }

    /**
     * The pinned messages of the conversation ([ChatPinnedMessages]): of the topic or thread, if any. [actions] unpin
     * them, [chat] is the chat as the conversation shows it, [people] name their senders, and [onShow] shows a tapped
     * one in the list.
     */
    fun pinnedMessages(
        actions: MessageActionRepository,
        chat: Flow<Chat>,
        people: Flow<ChatPeople>,
        onShow: (messageId: Long) -> Unit,
    ): ChatPinnedMessages =
        ChatPinnedMessages(chatId, topic, repositories, actions, chat, people, scope, onShow, onEffect)

    /** Opens the thread of message [messageId]: its comments or replies; a snackbar if it has none (any more). */
    fun open(messageId: Long) {
        scope.launch {
            when (val found = repositories.threads.thread(chatId, messageId)) {
                is Outcome.Success -> onEffect(ChatEffect.OpenThread(found.value.chatId, found.value.topic))
                is Outcome.Failure -> onEffect(ChatEffect.ShowError(found.error))
            }
        }
    }

    fun messages(messages: MessageRepository): MessageRepository =
        topic?.let { TopicMessageRepository(messages, it, head) } ?: messages

    fun attachments(attachments: AttachmentRepository): AttachmentRepository =
        topic?.let { TopicAttachmentRepository(attachments, it) } ?: attachments

    fun voiceNotes(voiceNotes: VoiceNoteRepository): VoiceNoteRepository =
        topic?.let { TopicVoiceNoteRepository(voiceNotes, it) } ?: voiceNotes

    fun inlineBots(inlineBots: InlineBotRepository): InlineBotRepository =
        topic?.let { TopicInlineBotRepository(inlineBots, it) } ?: inlineBots

    fun chats(chats: ChatRepository): ChatRepository = topic?.let { TopicChatRepository(chats, it) } ?: chats

    /** The drafts of [chat] for the composer, as they change: none in a topic or thread. */
    fun drafts(chat: Flow<Chat?>): Flow<ChatDraft?> =
        if (topic == null) chat.filterNotNull().map { it.draft }.distinctUntilChanged() else emptyFlow()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What the conversation shows of a chat: all of it, one of its forum topics, or a message thread. */
internal sealed interface ThreadState {
    data object WholeChat : ThreadState

    data class Topic(
        val topic: ForumTopic,
    ) : ThreadState

    /** A message thread: a channel post's comments, or the replies to a group's message. */
    data class Replies(
        val thread: MessageThread,
    ) : ThreadState

    /** The topic cannot be found: deleted, no access, or offline before it was ever loaded. */
    data object Missing : ThreadState

    /** The thread could not be fetched: it has none (any more), or as every request can fail. */
    data class Failed(
        val error: DomainError,
    ) : ThreadState
}

/** The message without its link to its thread, as the thread's head shows it. */
private fun Message.withoutThread(): Message = copy(interactionInfo = interactionInfo?.copy(replyCount = null))

/**
 * [chat] as the conversation of [this] shows it: a topic's or thread's unread count, read state and last message (a
 * thread's as it was when the conversation asked for it), no draft.
 */
internal fun ThreadState.inThread(chat: Chat): Chat =
    when (this) {
        is ThreadState.Topic -> {
            chat.copy(
                unreadCount = topic.unreadCount,
                unreadMentionCount = topic.unreadMentionCount,
                lastReadInboxMessageId = topic.lastReadInboxMessageId,
                lastReadOutboxMessageId = topic.lastReadOutboxMessageId,
                lastMessage = topic.lastMessage,
                draft = null,
            )
        }

        is ThreadState.Replies -> {
            chat.copy(
                unreadCount = thread.unreadCount,
                unreadMentionCount = 0,
                lastReadInboxMessageId = thread.lastReadInboxMessageId,
                lastReadOutboxMessageId = thread.lastReadOutboxMessageId,
                lastMessage = null,
                draft = null,
            )
        }

        ThreadState.WholeChat, ThreadState.Missing, is ThreadState.Failed -> {
            chat
        }
    }

/**
 * The top bar of a topic or thread. A topic: its name, then what someone does in the chat or else the forum's name. A
 * thread: the chat's name (the discussion group's, whose photo the bar shows), then what someone does in the chat or
 * else "12 comments" ("12 replies").
 */
internal fun ThreadState.header(
    header: ChatHeader,
    chat: Chat,
): ChatHeader {
    val action = header.subtitle as? ChatSubtitle.Action
    return when (this) {
        is ThreadState.Topic -> {
            header.copy(
                title = UiText.Plain(topic.name),
                subtitle = action ?: ChatSubtitle.Status(UiText.Plain(chat.title)),
            )
        }

        is ThreadState.Replies -> {
            header.copy(subtitle = action ?: ChatSubtitle.Status(thread.countText()))
        }

        ThreadState.WholeChat, ThreadState.Missing, is ThreadState.Failed -> {
            header
        }
    }
}

/** "12 comments", "1 reply"; "Comments" or "Replies" while there are none. */
private fun MessageThread.countText(): UiText =
    when {
        replyCount == 0 && isComments -> UiText.resource(R.string.thread_title_comments)
        replyCount == 0 -> UiText.resource(R.string.thread_title_replies)
        isComments -> UiText.plural(R.plurals.thread_comments, replyCount, replyCount)
        else -> UiText.plural(R.plurals.thread_replies, replyCount, replyCount)
    }

/**
 * The composer of a topic or thread: a closed topic takes messages from the forum's owner and administrators only; a
 * thread of a discussion group that does not ask to join first takes comments from those who are not members too.
 */
internal fun ThreadState.composer(
    composer: ComposerUiState,
    supergroup: Supergroup?,
): ComposerUiState {
    val status = supergroup?.status
    val isAdmin = status is ChatMemberStatus.Creator || status is ChatMemberStatus.Administrator
    val closed = this is ThreadState.Topic && topic.isClosed && !isAdmin
    val opensToAll = this is ThreadState.Replies && supergroup.takesCommentsFromAnyone()
    return when {
        closed && composer is ComposerUiState.Available -> {
            ComposerUiState.Unavailable(UiText.resource(R.string.composer_topic_closed))
        }

        opensToAll && composer is ComposerUiState.Unavailable -> {
            // Sent at once: the composer offers no schedule in a thread.
            ComposerUiState.Available(canSchedule = false)
        }

        else -> {
            composer
        }
    }
}

/** A discussion group the user is not in, which lets anyone comment (td_api.tl `supergroup.join_to_send_messages`). */
private fun Supergroup?.takesCommentsFromAnyone(): Boolean = this != null && !status.isMember && !joinToSendMessages
